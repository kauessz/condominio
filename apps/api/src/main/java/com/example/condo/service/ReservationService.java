package com.example.condo.service;

import com.example.condo.entity.CommonArea;
import com.example.condo.entity.Condominium;
import com.example.condo.entity.Reservation;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CommonAreaRepository;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ReservationRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    private final CommonAreaRepository areaRepo;
    private final CondominiumRepository condominiumRepo;
    private final ReservationRepository reservationRepo;
    private final UnitRepository unitRepo;
    private final AuditService auditService;

    public ReservationService(CommonAreaRepository areaRepo,
                              CondominiumRepository condominiumRepo,
                              ReservationRepository reservationRepo,
                              UnitRepository unitRepo,
                              AuditService auditService) {
        this.areaRepo = areaRepo;
        this.condominiumRepo = condominiumRepo;
        this.reservationRepo = reservationRepo;
        this.unitRepo = unitRepo;
        this.auditService = auditService;
    }

    // ── Áreas Comuns ──────────────────────────────────────────────

    public Page<CommonArea> listAreas(Long condoIdParam, Pageable pageable) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (UserContext.isSuperuser() && condoId == null) {
            return areaRepo.findAllActiveByTenant(tenant, pageable);
        }
        if (condoId == null) return Page.empty(pageable);
        return areaRepo.findAllActive(tenant, condoId, pageable);
    }

    public CommonArea getArea(Long id) {
        String tenant = TenantContext.get();
        return areaRepo.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new ResourceNotFoundException("Área comum", "id", id));
    }

    @Transactional
    public CommonArea createArea(Long condoIdParam, String name, Integer capacity,
                                 String rules, int maxHours, boolean requiresApproval,
                                 Integer allowedStartHour, Integer allowedEndHour,
                                 String reservationDescription, String reservationApprovalMode,
                                 boolean allowOverrideFromCondominiumDefault) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (condoId == null) throw new BusinessException("condominiumId é obrigatório");

        CommonArea area = new CommonArea();
        area.setTenantId(tenant);
        area.setCondominiumId(condoId);
        area.setName(name.trim());
        area.setCapacity(capacity);
        area.setRules(rules);
        area.setMaxHoursPerReservation(maxHours > 0 ? maxHours : 4);
        area.setRequiresApproval(requiresApproval);
        area.setAllowedStartHour(allowedStartHour);
        area.setAllowedEndHour(allowedEndHour);
        area.setReservationDescription(reservationDescription);
        area.setReservationApprovalMode(parseApprovalMode(reservationApprovalMode, requiresApproval));
        area.setAllowOverrideFromCondominiumDefault(allowOverrideFromCondominiumDefault);
        area.setActive(true);
        area.setCreatedAt(Instant.now());
        area = areaRepo.save(area);
        auditService.log("CREATE", "CommonArea", area.getId(), area.getCondominiumId(), null, area);
        return area;
    }

    @Transactional
    public CommonArea updateArea(Long id, String name, Integer capacity,
                                 String rules, int maxHours, boolean requiresApproval, boolean active,
                                 Integer allowedStartHour, Integer allowedEndHour,
                                 String reservationDescription, String reservationApprovalMode,
                                 boolean allowOverrideFromCondominiumDefault) {
        CommonArea area = getArea(id);
        CommonArea before = copyArea(area);
        enforceSameCondominium(area.getCondominiumId());
        if (name != null) area.setName(name.trim());
        if (capacity != null) area.setCapacity(capacity);
        if (rules != null) area.setRules(rules);
        if (maxHours > 0) area.setMaxHoursPerReservation(maxHours);
        area.setRequiresApproval(requiresApproval);
        area.setAllowedStartHour(allowedStartHour);
        area.setAllowedEndHour(allowedEndHour);
        area.setReservationDescription(reservationDescription);
        area.setReservationApprovalMode(parseApprovalMode(reservationApprovalMode, requiresApproval));
        area.setAllowOverrideFromCondominiumDefault(allowOverrideFromCondominiumDefault);
        area.setActive(active);
        area = areaRepo.save(area);
        auditService.log("CONFIG_CHANGED", "CommonArea", area.getId(), area.getCondominiumId(), before, area);
        return area;
    }

    @Transactional
    public void deleteArea(Long id) {
        CommonArea area = getArea(id);
        CommonArea before = copyArea(area);
        enforceSameCondominium(area.getCondominiumId());
        area.setActive(false);
        areaRepo.save(area);
        auditService.log("DELETE", "CommonArea", area.getId(), area.getCondominiumId(), before, area);
    }

    // ── Reservas ──────────────────────────────────────────────────

    public Page<Reservation> listReservations(Long condoIdParam, Long areaId, Long unitIdParam,
                                               String statusStr, Pageable pageable) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);

        Long effectiveUnitId = unitIdParam;
        if (isMorador()) effectiveUnitId = UserContext.unitId();

        Reservation.Status status = statusStr != null ? Reservation.Status.valueOf(statusStr.toUpperCase()) : null;
        if (UserContext.isSuperuser() && condoId == null) {
            return reservationRepo.searchAllCondos(tenant, areaId, effectiveUnitId, status, pageable);
        }
        if (condoId == null) return Page.empty(pageable);
        return reservationRepo.search(tenant, condoId, areaId, effectiveUnitId, status, pageable);
    }

    public Reservation getReservation(Long id) {
        String tenant = TenantContext.get();
        Reservation r = reservationRepo.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new ResourceNotFoundException("Reserva", "id", id));
        enforceSameCondominium(r.getCondominiumId());
        return r;
    }

    @Transactional
    public Reservation createReservation(Long condoIdParam, Long areaId, Long unitIdParam,
                                          Instant start, Instant end, String title, String notes) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (condoId == null) throw new BusinessException("condominiumId é obrigatório");

        CommonArea area = areaRepo.findByTenantIdAndId(tenant, areaId)
            .orElseThrow(() -> new ResourceNotFoundException("Área comum", "id", areaId));
        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenant, condoId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condoId));

        if (!area.getCondominiumId().equals(condoId)) {
            throw new BusinessException("Área não pertence ao condomínio");
        }

        Long unitId = resolveReservationUnitId(unitIdParam);
        if (unitId == null) throw new BusinessException("unitId é obrigatório");
        validateUnitScope(tenant, condoId, unitId);

        // Validação: 30 min de antecedência
        if (start.isBefore(Instant.now().plus(30, ChronoUnit.MINUTES))) {
            throw new BusinessException("Reserva deve ser feita com pelo menos 30 minutos de antecedência");
        }

        // Validação: máximo de horas
        ReservationPolicy policy = resolvePolicy(condominium, area);
        validateReservationWindow(policy, start, end);

        // Verificar conflito
        List<Reservation> conflicts = reservationRepo.findConflicts(areaId, start, end, null);
        if (!conflicts.isEmpty()) {
            throw new BusinessException("Conflito de horário: já existe uma reserva neste período");
        }

        Reservation res = new Reservation();
        res.setTenantId(tenant);
        res.setCondominiumId(condoId);
        res.setCommonAreaId(areaId);
        res.setUnitId(unitId);
        res.setStartDatetime(start);
        res.setEndDatetime(end);
        res.setTitle(title);
        res.setNotes(notes);
        res.setCreatedBy(UserContext.userId());
        res.setCreatedAt(Instant.now());

        // Auto-aprovação se área não requer aprovação
        if (!policy.requiresApproval()) {
            res.setStatus(Reservation.Status.APPROVED);
            res.setApprovedAt(Instant.now());
        } else {
            res.setStatus(Reservation.Status.PENDING);
        }

        try {
            res = reservationRepo.save(res);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Conflito de horário: já existe uma reserva neste período");
        }
        auditService.log("CREATE", "Reservation", res.getId(), res.getCondominiumId(), null, res);
        return res;
    }

    @Transactional
    public Reservation approve(Long id) {
        Reservation res = getReservation(id);
        Reservation before = copyReservation(res);
        if (res.getStatus() != Reservation.Status.PENDING) {
            throw new BusinessException("Apenas reservas pendentes podem ser aprovadas");
        }
        res.setStatus(Reservation.Status.APPROVED);
        res.setApprovedAt(Instant.now());
        res.setApprovedBy(UserContext.userId());
        res = reservationRepo.save(res);
        auditService.log("APPROVE", "Reservation", res.getId(), res.getCondominiumId(), before, res);
        return res;
    }

    @Transactional
    public Reservation reject(Long id, String reason) {
        Reservation res = getReservation(id);
        Reservation before = copyReservation(res);
        if (res.getStatus() != Reservation.Status.PENDING) {
            throw new BusinessException("Apenas reservas pendentes podem ser rejeitadas");
        }
        res.setStatus(Reservation.Status.REJECTED);
        res.setRejectionReason(reason);
        res = reservationRepo.save(res);
        auditService.log("REJECT", "Reservation", res.getId(), res.getCondominiumId(), before, res);
        return res;
    }

    @Transactional
    public Reservation cancel(Long id) {
        Reservation res = getReservation(id);
        Reservation before = copyReservation(res);
        if (res.getStatus() == Reservation.Status.CANCELLED) {
            throw new BusinessException("Reserva já cancelada");
        }
        if (res.getStatus() == Reservation.Status.COMPLETED) {
            throw new BusinessException("Não é possível cancelar reserva concluída");
        }
        // MORADOR só pode cancelar a própria unidade
        if (isMorador() && !res.getUnitId().equals(UserContext.unitId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
        res.setStatus(Reservation.Status.CANCELLED);
        res.setCancelledAt(Instant.now());
        res.setCancelledBy(UserContext.userId());
        res = reservationRepo.save(res);
        auditService.log("CANCEL", "Reservation", res.getId(), res.getCondominiumId(), before, res);
        return res;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private boolean isMorador() {
        UserContext.Data ctx = UserContext.get();
        return ctx != null && "MORADOR".equalsIgnoreCase(ctx.role());
    }

    private Long resolveReservationUnitId(Long unitIdParam) {
        if (isMorador()) {
            return UserContext.unitId();
        }
        Long currentUnitId = UserContext.unitId();
        if (currentUnitId != null && unitIdParam == null) {
            return currentUnitId;
        }
        return unitIdParam;
    }

    private void validateUnitScope(String tenantId, Long condominiumId, Long unitId) {
        if (!unitRepo.existsByTenantIdAndIdAndCondominiumId(tenantId, unitId, condominiumId)) {
            throw new BusinessException("A unidade informada não pertence ao condomínio selecionado");
        }
    }

    private void enforceSameCondominium(Long condoId) {
        Long effective = UserContext.resolveCondominiumId(condoId);
        if (effective != null && !effective.equals(condoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
    }

    private CommonArea copyArea(CommonArea area) {
        CommonArea copy = new CommonArea();
        copy.setId(area.getId());
        copy.setTenantId(area.getTenantId());
        copy.setCondominiumId(area.getCondominiumId());
        copy.setName(area.getName());
        copy.setCapacity(area.getCapacity());
        copy.setRules(area.getRules());
        copy.setMaxHoursPerReservation(area.getMaxHoursPerReservation());
        copy.setRequiresApproval(area.isRequiresApproval());
        copy.setAllowedStartHour(area.getAllowedStartHour());
        copy.setAllowedEndHour(area.getAllowedEndHour());
        copy.setReservationDescription(area.getReservationDescription());
        copy.setReservationApprovalMode(area.getReservationApprovalMode());
        copy.setAllowOverrideFromCondominiumDefault(area.isAllowOverrideFromCondominiumDefault());
        copy.setActive(area.isActive());
        copy.setCreatedAt(area.getCreatedAt());
        return copy;
    }

    private Reservation copyReservation(Reservation source) {
        Reservation copy = new Reservation();
        copy.setId(source.getId());
        copy.setTenantId(source.getTenantId());
        copy.setCondominiumId(source.getCondominiumId());
        copy.setCommonAreaId(source.getCommonAreaId());
        copy.setUnitId(source.getUnitId());
        copy.setResidentId(source.getResidentId());
        copy.setStartDatetime(source.getStartDatetime());
        copy.setEndDatetime(source.getEndDatetime());
        copy.setTitle(source.getTitle());
        copy.setNotes(source.getNotes());
        copy.setStatus(source.getStatus());
        copy.setApprovedBy(source.getApprovedBy());
        copy.setApprovedAt(source.getApprovedAt());
        copy.setRejectionReason(source.getRejectionReason());
        copy.setCancelledAt(source.getCancelledAt());
        copy.setCancelledBy(source.getCancelledBy());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private CommonArea.ReservationApprovalMode parseApprovalMode(String value, boolean requiresApproval) {
        if (value == null || value.isBlank()) {
            return requiresApproval
                ? CommonArea.ReservationApprovalMode.REQUIRE_APPROVAL
                : CommonArea.ReservationApprovalMode.AUTOMATIC;
        }
        try {
            return CommonArea.ReservationApprovalMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Modo de aprovação inválido: " + value);
        }
    }

    private ReservationPolicy resolvePolicy(Condominium condominium, CommonArea area) {
        boolean areaOverrides = area.isAllowOverrideFromCondominiumDefault();
        int maxDurationHours = areaOverrides && area.getMaxHoursPerReservation() > 0
            ? area.getMaxHoursPerReservation()
            : condominium.getDefaultMaxDurationHours();
        Integer startHour = areaOverrides && area.getAllowedStartHour() != null
            ? area.getAllowedStartHour()
            : condominium.getDefaultStartHour();
        Integer endHour = areaOverrides && area.getAllowedEndHour() != null
            ? area.getAllowedEndHour()
            : condominium.getDefaultEndHour();
        CommonArea.ReservationApprovalMode approvalMode = areaOverrides && area.getReservationApprovalMode() != null
            ? area.getReservationApprovalMode()
            : (condominium.getReservationApprovalMode() == Condominium.ReservationApprovalMode.REQUIRE_APPROVAL
                ? CommonArea.ReservationApprovalMode.REQUIRE_APPROVAL
                : CommonArea.ReservationApprovalMode.AUTOMATIC);
        return new ReservationPolicy(
            condominium.getReservationPolicyMode(),
            maxDurationHours,
            startHour,
            endHour,
            condominium.isAllDayReservationAllowed(),
            approvalMode == CommonArea.ReservationApprovalMode.REQUIRE_APPROVAL
        );
    }

    private void validateReservationWindow(ReservationPolicy policy, Instant start, Instant end) {
        long minutes = ChronoUnit.MINUTES.between(start, end);
        if (minutes <= 0) {
            throw new BusinessException("Período da reserva inválido");
        }
        if (minutes > policy.maxDurationHours() * 60L) {
            throw new BusinessException("Duração inválida. Máximo configurado: " + policy.maxDurationHours() + " horas");
        }
        if (policy.allDayReservationAllowed()) {
            return;
        }

        var startDateTime = start.atZone(java.time.ZoneId.systemDefault());
        var endDateTime = end.atZone(java.time.ZoneId.systemDefault());
        if (!startDateTime.toLocalDate().equals(endDateTime.toLocalDate())) {
            throw new BusinessException("A reserva deve começar e terminar no mesmo dia");
        }

        int startHour = startDateTime.getHour();
        int endHour = endDateTime.getHour();
        boolean startsBeforeWindow = startHour < policy.startHour();
        boolean endsAfterWindow = endDateTime.getHour() > policy.endHour()
            || (endDateTime.getHour() == policy.endHour() && endDateTime.getMinute() > 0);
        if (startsBeforeWindow || endsAfterWindow) {
            throw new BusinessException("Horário inválido. Permitido entre "
                + formatHour(policy.startHour()) + " e " + formatHour(policy.endHour()));
        }
    }

    private String formatHour(int hour) {
        return String.format("%02d:00", hour);
    }

    private record ReservationPolicy(
        Condominium.ReservationPolicyMode mode,
        int maxDurationHours,
        int startHour,
        int endHour,
        boolean allDayReservationAllowed,
        boolean requiresApproval
    ) {}
}
