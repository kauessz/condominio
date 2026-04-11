package com.example.condo.service;

import com.example.condo.audit.AuditAction;
import com.example.condo.audit.AuditModule;
import com.example.condo.dto.visitor.*;
import com.example.condo.entity.Condominium;
import com.example.condo.entity.Unit;
import com.example.condo.entity.Visitor;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.VisitorRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class VisitorService {

    private final VisitorRepository visitorRepo;
    private final CondominiumRepository condominiumRepo;
    private final UnitRepository unitRepo;
    private final AuditService auditService;

    public VisitorService(
        VisitorRepository visitorRepo,
        CondominiumRepository condominiumRepo,
        UnitRepository unitRepo,
        AuditService auditService
    ) {
        this.visitorRepo = visitorRepo;
        this.condominiumRepo = condominiumRepo;
        this.unitRepo = unitRepo;
        this.auditService = auditService;
    }

    public Page<VisitorResponse> search(
        Long condominiumIdParam,
        Long unitIdParam,
        String status,
        String type,
        Instant dateFrom,
        Instant dateTo,
        Pageable pageable
    ) {
        String tenantId = TenantContext.get();
        Long condominiumId = UserContext.resolveCondominiumId(condominiumIdParam);

        if (condominiumId == null) {
            return Page.empty(pageable);
        }

        Long effectiveUnitId = isMorador() ? UserContext.unitId() : unitIdParam;
        Visitor.Type typeEnum = parseType(type);
        Visitor.Status statusEnum = parseStatus(status);

        return visitorRepo.search(
            tenantId,
            condominiumId,
            effectiveUnitId,
            null,
            dateFrom,
            dateTo,
            statusEnum,
            typeEnum,
            pageable
        ).map(this::toResponse);
    }

    public VisitorResponse getById(Long id) {
        String tenantId = TenantContext.get();
        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));
        validateOwnership(visitor);
        if (isMorador() && !Objects.equals(visitor.getUnitId(), UserContext.unitId())) {
            throw new ResourceNotFoundException("Visitante", "id", id);
        }
        return toResponse(visitor);
    }

    @Transactional
    public VisitorResponse create(CreateVisitorRequest request) {
        String tenantId = TenantContext.get();
        Long condominiumId = UserContext.resolveCondominiumId(request.condominiumId());
        if (condominiumId == null) {
            throw new BusinessException("Usuário sem condomínio configurado. Contate o administrador.");
        }

        Condominium condominium = findCondominium(tenantId, condominiumId);
        Long unitId = isMorador() ? UserContext.unitId() : request.unitId();
        if (unitId != null) {
            validateUnitExists(tenantId, condominiumId, unitId);
        }

        Visitor visitor = new Visitor();
        visitor.setTenantId(tenantId);
        visitor.setCondominiumId(condominiumId);
        visitor.setUnitId(unitId);
        visitor.setName(request.name().trim());
        visitor.setDocument(trimToNull(request.document()));
        visitor.setPlate(trimToNull(request.plate()));
        visitor.setPhone(trimToNull(request.phone()));
        visitor.setEmail(trimToNull(request.email()));
        visitor.setNote(trimToNull(request.note()));
        visitor.setCarrier(trimToNull(request.carrier()));
        visitor.setPackages(request.packages());
        visitor.setCheckInAt(Instant.now());
        visitor.setExpectedInAt(request.expectedInAt());
        visitor.setExpectedOutAt(request.expectedOutAt());
        visitor.setType(parseTypeRequired(request.type()));
        visitor.setStatus(resolveInitialStatus(condominium));

        visitor = visitorRepo.save(visitor);
        VisitorResponse after = toResponse(visitor);
        auditService.log(
            AuditModule.VISITORS,
            visitor.getType() == Visitor.Type.DELIVERY ? AuditAction.REGISTER_DELIVERY : AuditAction.CREATE_VISITOR,
            "Visitor",
            visitor.getId(),
            visitor.getCondominiumId(),
            describeVisitorCreation(visitor),
            null,
            after,
            visitorDetails(visitor, null, after.status())
        );
        return after;
    }

    @Transactional
    public VisitorResponse update(Long id, UpdateVisitorRequest request) {
        String tenantId = TenantContext.get();
        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));
        VisitorResponse before = VisitorResponse.from(visitor);
        validateOwnership(visitor);

        if (visitor.getStatus() != Visitor.Status.DRAFT && visitor.getStatus() != Visitor.Status.PENDING_APPROVAL) {
            throw new BusinessException("Não é possível editar visitante que já foi processado.");
        }

        if (request.name() != null) visitor.setName(request.name().trim());
        if (request.document() != null) visitor.setDocument(trimToNull(request.document()));
        if (request.plate() != null) visitor.setPlate(trimToNull(request.plate()));
        if (request.phone() != null) visitor.setPhone(trimToNull(request.phone()));
        if (request.email() != null) visitor.setEmail(trimToNull(request.email()));
        if (request.note() != null) visitor.setNote(trimToNull(request.note()));
        if (request.carrier() != null) visitor.setCarrier(trimToNull(request.carrier()));
        if (request.packages() != null) visitor.setPackages(request.packages());
        if (request.expectedInAt() != null) visitor.setExpectedInAt(request.expectedInAt());
        if (request.expectedOutAt() != null) visitor.setExpectedOutAt(request.expectedOutAt());
        if (request.type() != null) visitor.setType(parseTypeRequired(request.type()));

        visitor = visitorRepo.save(visitor);
        VisitorResponse after = toResponse(visitor);
        auditService.log(
            AuditModule.VISITORS,
            AuditAction.UPDATE_VISITOR,
            "Visitor",
            visitor.getId(),
            visitor.getCondominiumId(),
            "Cadastro de visitante " + after.name() + " atualizado.",
            before,
            after,
            visitorDetails(visitor, before.status(), after.status())
        );
        return after;
    }

    @Transactional
    public VisitorResponse approve(Long id, Long approvedBy, ApproveVisitorRequest request) {
        String tenantId = TenantContext.get();
        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));
        VisitorResponse before = VisitorResponse.from(visitor);
        validateOwnership(visitor);
        validateCanApprove(visitor, findCondominium(tenantId, visitor.getCondominiumId()));

        if (visitor.getStatus() != Visitor.Status.PENDING_APPROVAL && visitor.getStatus() != Visitor.Status.DRAFT) {
            throw new BusinessException("Visitante já foi aprovado ou rejeitado anteriormente");
        }

        visitor.setStatus(Visitor.Status.APPROVED);
        visitor.setApprovedAt(Instant.now());
        visitor.setApprovedBy(approvedBy != null ? approvedBy.toString() : null);
        if (request != null && request.note() != null) {
            String existing = visitor.getNote() != null ? visitor.getNote() : "";
            visitor.setNote((existing + "\n[Aprovação] " + request.note()).trim());
        }

        visitor = visitorRepo.save(visitor);
        VisitorResponse after = toResponse(visitor);
        auditService.log(
            AuditModule.VISITORS,
            AuditAction.APPROVE_VISITOR,
            "Visitor",
            visitor.getId(),
            visitor.getCondominiumId(),
            "Visitante " + after.name() + " aprovado para a unidade " + formatUnitLabel(after.unitCode(), after.unitNumber(), after.unitBlock()) + ".",
            before,
            after,
            visitorDetails(visitor, before.status(), after.status())
        );
        return after;
    }

    @Transactional
    public VisitorResponse reject(Long id, RejectVisitorRequest request) {
        String tenantId = TenantContext.get();
        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));
        VisitorResponse before = VisitorResponse.from(visitor);
        validateOwnership(visitor);
        validateCanApprove(visitor, findCondominium(tenantId, visitor.getCondominiumId()));

        if (visitor.getStatus() != Visitor.Status.PENDING_APPROVAL && visitor.getStatus() != Visitor.Status.DRAFT) {
            throw new BusinessException("Visitante já foi aprovado ou rejeitado anteriormente");
        }

        visitor.setStatus(Visitor.Status.REJECTED);
        visitor.setRejectionReason(request.reason().trim());
        visitor = visitorRepo.save(visitor);
        VisitorResponse after = toResponse(visitor);
        auditService.log(
            AuditModule.VISITORS,
            AuditAction.REJECT_VISITOR,
            "Visitor",
            visitor.getId(),
            visitor.getCondominiumId(),
            "Visitante " + after.name() + " rejeitado" + (after.rejectionReason() != null ? ": " + after.rejectionReason() : "."),
            before,
            after,
            visitorDetails(visitor, before.status(), after.status())
        );
        return after;
    }

    @Transactional
    public VisitorResponse checkout(Long id) {
        String tenantId = TenantContext.get();
        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));
        VisitorResponse before = VisitorResponse.from(visitor);
        validateOwnership(visitor);
        validateCanOperateAccess();

        if (visitor.getStatus() == Visitor.Status.CHECKED_OUT) {
            throw new BusinessException("Visitante já realizou checkout");
        }
        if (visitor.getStatus() == Visitor.Status.PENDING_APPROVAL || visitor.getStatus() == Visitor.Status.REJECTED) {
            throw new BusinessException("Não é possível fazer checkout de visitante não liberado.");
        }

        visitor.setStatus(Visitor.Status.CHECKED_OUT);
        visitor.setCheckOutAt(Instant.now());
        visitor = visitorRepo.save(visitor);
        VisitorResponse after = toResponse(visitor);
        auditService.log(
            AuditModule.VISITORS,
            visitor.getType() == Visitor.Type.DELIVERY ? AuditAction.WITHDRAW_DELIVERY : AuditAction.CHECK_OUT_VISITOR,
            "Visitor",
            visitor.getId(),
            visitor.getCondominiumId(),
            visitor.getType() == Visitor.Type.DELIVERY
                ? "Entrega " + after.name() + " marcada como retirada."
                : "Checkout registrado para o visitante " + after.name() + ".",
            before,
            after,
            visitorDetails(visitor, before.status(), after.status())
        );
        return after;
    }

    @Transactional
    public void delete(Long id) {
        String tenantId = TenantContext.get();
        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));
        validateOwnership(visitor);
        VisitorResponse before = VisitorResponse.from(visitor);
        visitor.setDeletedAt(Instant.now());
        visitorRepo.save(visitor);
        auditService.log(
            AuditModule.VISITORS,
            AuditAction.DELETE,
            "Visitor",
            id,
            visitor.getCondominiumId(),
            "Registro de visitante " + before.name() + " removido.",
            before,
            null,
            visitorDetails(visitor, before.status(), null)
        );
    }

    @Transactional
    public VisitorResponse updateStatus(Long id, UpdateVisitorStatusRequest request) {
        String tenantId = TenantContext.get();
        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));
        VisitorResponse before = VisitorResponse.from(visitor);
        validateOwnership(visitor);
        Condominium condominium = findCondominium(tenantId, visitor.getCondominiumId());

        Visitor.Status newStatus = parseStatusRequired(request.status());
        Visitor.Status current = visitor.getStatus();
        Long updatedById = UserContext.userId();

        switch (newStatus) {
            case APPROVED -> {
                validateCanApprove(visitor, condominium);
                ensurePending(current);
                visitor.setStatus(Visitor.Status.APPROVED);
                visitor.setApprovedAt(Instant.now());
                visitor.setApprovedBy(updatedById != null ? updatedById.toString() : null);
            }
            case REJECTED -> {
                validateCanApprove(visitor, condominium);
                ensurePending(current);
                visitor.setStatus(Visitor.Status.REJECTED);
                visitor.setRejectionReason(trimToNull(request.reason()));
            }
            case CHECKED_IN -> {
                validateCanOperateAccess();
                if (current != Visitor.Status.APPROVED) {
                    throw new BusinessException("Somente visitantes aprovados podem fazer check-in.");
                }
                visitor.setStatus(Visitor.Status.CHECKED_IN);
            }
            case CHECKED_OUT -> {
                validateCanOperateAccess();
                if (current != Visitor.Status.APPROVED && current != Visitor.Status.CHECKED_IN) {
                    throw new BusinessException("Somente visitantes aprovados ou já presentes podem fazer checkout.");
                }
                visitor.setStatus(Visitor.Status.CHECKED_OUT);
                visitor.setCheckOutAt(Instant.now());
            }
            case CANCELLED -> {
                if (!canCancel(visitor)) {
                    throw new AccessDeniedException("Você não pode cancelar esta visita.");
                }
                visitor.setStatus(Visitor.Status.CANCELLED);
            }
            default -> throw new BusinessException("Transição de status não permitida para: " + newStatus);
        }

        visitor = visitorRepo.save(visitor);
        VisitorResponse after = toResponse(visitor);
        auditService.log(
            AuditModule.VISITORS,
            resolveStatusAction(visitor.getType(), newStatus),
            "Visitor",
            visitor.getId(),
            visitor.getCondominiumId(),
            describeStatusChange(visitor, before.status(), after.status()),
            before,
            after,
            visitorDetails(visitor, before.status(), after.status())
        );
        return after;
    }

    private AuditAction resolveStatusAction(Visitor.Type type, Visitor.Status status) {
        return switch (status) {
            case APPROVED -> AuditAction.APPROVE_VISITOR;
            case REJECTED -> AuditAction.REJECT_VISITOR;
            case CHECKED_IN -> AuditAction.CHECK_IN_VISITOR;
            case CHECKED_OUT -> type == Visitor.Type.DELIVERY ? AuditAction.WITHDRAW_DELIVERY : AuditAction.CHECK_OUT_VISITOR;
            default -> AuditAction.STATUS_CHANGE;
        };
    }

    private String describeVisitorCreation(Visitor visitor) {
        if (visitor.getType() == Visitor.Type.DELIVERY) {
            return "Entrega " + visitor.getName() + " registrada para a unidade " + (visitor.getUnitId() != null ? "#" + visitor.getUnitId() : "sem unidade") + ".";
        }
        return visitor.getUnitId() != null
            ? "Visitante " + visitor.getName() + " cadastrado para a unidade #" + visitor.getUnitId() + "."
            : "Visitante " + visitor.getName() + " cadastrado.";
    }

    private String describeStatusChange(Visitor visitor, String beforeStatus, String afterStatus) {
        if (visitor.getType() == Visitor.Type.DELIVERY && "CHECKED_OUT".equals(afterStatus)) {
            return "Entrega " + visitor.getName() + " marcada como retirada.";
        }
        if ("CHECKED_IN".equals(afterStatus)) {
            return "Check-in registrado para " + visitor.getName() + ".";
        }
        if ("CHECKED_OUT".equals(afterStatus)) {
            return "Checkout registrado para " + visitor.getName() + ".";
        }
        return "Status de " + visitor.getName() + " alterado de " + beforeStatus + " para " + afterStatus + ".";
    }

    private Map<String, Object> visitorDetails(Visitor visitor, String beforeStatus, String afterStatus) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("visitorId", visitor.getId());
        details.put("visitorName", visitor.getName());
        details.put("visitorType", visitor.getType().name());
        details.put("unitId", visitor.getUnitId());
        details.put("unitLabel", buildUnitLabel(visitor.getUnitId()));
        details.put("statusBefore", beforeStatus);
        details.put("statusAfter", afterStatus);
        details.put("expectedInAt", visitor.getExpectedInAt());
        details.put("expectedOutAt", visitor.getExpectedOutAt());
        return details;
    }

    private String formatUnitLabel(String unitCode, String unitNumber, String unitBlock) {
        String base = unitNumber != null && !unitNumber.isBlank()
            ? unitNumber
            : (unitCode != null && !unitCode.isBlank() ? unitCode : "sem identificação");
        return unitBlock != null && !unitBlock.isBlank() ? base + " • Bloco " + unitBlock : base;
    }

    private String buildUnitLabel(Long unitId) {
        if (unitId == null) {
            return null;
        }
        return unitRepo.findByTenantIdAndId(TenantContext.get(), unitId)
            .map(unit -> unit.getBlock() != null && !unit.getBlock().isBlank()
                ? "Unidade " + unit.getNumber() + " - Bloco " + unit.getBlock()
                : "Unidade " + unit.getNumber())
            .orElse("Unidade #" + unitId);
    }

    private void ensurePending(Visitor.Status current) {
        if (current != Visitor.Status.PENDING_APPROVAL && current != Visitor.Status.DRAFT) {
            throw new BusinessException("Somente visitantes pendentes podem ser processados.");
        }
    }

    private boolean isMorador() {
        UserContext.Data ctx = UserContext.get();
        return ctx != null && "MORADOR".equalsIgnoreCase(ctx.role());
    }

    private void validateOwnership(Visitor visitor) {
        Long effectiveCondoId = UserContext.resolveCondominiumId(visitor.getCondominiumId());
        if (effectiveCondoId != null && !effectiveCondoId.equals(visitor.getCondominiumId())) {
            throw new ResourceNotFoundException("Visitante", "id", visitor.getId());
        }
    }

    private void validateCanApprove(Visitor visitor, Condominium condominium) {
        if (UserContext.isSuperuser()) return;
        UserContext.Data ctx = UserContext.get();
        if (ctx == null || ctx.role() == null) {
            throw new AccessDeniedException("Usuário não autenticado");
        }
        String role = ctx.role().toUpperCase();
        if ("ADMIN".equals(role) && condominium.isAdminOverrideAllowed()) return;
        if ("SINDICO".equals(role) && condominium.isAllowSyndicApproveVisitor()) return;
        if ("MORADOR".equals(role) && Objects.equals(visitor.getUnitId(), ctx.unitId())) return;
        if ("PORTARIA".equals(role) && condominium.isPortariaCanAutoApprove()) return;
        throw new AccessDeniedException("Este perfil não pode aprovar ou rejeitar este visitante.");
    }

    private void validateCanOperateAccess() {
        if (UserContext.isSuperuser()) return;
        UserContext.Data ctx = UserContext.get();
        if (ctx == null || ctx.role() == null) {
            throw new AccessDeniedException("Usuário não autenticado");
        }
        String role = ctx.role().toUpperCase();
        if ("PORTARIA".equals(role) || "ADMIN".equals(role) || "SINDICO".equals(role)) {
            return;
        }
        throw new AccessDeniedException("Este perfil não pode operar check-in/check-out.");
    }

    private boolean canCancel(Visitor visitor) {
        if (UserContext.isSuperuser()) return true;
        UserContext.Data ctx = UserContext.get();
        if (ctx == null || ctx.role() == null) return false;
        String role = ctx.role().toUpperCase();
        if ("ADMIN".equals(role) || "SINDICO".equals(role) || "PORTARIA".equals(role)) return true;
        return "MORADOR".equals(role) && Objects.equals(visitor.getUnitId(), ctx.unitId());
    }

    private Visitor.Status resolveInitialStatus(Condominium condominium) {
        if (UserContext.isSuperuser()) return Visitor.Status.APPROVED;
        UserContext.Data ctx = UserContext.get();
        if (ctx == null || ctx.role() == null) return Visitor.Status.PENDING_APPROVAL;
        String role = ctx.role().toUpperCase();
        if ("ADMIN".equals(role) && condominium.isAdminOverrideAllowed()) return Visitor.Status.APPROVED;
        if ("PORTARIA".equals(role) && condominium.isPortariaCanAutoApprove()) return Visitor.Status.APPROVED;
        return condominium.isResidentApprovalRequired() ? Visitor.Status.PENDING_APPROVAL : Visitor.Status.APPROVED;
    }

    private Visitor.Status parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        return parseStatusRequired(status);
    }

    private Visitor.Status parseStatusRequired(String status) {
        try {
            return Visitor.Status.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                "Status inválido. Valores aceitos: DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, CHECKED_IN, CHECKED_OUT, CANCELLED");
        }
    }

    private Visitor.Type parseType(String type) {
        if (type == null || type.isBlank()) return null;
        return parseTypeRequired(type);
    }

    private Visitor.Type parseTypeRequired(String type) {
        try {
            return Visitor.Type.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Tipo inválido. Valores aceitos: VISITOR, DELIVERY, SERVICE");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Condominium findCondominium(String tenantId, Long condominiumId) {
        return condominiumRepo.findByTenantIdAndId(tenantId, condominiumId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condominiumId));
    }

    private void validateUnitExists(String tenantId, Long condominiumId, Long unitId) {
        var unit = unitRepo.findByTenantIdAndId(tenantId, unitId)
            .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", unitId));
        if (!unit.getCondominiumId().equals(condominiumId)) {
            throw new ResourceNotFoundException("Unidade", "id no condomínio especificado", unitId);
        }
    }

    private VisitorResponse toResponse(Visitor visitor) {
        if (visitor.getUnitId() == null) {
            return VisitorResponse.from(visitor);
        }
        Unit unit = unitRepo.findByTenantIdAndId(TenantContext.get(), visitor.getUnitId()).orElse(null);
        if (unit == null) {
            return VisitorResponse.from(visitor);
        }
        return VisitorResponse.withUnit(
            visitor,
            unit.getCode(),
            unit.getNumber(),
            unit.getBlock()
        );
    }
}
