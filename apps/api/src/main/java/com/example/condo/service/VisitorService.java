package com.example.condo.service;

import com.example.condo.dto.visitor.*;
import com.example.condo.entity.Visitor;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.VisitorRepository;
import com.example.condo.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service para operações de visitantes/entregas.
 *
 * Responsabilidades:
 * - Aprovação e rejeição de visitantes
 * - Soft delete
 * - Validação de status e regras de negócio
 * - Conversão de DTOs
 * - Isolamento multi-tenant
 */
@Service
@Transactional(readOnly = true)
public class VisitorService {

    private final VisitorRepository visitorRepo;
    private final CondominiumRepository condominiumRepo;
    private final UnitRepository unitRepo;

    public VisitorService(
        VisitorRepository visitorRepo,
        CondominiumRepository condominiumRepo,
        UnitRepository unitRepo
    ) {
        this.visitorRepo = visitorRepo;
        this.condominiumRepo = condominiumRepo;
        this.unitRepo = unitRepo;
    }

    /**
     * Busca visitantes com filtros e paginação.
     */
    public Page<VisitorResponse> search(
        Long condominiumId,
        Long unitId,
        String status,
        String type,
        Instant dateFrom,
        Instant dateTo,
        Pageable pageable
    ) {
        String tenantId = TenantContext.get();

        // Converte strings para enums (se fornecidos)
        Visitor.Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = Visitor.Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Status inválido. Valores aceitos: PENDING, APPROVED, REJECTED, CHECKED_OUT");
            }
        }

        Visitor.Type typeEnum = null;
        if (type != null && !type.isBlank()) {
            try {
                typeEnum = Visitor.Type.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Tipo inválido. Valores aceitos: VISITOR, DELIVERY, SERVICE");
            }
        }

        Page<Visitor> page = visitorRepo.search(
            tenantId,
            condominiumId,
            unitId,
            null, // query de busca (não implementado no VisitorService ainda)
            dateFrom,
            dateTo,
            statusEnum,
            typeEnum,
            pageable
        );

        return page.map(VisitorResponse::from);
    }

    /**
     * Busca visitante por ID.
     */
    public VisitorResponse getById(Long id) {
        String tenantId = TenantContext.get();

        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));

        return VisitorResponse.from(visitor);
    }

    /**
     * Cria um novo visitante/entrega.
     */
    @Transactional
    public VisitorResponse create(CreateVisitorRequest request) {
        String tenantId = TenantContext.get();

        // Valida condomínio
        validateCondominiumExists(tenantId, request.condominiumId());

        // Valida unidade (se fornecida)
        if (request.unitId() != null) {
            validateUnitExists(tenantId, request.condominiumId(), request.unitId());
        }

        // Parseia tipo
        Visitor.Type type;
        try {
            type = Visitor.Type.valueOf(request.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Tipo inválido. Valores aceitos: VISITOR, DELIVERY, SERVICE");
        }

        // Cria visitante
        Visitor visitor = new Visitor();
        visitor.setTenantId(tenantId);
        visitor.setCondominiumId(request.condominiumId());
        visitor.setUnitId(request.unitId());
        visitor.setName(request.name().trim());
        visitor.setDocument(request.document() != null ? request.document().trim() : null);
        visitor.setPlate(request.plate() != null ? request.plate().trim() : null);
        visitor.setPhone(request.phone() != null ? request.phone().trim() : null);
        visitor.setEmail(request.email() != null ? request.email().trim() : null);
        visitor.setNote(request.note() != null ? request.note().trim() : null);
        visitor.setCarrier(request.carrier() != null ? request.carrier().trim() : null);
        visitor.setPackages(request.packages());
        visitor.setCheckInAt(Instant.now());
        visitor.setExpectedInAt(request.expectedInAt());
        visitor.setExpectedOutAt(request.expectedOutAt());
        visitor.setStatus(Visitor.Status.PENDING);
        visitor.setType(type);

        visitor = visitorRepo.save(visitor);

        return VisitorResponse.from(visitor);
    }

    /**
     * Atualiza um visitante existente.
     *
     * Regra: só pode atualizar se ainda estiver PENDING.
     */
    @Transactional
    public VisitorResponse update(Long id, UpdateVisitorRequest request) {
        String tenantId = TenantContext.get();

        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));

        // Regra: só pode editar se ainda estiver pendente
        if (visitor.getStatus() != Visitor.Status.PENDING) {
            throw new BusinessException("Não é possível editar visitante que já foi aprovado/rejeitado");
        }

        // Atualização parcial
        if (request.name() != null) visitor.setName(request.name().trim());
        if (request.document() != null) visitor.setDocument(request.document().trim());
        if (request.plate() != null) visitor.setPlate(request.plate().trim());
        if (request.phone() != null) visitor.setPhone(request.phone().trim());
        if (request.email() != null) visitor.setEmail(request.email().trim());
        if (request.note() != null) visitor.setNote(request.note().trim());
        if (request.carrier() != null) visitor.setCarrier(request.carrier().trim());
        if (request.packages() != null) visitor.setPackages(request.packages());
        if (request.expectedInAt() != null) visitor.setExpectedInAt(request.expectedInAt());
        if (request.expectedOutAt() != null) visitor.setExpectedOutAt(request.expectedOutAt());

        if (request.type() != null) {
            try {
                visitor.setType(Visitor.Type.valueOf(request.type().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Tipo inválido. Valores aceitos: VISITOR, DELIVERY, SERVICE");
            }
        }

        visitor = visitorRepo.save(visitor);

        return VisitorResponse.from(visitor);
    }

    /**
     * Aprova um visitante.
     *
     * Regra: só pode aprovar se estiver PENDING.
     */
    @Transactional
    public VisitorResponse approve(Long id, Long approvedBy, ApproveVisitorRequest request) {
        String tenantId = TenantContext.get();

        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));

        if (visitor.getStatus() != Visitor.Status.PENDING) {
            throw new BusinessException("Visitante já foi aprovado ou rejeitado anteriormente");
        }

        visitor.setStatus(Visitor.Status.APPROVED);
        visitor.setApprovedAt(Instant.now());
        visitor.setApprovedBy(approvedBy);

        if (request != null && request.note() != null) {
            visitor.setNote(visitor.getNote() + "\n[Aprovação] " + request.note());
        }

        visitor = visitorRepo.save(visitor);

        return VisitorResponse.from(visitor);
    }

    /**
     * Rejeita um visitante.
     *
     * Regra: só pode rejeitar se estiver PENDING.
     */
    @Transactional
    public VisitorResponse reject(Long id, RejectVisitorRequest request) {
        String tenantId = TenantContext.get();

        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));

        if (visitor.getStatus() != Visitor.Status.PENDING) {
            throw new BusinessException("Visitante já foi aprovado ou rejeitado anteriormente");
        }

        visitor.setStatus(Visitor.Status.REJECTED);
        visitor.setRejectionReason(request.reason().trim());

        visitor = visitorRepo.save(visitor);

        return VisitorResponse.from(visitor);
    }

    /**
     * Marca visitante como saiu (checkout).
     */
    @Transactional
    public VisitorResponse checkout(Long id) {
        String tenantId = TenantContext.get();

        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));

        if (visitor.getStatus() == Visitor.Status.CHECKED_OUT) {
            throw new BusinessException("Visitante já realizou checkout");
        }

        visitor.setStatus(Visitor.Status.CHECKED_OUT);
        visitor.setCheckOutAt(Instant.now());

        visitor = visitorRepo.save(visitor);

        return VisitorResponse.from(visitor);
    }

    /**
     * Soft delete de visitante.
     */
    @Transactional
    public void delete(Long id) {
        String tenantId = TenantContext.get();

        Visitor visitor = visitorRepo.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Visitante", "id", id));

        visitor.setDeletedAt(Instant.now());
        visitorRepo.save(visitor);
    }

    // ========== Métodos auxiliares ==========

    private void validateCondominiumExists(String tenantId, Long condominiumId) {
        condominiumRepo.findByTenantIdAndId(tenantId, condominiumId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condominiumId));
    }

    private void validateUnitExists(String tenantId, Long condominiumId, Long unitId) {
        var unit = unitRepo.findByTenantIdAndId(tenantId, unitId)
            .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", unitId));

        if (!unit.getCondominiumId().equals(condominiumId)) {
            throw new ResourceNotFoundException("Unidade", "id no condomínio especificado", unitId);
        }
    }
}
