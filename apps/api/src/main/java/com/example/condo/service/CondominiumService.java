package com.example.condo.service;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.condominium.CondominiumResponse;
import com.example.condo.dto.condominium.CreateCondominiumRequest;
import com.example.condo.dto.condominium.UpdateCondominiumRequest;
import com.example.condo.entity.Condominium;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service para operações de condomínio.
 *
 * Responsabilidades:
 * - Validações de negócio
 * - Conversão de DTOs
 * - Isolamento multi-tenant
 * - Controle transacional
 *
 * Regra de isolamento:
 *   SUPERUSER  → lista/acessa todos os condomínios do tenant
 *   Demais roles → listam/acessam APENAS o próprio condomínio (condominiumId do JWT)
 */
@Service
@Transactional(readOnly = true)
public class CondominiumService {

    private final CondominiumRepository condominiumRepo;
    private final UnitRepository unitRepo;
    private final ResidentRepository residentRepo;
    private final AuditService auditService;

    public CondominiumService(
        CondominiumRepository condominiumRepo,
        UnitRepository unitRepo,
        ResidentRepository residentRepo,
        AuditService auditService
    ) {
        this.condominiumRepo = condominiumRepo;
        this.unitRepo = unitRepo;
        this.residentRepo = residentRepo;
        this.auditService = auditService;
    }

    /**
     * Lista condomínios com paginação e contadores.
     *
     * SUPERUSER → todos os condomínios do tenant (com paginação).
     * Demais roles → retorna página com somente o próprio condomínio.
     */
    public PageResponse<CondominiumResponse> listWithCounts(int page, int pageSize) {
        String tenantId = TenantContext.get();

        // ---------------------------------------------------------------
        // Isolamento de tenant: não-SUPERUSER vê APENAS o próprio condo
        // ---------------------------------------------------------------
        if (!UserContext.isSuperuser()) {
            UserContext.Data ctx = UserContext.get();
            Long condominiumId = ctx != null ? ctx.condominiumId() : null;

            if (condominiumId == null) {
                // Usuário sem condomínio vinculado → lista vazia
                return PageResponse.of(List.of(), 0, pageSize, 0);
            }

            // Busca o único condomínio do usuário
            Condominium condo = condominiumRepo.findByTenantIdAndId(tenantId, condominiumId)
                .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condominiumId));

            long unitCount     = unitRepo.countByTenantIdAndCondominiumId(tenantId, condominiumId);
            long residentCount = residentRepo.countByTenantIdAndCondominiumId(tenantId, condominiumId);

            CondominiumResponse resp = CondominiumResponse.withCounts(condo, unitCount, residentCount);
            return PageResponse.of(List.of(resp), 0, pageSize, 1);
        }

        // ---------------------------------------------------------------
        // SUPERUSER → lista paginada de todos os condomínios do tenant
        // ---------------------------------------------------------------
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "created_at"));
        Page<Object[]> result = condominiumRepo.pageWithCounts(tenantId, pageable);

        var items = result.getContent().stream()
            .map(row -> {
                Long id            = ((Number) row[0]).longValue();
                String name        = (String) row[1];
                String cnpj        = (String) row[2];
                LocalDateTime createdAt = ((Timestamp) row[3]).toLocalDateTime();
                long unitCount     = ((Number) row[4]).longValue();
                long residentCount = ((Number) row[5]).longValue();

                return new CondominiumResponse(
                    id,
                    name,
                    cnpj,
                    true,
                    createdAt,
                    unitCount,
                    residentCount,
                    false,
                    true,
                    true,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                );
            })
            .toList();

        return PageResponse.of(items, page, pageSize, result.getTotalElements());
    }

    /**
     * Busca condomínio por ID com contadores.
     *
     * Regra de isolamento: não-SUPERUSER só pode acessar o próprio condomínio.
     * Tentativa de acessar outro ID retorna 403.
     */
    public CondominiumResponse getById(Long id) {
        String tenantId = TenantContext.get();

        // Garante que não-SUPERUSER acessa apenas o próprio condomínio
        if (!UserContext.isSuperuser()) {
            UserContext.Data ctx = UserContext.get();
            Long myCondoId = ctx != null ? ctx.condominiumId() : null;

            if (myCondoId == null || !myCondoId.equals(id)) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Acesso negado: você só pode visualizar o seu próprio condomínio"
                );
            }
        }

        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", id));

        long unitCount     = unitRepo.countByTenantIdAndCondominiumId(tenantId, id);
        long residentCount = residentRepo.countByTenantIdAndCondominiumId(tenantId, id);

        return CondominiumResponse.withCounts(condominium, unitCount, residentCount);
    }

    /**
     * Cria um novo condomínio.
     *
     * Requer SUPERUSER (garantido pela camada de segurança no Controller).
     */
    @Transactional
    public CondominiumResponse create(CreateCondominiumRequest request) {
        String tenantId = TenantContext.get();

        Condominium condominium = new Condominium();
        condominium.setTenantId(tenantId);
        condominium.setName(request.name().trim());
        condominium.setCnpj(request.cnpj() != null ? request.cnpj().trim() : "");
        if (request.active() != null) {
            condominium.setActive(request.active());
        }
        applyVisitorPolicySettings(
            condominium,
            request.allowSyndicApproveVisitor(),
            request.residentApprovalRequired(),
            request.adminOverrideAllowed(),
            request.portariaCanAutoApprove()
        );
        applyParkingSettings(
            condominium,
            request.parkingPolicyMode(),
            request.parkingDrawFrequency(),
            request.drawIntervalMonths(),
            request.allowManualAssignments(),
            request.allowResidentRegistration(),
            request.maxVehiclesPerUnit(),
            request.parkingRules()
        );
        applyReservationSettings(
            condominium,
            request.reservationPolicyMode(),
            request.defaultMaxDurationHours(),
            request.defaultStartHour(),
            request.defaultEndHour(),
            request.allDayReservationAllowed(),
            request.reservationApprovalMode(),
            request.reservationRules()
        );

        condominium = condominiumRepo.save(condominium);
        auditService.log("CREATE", "Condominium", condominium.getId(), condominium.getId(), null, CondominiumResponse.from(condominium));

        return CondominiumResponse.from(condominium);
    }

    /**
     * Atualiza um condomínio existente.
     *
     * Requer SUPERUSER ou ADMIN (garantido pela camada de segurança no Controller).
     */
    @Transactional
    public CondominiumResponse update(Long id, UpdateCondominiumRequest request) {
        String tenantId = TenantContext.get();

        // ADMIN só pode atualizar o próprio condomínio
        if (!UserContext.isSuperuser()) {
            UserContext.Data ctx = UserContext.get();
            Long myCondoId = ctx != null ? ctx.condominiumId() : null;
            if (myCondoId == null || !myCondoId.equals(id)) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Acesso negado: você só pode editar o seu próprio condomínio"
                );
            }
        }

        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", id));

        CondominiumResponse before = CondominiumResponse.from(condominium);

        if (request.name() != null) {
            condominium.setName(request.name().trim());
        }
        if (request.cnpj() != null) {
            condominium.setCnpj(request.cnpj().trim());
        }
        if (request.active() != null) {
            condominium.setActive(request.active());
        }
        applyVisitorPolicySettings(
            condominium,
            request.allowSyndicApproveVisitor(),
            request.residentApprovalRequired(),
            request.adminOverrideAllowed(),
            request.portariaCanAutoApprove()
        );
        applyParkingSettings(
            condominium,
            request.parkingPolicyMode(),
            request.parkingDrawFrequency(),
            request.drawIntervalMonths(),
            request.allowManualAssignments(),
            request.allowResidentRegistration(),
            request.maxVehiclesPerUnit(),
            request.parkingRules()
        );
        applyReservationSettings(
            condominium,
            request.reservationPolicyMode(),
            request.defaultMaxDurationHours(),
            request.defaultStartHour(),
            request.defaultEndHour(),
            request.allDayReservationAllowed(),
            request.reservationApprovalMode(),
            request.reservationRules()
        );

        condominium = condominiumRepo.save(condominium);
        auditService.log("CONFIG_CHANGED", "Condominium", condominium.getId(), condominium.getId(), before, CondominiumResponse.from(condominium));

        return CondominiumResponse.from(condominium);
    }

    /**
     * Deleta um condomínio.
     *
     * Requer SUPERUSER (garantido pelo Controller).
     * Regra de negócio: não permite deletar se houver unidades ou moradores vinculados.
     */
    @Transactional
    public void delete(Long id) {
        String tenantId = TenantContext.get();

        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", id));

        long unitCount     = unitRepo.countByTenantIdAndCondominiumId(tenantId, id);
        long residentCount = residentRepo.countByTenantIdAndCondominiumId(tenantId, id);

        if (unitCount > 0 || residentCount > 0) {
            throw new BusinessException(
                String.format(
                    "Não é possível deletar o condomínio pois há %d unidade(s) e %d morador(es) vinculado(s)",
                    unitCount,
                    residentCount
                )
            );
        }

        CondominiumResponse before = CondominiumResponse.from(condominium);
        condominiumRepo.delete(condominium);
        auditService.log("DELETE", "Condominium", id, id, before, null);
    }

    @Transactional
    public CondominiumResponse setActive(Long id, boolean active) {
        String tenantId = TenantContext.get();
        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", id));
        CondominiumResponse before = CondominiumResponse.from(condominium);
        condominium.setActive(active);
        condominium = condominiumRepo.save(condominium);
        auditService.log(active ? "ACTIVATE" : "DEACTIVATE", "Condominium", id, id, before, CondominiumResponse.from(condominium));
        return CondominiumResponse.from(condominium);
    }

    private void applyVisitorPolicySettings(
        Condominium condominium,
        Boolean allowSyndicApproveVisitor,
        Boolean residentApprovalRequired,
        Boolean adminOverrideAllowed,
        Boolean portariaCanAutoApprove
    ) {
        if (allowSyndicApproveVisitor != null) {
            condominium.setAllowSyndicApproveVisitor(allowSyndicApproveVisitor);
        }
        if (residentApprovalRequired != null) {
            condominium.setResidentApprovalRequired(residentApprovalRequired);
        }
        if (adminOverrideAllowed != null) {
            condominium.setAdminOverrideAllowed(adminOverrideAllowed);
        }
        if (portariaCanAutoApprove != null) {
            condominium.setPortariaCanAutoApprove(portariaCanAutoApprove);
        }
    }

    private void applyParkingSettings(
        Condominium condominium,
        String parkingPolicyMode,
        String parkingDrawFrequency,
        Integer drawIntervalMonths,
        Boolean allowManualAssignments,
        Boolean allowResidentRegistration,
        Integer maxVehiclesPerUnit,
        String parkingRules
    ) {
        if (parkingPolicyMode != null) {
            condominium.setParkingPolicyMode(parseParkingPolicyMode(parkingPolicyMode));
        }
        if (parkingDrawFrequency != null) {
            condominium.setParkingDrawFrequency(parseParkingDrawFrequency(parkingDrawFrequency));
        }
        if (drawIntervalMonths != null) {
            condominium.setDrawIntervalMonths(drawIntervalMonths);
        }
        if (allowManualAssignments != null) {
            condominium.setAllowManualAssignments(allowManualAssignments);
        }
        if (allowResidentRegistration != null) {
            condominium.setAllowResidentRegistration(allowResidentRegistration);
        }
        if (maxVehiclesPerUnit != null) {
            if (maxVehiclesPerUnit < 1) {
                throw new BusinessException("maxVehiclesPerUnit deve ser maior que zero");
            }
            condominium.setMaxVehiclesPerUnit(maxVehiclesPerUnit);
        }
        if (parkingRules != null) {
            condominium.setParkingRules(parkingRules.isBlank() ? null : parkingRules.trim());
        }

        if (condominium.getParkingPolicyMode() == Condominium.ParkingPolicyMode.FIXED) {
            condominium.setDrawIntervalMonths(null);
        } else if (condominium.getParkingDrawFrequency() == Condominium.ParkingDrawFrequency.CUSTOM) {
            Integer interval = drawIntervalMonths != null ? drawIntervalMonths : condominium.getDrawIntervalMonths();
            if (interval == null || interval < 1) {
                throw new BusinessException("drawIntervalMonths é obrigatório quando parkingDrawFrequency = CUSTOM");
            }
            condominium.setDrawIntervalMonths(interval);
        } else {
            condominium.setDrawIntervalMonths(null);
        }
    }

    private Condominium.ParkingPolicyMode parseParkingPolicyMode(String value) {
        try {
            return Condominium.ParkingPolicyMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("parkingPolicyMode inválido. Valores aceitos: FIXED, DRAW");
        }
    }

    private Condominium.ParkingDrawFrequency parseParkingDrawFrequency(String value) {
        try {
            return Condominium.ParkingDrawFrequency.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                "parkingDrawFrequency inválido. Valores aceitos: MONTHLY, QUARTERLY, SEMIANNUAL, YEARLY, CUSTOM"
            );
        }
    }

    private void applyReservationSettings(
        Condominium condominium,
        String reservationPolicyMode,
        Integer defaultMaxDurationHours,
        Integer defaultStartHour,
        Integer defaultEndHour,
        Boolean allDayReservationAllowed,
        String reservationApprovalMode,
        String reservationRules
    ) {
        if (reservationPolicyMode != null) {
            condominium.setReservationPolicyMode(parseReservationPolicyMode(reservationPolicyMode));
        }
        if (defaultMaxDurationHours != null) {
            if (defaultMaxDurationHours < 1) {
                throw new BusinessException("defaultMaxDurationHours deve ser maior que zero");
            }
            condominium.setDefaultMaxDurationHours(defaultMaxDurationHours);
        }
        if (defaultStartHour != null) {
            validateHour("defaultStartHour", defaultStartHour);
            condominium.setDefaultStartHour(defaultStartHour);
        }
        if (defaultEndHour != null) {
            validateHour("defaultEndHour", defaultEndHour);
            condominium.setDefaultEndHour(defaultEndHour);
        }
        if (allDayReservationAllowed != null) {
            condominium.setAllDayReservationAllowed(allDayReservationAllowed);
        }
        if (reservationApprovalMode != null) {
            condominium.setReservationApprovalMode(parseReservationApprovalMode(reservationApprovalMode));
        }
        if (reservationRules != null) {
            condominium.setReservationRules(reservationRules.isBlank() ? null : reservationRules.trim());
        }

        if (!condominium.isAllDayReservationAllowed()
            && condominium.getDefaultEndHour() <= condominium.getDefaultStartHour()) {
            throw new BusinessException("defaultEndHour deve ser maior que defaultStartHour quando a reserva não é all-day");
        }
    }

    private Condominium.ReservationPolicyMode parseReservationPolicyMode(String value) {
        try {
            return Condominium.ReservationPolicyMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("reservationPolicyMode inválido. Valores aceitos: FLEXIBLE_INTERVAL, FIXED_WINDOW");
        }
    }

    private Condominium.ReservationApprovalMode parseReservationApprovalMode(String value) {
        try {
            return Condominium.ReservationApprovalMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("reservationApprovalMode inválido. Valores aceitos: AUTOMATIC, REQUIRE_APPROVAL");
        }
    }

    private void validateHour(String field, int hour) {
        if (hour < 0 || hour > 23) {
            throw new BusinessException(field + " deve estar entre 0 e 23");
        }
    }
}
