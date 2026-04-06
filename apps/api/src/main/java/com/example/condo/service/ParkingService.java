package com.example.condo.service;

import com.example.condo.dto.parking.ParkingAssignmentRequest;
import com.example.condo.dto.parking.ParkingAssignmentResponse;
import com.example.condo.dto.parking.ParkingDrawRegistrationResponse;
import com.example.condo.entity.*;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.*;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ParkingService {

    private final CondominiumRepository condominiumRepo;
    private final ParkingSpotRepository spotRepo;
    private final ParkingDrawRepository drawRepo;
    private final ParkingDrawRegistrationRepository regRepo;
    private final ParkingSpotAssignmentRepository assignRepo;
    private final UnitRepository unitRepo;
    private final ResidentRepository residentRepo;
    private final AuditService auditService;

    public ParkingService(CondominiumRepository condominiumRepo,
                           ParkingSpotRepository spotRepo,
                           ParkingDrawRepository drawRepo,
                           ParkingDrawRegistrationRepository regRepo,
                           ParkingSpotAssignmentRepository assignRepo,
                           UnitRepository unitRepo,
                           ResidentRepository residentRepo,
                           AuditService auditService) {
        this.condominiumRepo = condominiumRepo;
        this.spotRepo = spotRepo;
        this.drawRepo = drawRepo;
        this.regRepo = regRepo;
        this.assignRepo = assignRepo;
        this.unitRepo = unitRepo;
        this.residentRepo = residentRepo;
        this.auditService = auditService;
    }

    // ── Vagas ─────────────────────────────────────────────────────

    public Page<ParkingSpot> listSpots(Long condoIdParam, Pageable pageable) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (UserContext.isSuperuser() && condoId == null) {
            return spotRepo.findAllByTenant(tenant, pageable);
        }
        if (condoId == null) return Page.empty(pageable);
        return spotRepo.findAll(tenant, condoId, pageable);
    }

    @Transactional
    public ParkingSpot createSpot(Long condoIdParam, String code, String description) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (condoId == null) throw new BusinessException("condominiumId é obrigatório");

        ParkingSpot spot = new ParkingSpot();
        spot.setTenantId(tenant);
        spot.setCondominiumId(condoId);
        spot.setCode(code.trim().toUpperCase());
        spot.setDescription(description);
        spot.setActive(true);
        spot.setCreatedAt(Instant.now());
        spot = spotRepo.save(spot);
        auditService.log("CREATE", "ParkingSpot", spot.getId(), spot.getCondominiumId(), null, spot);
        return spot;
    }

    @Transactional
    public ParkingSpot updateSpot(Long id, String code, String description, boolean active) {
        String tenant = TenantContext.get();
        ParkingSpot spot = spotRepo.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new ResourceNotFoundException("Vaga", "id", id));
        ParkingSpot before = copySpot(spot);
        enforceSameCondominium(spot.getCondominiumId());
        if (code != null) spot.setCode(code.trim().toUpperCase());
        if (description != null) spot.setDescription(description);
        spot.setActive(active);
        spot = spotRepo.save(spot);
        auditService.log("UPDATE", "ParkingSpot", spot.getId(), spot.getCondominiumId(), before, spot);
        return spot;
    }

    @Transactional
    public void deleteSpot(Long id) {
        String tenant = TenantContext.get();
        ParkingSpot spot = spotRepo.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new ResourceNotFoundException("Vaga", "id", id));
        ParkingSpot before = copySpot(spot);
        enforceSameCondominium(spot.getCondominiumId());
        spot.setActive(false);
        spotRepo.save(spot);
        auditService.log("DELETE", "ParkingSpot", spot.getId(), spot.getCondominiumId(), before, spot);
    }

    // ── Sorteios ──────────────────────────────────────────────────

    public Page<ParkingDraw> listDraws(Long condoIdParam, Pageable pageable) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (UserContext.isSuperuser() && condoId == null) {
            return drawRepo.findAllByTenant(tenant, pageable);
        }
        if (condoId == null) return Page.empty(pageable);
        return drawRepo.findAll(tenant, condoId, pageable);
    }

    public ParkingDraw getDraw(Long id) {
        String tenant = TenantContext.get();
        ParkingDraw draw = drawRepo.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new ResourceNotFoundException("Sorteio", "id", id));
        enforceSameCondominium(draw.getCondominiumId());
        return draw;
    }

    @Transactional
    public ParkingDraw createDraw(Long condoIdParam, String name,
                                   Instant regOpen, Instant regClose,
                                   LocalDate validFrom, LocalDate validUntil) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (condoId == null) throw new BusinessException("condominiumId é obrigatório");
        ensureDrawPolicyEnabled(tenant, condoId);

        ParkingDraw draw = new ParkingDraw();
        draw.setTenantId(tenant);
        draw.setCondominiumId(condoId);
        draw.setName(name.trim());
        draw.setRegistrationOpenAt(regOpen);
        draw.setRegistrationCloseAt(regClose);
        draw.setValidFrom(validFrom);
        draw.setValidUntil(validUntil);
        draw.setStatus(ParkingDraw.Status.OPEN);
        draw.setCreatedBy(UserContext.userId());
        draw.setCreatedAt(Instant.now());
        draw = drawRepo.save(draw);
        auditService.log("CREATE", "ParkingDraw", draw.getId(), draw.getCondominiumId(), null, draw);
        return draw;
    }

    @Transactional
    public ParkingDrawRegistration registerForDraw(Long drawId) {
        ParkingDraw draw = getDraw(drawId);
        ensureResidentRegistrationAllowed(TenantContext.get(), draw.getCondominiumId());

        if (draw.getStatus() != ParkingDraw.Status.OPEN) {
            throw new BusinessException("Inscrições não estão abertas para este sorteio");
        }
        Instant now = Instant.now();
        if (now.isAfter(draw.getRegistrationCloseAt())) {
            throw new BusinessException("Prazo de inscrição encerrado");
        }

        Long unitId = UserContext.unitId();
        if (unitId == null) throw new BusinessException("Usuário sem unidade vinculada");

        if (regRepo.existsByDrawIdAndUnitId(drawId, unitId)) {
            throw new BusinessException("Unidade já inscrita neste sorteio");
        }

        ParkingDrawRegistration reg = new ParkingDrawRegistration();
        reg.setDrawId(drawId);
        reg.setTenantId(TenantContext.get());
        reg.setCondominiumId(draw.getCondominiumId());
        reg.setUnitId(unitId);
        Resident currentResident = residentRepo.findByTenantIdAndUserId(TenantContext.get(), UserContext.userId()).orElse(null);
        if (currentResident != null) {
            reg.setResidentId(currentResident.getId());
        }
        reg.setRegisteredAt(Instant.now());
        reg = regRepo.save(reg);
        auditService.log("REGISTER", "ParkingDrawRegistration", reg.getId(), draw.getCondominiumId(), null, reg);
        return reg;
    }

    @Transactional
    public void unregisterFromDraw(Long drawId) {
        ParkingDraw draw = getDraw(drawId);
        ensureDrawPolicyEnabled(TenantContext.get(), draw.getCondominiumId());
        if (draw.getStatus() != ParkingDraw.Status.OPEN) {
            throw new BusinessException("Não é possível cancelar inscrição: sorteio não está aberto");
        }
        Long unitId = UserContext.unitId();
        ParkingDrawRegistration reg = regRepo.findByDrawIdAndUnitId(drawId, unitId)
            .orElseThrow(() -> new BusinessException("Unidade não inscrita neste sorteio"));
        regRepo.delete(reg);
    }

    public List<ParkingDrawRegistrationResponse> getRegistrations(Long drawId) {
        ParkingDraw draw = getDraw(drawId);
        String tenant = TenantContext.get();
        List<ParkingDrawRegistration> registrations = regRepo.findByDrawId(drawId);
        Map<Long, Unit> unitsById = loadUnitsById(tenant, registrations.stream().map(ParkingDrawRegistration::getUnitId).toList());
        Map<Long, Resident> residentsByUnitId = loadResidentsByUnitId(tenant, registrations.stream().map(ParkingDrawRegistration::getUnitId).toList());
        Set<Long> unitsWithActiveAssignment = loadUnitsWithActiveAssignment(tenant, draw.getCondominiumId());
        return registrations.stream()
            .map(registration -> new ParkingDrawRegistrationResponse(
                registration.getId(),
                registration.getDrawId(),
                registration.getCondominiumId(),
                registration.getUnitId(),
                buildUnitLabel(unitsById.get(registration.getUnitId()), registration.getUnitId()),
                registration.getResidentId(),
                resolveResidentName(registration, residentsByUnitId),
                registration.getRegisteredAt(),
                unitsWithActiveAssignment.contains(registration.getUnitId())
            ))
            .toList();
    }

    @Transactional
    public List<ParkingSpotAssignment> executeDraw(Long drawId) {
        ParkingDraw draw = getDraw(drawId);
        ParkingDraw before = copyDraw(draw);
        ensureDrawPolicyEnabled(TenantContext.get(), draw.getCondominiumId());

        if (draw.getStatus() != ParkingDraw.Status.OPEN && draw.getStatus() != ParkingDraw.Status.CLOSED) {
            throw new BusinessException("Sorteio não pode ser executado no status atual: " + draw.getStatus());
        }

        String tenant = TenantContext.get();
        Long condoId = draw.getCondominiumId();

        List<ParkingDrawRegistration> registrations = regRepo.findByDrawId(drawId);
        if (registrations.isEmpty()) {
            throw new BusinessException("Nenhuma inscrição para sortear");
        }

        List<ParkingSpot> availableSpots = spotRepo.findAllActive(tenant, condoId).stream()
            .filter(spot -> !assignRepo.existsActiveConflictForSpot(
                tenant, condoId, spot.getId(), draw.getValidFrom(), draw.getValidUntil(), null))
            .toList();
        if (availableSpots.isEmpty()) {
            throw new BusinessException("Nenhuma vaga disponível para o sorteio");
        }

        // Cancelar atribuições anteriores do mesmo sorteio (re-execução)
        List<ParkingSpotAssignment> existing = assignRepo.findByDrawId(drawId);
        for (ParkingSpotAssignment a : existing) {
            a.setStatus(ParkingSpotAssignment.Status.CANCELLED);
        }
        assignRepo.saveAll(existing);

        // Embaralhar inscrições com SecureRandom
        List<ParkingDrawRegistration> shuffled = registrations.stream()
            .filter(registration -> !assignRepo.existsActiveConflictForUnit(
                tenant, condoId, registration.getUnitId(), draw.getValidFrom(), draw.getValidUntil(), null))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (shuffled.isEmpty()) {
            throw new BusinessException("Todas as unidades inscritas já possuem atribuição ativa no período informado.");
        }
        Collections.shuffle(shuffled, new SecureRandom());

        int spotsCount = Math.min(shuffled.size(), availableSpots.size());
        List<ParkingSpotAssignment> assignments = new ArrayList<>();

        for (int i = 0; i < spotsCount; i++) {
            ParkingDrawRegistration reg = shuffled.get(i);
            ParkingSpot spot = availableSpots.get(i);

            ParkingSpotAssignment assignment = new ParkingSpotAssignment();
            assignment.setTenantId(tenant);
            assignment.setCondominiumId(condoId);
            assignment.setSpotId(spot.getId());
            assignment.setUnitId(reg.getUnitId());
            assignment.setDrawId(drawId);
            assignment.setValidFrom(draw.getValidFrom());
            assignment.setValidUntil(draw.getValidUntil());
            assignment.setStatus(ParkingSpotAssignment.Status.ACTIVE);
            assignment.setCreatedAt(Instant.now());
            assignments.add(assignment);
        }

        assignRepo.saveAll(assignments);

        draw.setStatus(ParkingDraw.Status.EXECUTED);
        draw.setExecutedAt(Instant.now());
        draw.setExecutedBy(UserContext.userId());
        drawRepo.save(draw);
        auditService.log("EXECUTE", "ParkingDraw", draw.getId(), draw.getCondominiumId(), before, draw);

        return assignments;
    }

    public Optional<ParkingSpotAssignment> getMyAssignment(Long condoIdParam) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (UserContext.isSuperuser() && condoId == null) return Optional.empty();
        if (condoId == null) return Optional.empty();
        Long unitId = UserContext.unitId();
        if (unitId == null) return Optional.empty();
        return assignRepo.findActiveAssignmentForUnit(tenant, condoId, unitId, LocalDate.now());
    }

    public List<ParkingAssignmentResponse> getAllAssignments(Long condoIdParam) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        List<ParkingSpotAssignment> assignments;
        if (UserContext.isSuperuser() && condoId == null) {
            assignments = assignRepo.findAllActiveByTenant(tenant);
        } else {
            if (condoId == null) return List.of();
            assignments = assignRepo.findAllActiveForCondo(tenant, condoId);
        }
        return toAssignmentResponses(tenant, assignments);
    }

    @Transactional
    public ParkingAssignmentResponse createManualAssignment(ParkingAssignmentRequest request) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(request.condominiumId());
        validateManualAssignmentPayload(tenant, condoId, request.spotId(), request.unitId(), request.validFrom(), request.validUntil(), null);

        ParkingSpotAssignment assignment = new ParkingSpotAssignment();
        assignment.setTenantId(tenant);
        assignment.setCondominiumId(condoId);
        assignment.setSpotId(request.spotId());
        assignment.setUnitId(request.unitId());
        assignment.setDrawId(null);
        assignment.setValidFrom(request.validFrom());
        assignment.setValidUntil(request.validUntil());
        assignment.setStatus(ParkingSpotAssignment.Status.ACTIVE);
        assignment.setCreatedAt(Instant.now());
        assignment = assignRepo.save(assignment);
        ParkingAssignmentResponse response = toAssignmentResponses(tenant, List.of(assignment)).stream().findFirst()
            .orElseThrow(() -> new BusinessException("Falha ao montar a atribuição criada."));
        auditService.log("CREATE", "ParkingSpotAssignment", assignment.getId(), assignment.getCondominiumId(), null, response);
        return response;
    }

    @Transactional
    public ParkingAssignmentResponse updateAssignment(Long id, ParkingAssignmentRequest request) {
        String tenant = TenantContext.get();
        ParkingSpotAssignment assignment = assignRepo.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new ResourceNotFoundException("Atribuição", "id", id));
        enforceSameCondominium(assignment.getCondominiumId());
        ParkingAssignmentResponse before = toAssignmentResponses(tenant, List.of(assignment)).stream().findFirst().orElse(null);

        Long condoId = assignment.getCondominiumId();
        Long spotId = request.spotId() != null ? request.spotId() : assignment.getSpotId();
        Long unitId = request.unitId() != null ? request.unitId() : assignment.getUnitId();
        LocalDate validFrom = request.validFrom() != null ? request.validFrom() : assignment.getValidFrom();
        LocalDate validUntil = request.validUntil() != null ? request.validUntil() : assignment.getValidUntil();

        validateManualAssignmentPayload(tenant, condoId, spotId, unitId, validFrom, validUntil, assignment.getId());
        assignment.setSpotId(spotId);
        assignment.setUnitId(unitId);
        assignment.setValidFrom(validFrom);
        assignment.setValidUntil(validUntil);
        assignment = assignRepo.save(assignment);
        ParkingAssignmentResponse after = toAssignmentResponses(tenant, List.of(assignment)).stream().findFirst()
            .orElseThrow(() -> new BusinessException("Falha ao montar a atribuição atualizada."));
        auditService.log("UPDATE", "ParkingSpotAssignment", assignment.getId(), assignment.getCondominiumId(), before, after);
        return after;
    }

    @Transactional
    public void cancelAssignment(Long id) {
        String tenant = TenantContext.get();
        ParkingSpotAssignment assignment = assignRepo.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new ResourceNotFoundException("Atribuição", "id", id));
        enforceSameCondominium(assignment.getCondominiumId());
        ParkingAssignmentResponse before = toAssignmentResponses(tenant, List.of(assignment)).stream().findFirst().orElse(null);
        assignment.setStatus(ParkingSpotAssignment.Status.CANCELLED);
        assignRepo.save(assignment);
        auditService.log("CANCEL", "ParkingSpotAssignment", assignment.getId(), assignment.getCondominiumId(), before, null);
    }

    private void enforceSameCondominium(Long condoId) {
        Long effective = UserContext.resolveCondominiumId(condoId);
        if (effective != null && !effective.equals(condoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
    }

    private void ensureDrawPolicyEnabled(String tenantId, Long condoId) {
        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenantId, condoId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condoId));
        if (condominium.getParkingPolicyMode() != Condominium.ParkingPolicyMode.DRAW) {
            throw new BusinessException("Este condomínio está configurado para vagas fixas/manuais.");
        }
    }

    private void ensureResidentRegistrationAllowed(String tenantId, Long condoId) {
        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenantId, condoId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condoId));
        if (condominium.getParkingPolicyMode() != Condominium.ParkingPolicyMode.DRAW) {
            throw new BusinessException("Inscrições em sorteio estão desabilitadas para este condomínio.");
        }
        if (!condominium.isAllowResidentRegistration()) {
            throw new BusinessException("Este condomínio não permite inscrições de moradores em sorteios.");
        }
    }

    private void ensureManualAssignmentsAllowed(String tenantId, Long condoId) {
        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenantId, condoId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condoId));
        if (!condominium.isAllowManualAssignments()) {
            throw new BusinessException("Este condomínio não permite atribuições manuais de vagas.");
        }
    }

    private void validateManualAssignmentPayload(String tenant,
                                                 Long condoId,
                                                 Long spotId,
                                                 Long unitId,
                                                 LocalDate validFrom,
                                                 LocalDate validUntil,
                                                 Long ignoreAssignmentId) {
        if (condoId == null) {
            throw new BusinessException("condominiumId é obrigatório para atribuição manual.");
        }
        ensureManualAssignmentsAllowed(tenant, condoId);
        if (spotId == null || unitId == null || validFrom == null || validUntil == null) {
            throw new BusinessException("Informe vaga, unidade e período da atribuição.");
        }
        if (validUntil.isBefore(validFrom)) {
            throw new BusinessException("A data final da atribuição deve ser maior ou igual à inicial.");
        }

        ParkingSpot spot = spotRepo.findByTenantIdAndId(tenant, spotId)
            .orElseThrow(() -> new ResourceNotFoundException("Vaga", "id", spotId));
        if (!spot.isActive()) {
            throw new BusinessException("A vaga selecionada está inativa.");
        }
        if (!Objects.equals(spot.getCondominiumId(), condoId)) {
            throw new BusinessException("A vaga selecionada não pertence ao condomínio informado.");
        }
        if (!unitRepo.existsByTenantIdAndIdAndCondominiumId(tenant, unitId, condoId)) {
            throw new BusinessException("A unidade selecionada não pertence ao condomínio informado.");
        }
        if (assignRepo.existsActiveConflictForSpot(tenant, condoId, spotId, validFrom, validUntil, ignoreAssignmentId)) {
            throw new BusinessException("Já existe uma atribuição ativa para esta vaga no período informado.");
        }
        if (assignRepo.existsActiveConflictForUnit(tenant, condoId, unitId, validFrom, validUntil, ignoreAssignmentId)) {
            throw new BusinessException("A unidade já possui uma vaga ativa no período informado.");
        }
    }

    private List<ParkingAssignmentResponse> toAssignmentResponses(String tenant, List<ParkingSpotAssignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        Map<Long, Unit> unitsById = loadUnitsById(tenant, assignments.stream().map(ParkingSpotAssignment::getUnitId).toList());
        Map<Long, Resident> residentsByUnitId = loadResidentsByUnitId(tenant, assignments.stream().map(ParkingSpotAssignment::getUnitId).toList());
        Map<Long, ParkingSpot> spotsById = loadSpotsById(tenant, assignments.stream().map(ParkingSpotAssignment::getSpotId).toList());
        Map<Long, ParkingDraw> drawsById = loadDrawsById(tenant, assignments.stream().map(ParkingSpotAssignment::getDrawId).filter(Objects::nonNull).toList());

        return assignments.stream()
            .map(assignment -> {
                Unit unit = unitsById.get(assignment.getUnitId());
                Resident resident = residentsByUnitId.get(assignment.getUnitId());
                ParkingSpot spot = spotsById.get(assignment.getSpotId());
                ParkingDraw draw = assignment.getDrawId() != null ? drawsById.get(assignment.getDrawId()) : null;
                return new ParkingAssignmentResponse(
                    assignment.getId(),
                    assignment.getCondominiumId(),
                    assignment.getSpotId(),
                    spot != null ? spot.getCode() : "#" + assignment.getSpotId(),
                    spot != null ? spot.getDescription() : null,
                    assignment.getUnitId(),
                    buildUnitLabel(unit, assignment.getUnitId()),
                    resident != null ? resident.getName() : null,
                    assignment.getDrawId(),
                    draw != null ? draw.getName() : null,
                    assignment.getValidFrom(),
                    assignment.getValidUntil(),
                    assignment.getStatus().name()
                );
            })
            .toList();
    }

    private Map<Long, Unit> loadUnitsById(String tenant, List<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Unit> result = new HashMap<>();
        unitRepo.findByTenantIdAndIdIn(tenant, unitIds).forEach(unit -> result.put(unit.getId(), unit));
        return result;
    }

    private Map<Long, Resident> loadResidentsByUnitId(String tenant, List<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Resident> result = new HashMap<>();
        residentRepo.findByTenantIdAndUnitIdIn(tenant, unitIds).forEach(resident ->
            result.putIfAbsent(resident.getUnitId(), resident)
        );
        return result;
    }

    private Map<Long, ParkingSpot> loadSpotsById(String tenant, List<Long> spotIds) {
        if (spotIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ParkingSpot> result = new HashMap<>();
        spotRepo.findAllById(spotIds).stream()
            .filter(spot -> tenant.equals(spot.getTenantId()))
            .forEach(spot -> result.put(spot.getId(), spot));
        return result;
    }

    private Map<Long, ParkingDraw> loadDrawsById(String tenant, List<Long> drawIds) {
        if (drawIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ParkingDraw> result = new HashMap<>();
        drawRepo.findAllById(drawIds).stream()
            .filter(draw -> tenant.equals(draw.getTenantId()))
            .forEach(draw -> result.put(draw.getId(), draw));
        return result;
    }

    private Set<Long> loadUnitsWithActiveAssignment(String tenant, Long condominiumId) {
        LocalDate today = LocalDate.now();
        return assignRepo.findByTenantIdAndCondominiumIdAndStatus(tenant, condominiumId, ParkingSpotAssignment.Status.ACTIVE).stream()
            .filter(assignment -> !assignment.getValidUntil().isBefore(today))
            .map(ParkingSpotAssignment::getUnitId)
            .collect(java.util.stream.Collectors.toSet());
    }

    private String buildUnitLabel(Unit unit, Long fallbackUnitId) {
        if (unit == null) {
            return "Unidade #" + fallbackUnitId;
        }
        return unit.getBlock() != null && !unit.getBlock().isBlank()
            ? "Unidade " + unit.getNumber() + " • Bloco " + unit.getBlock()
            : "Unidade " + unit.getNumber();
    }

    private String resolveResidentName(ParkingDrawRegistration registration, Map<Long, Resident> residentsByUnitId) {
        Resident resident = residentsByUnitId.get(registration.getUnitId());
        if (resident == null) {
            return null;
        }
        if (registration.getResidentId() == null || Objects.equals(registration.getResidentId(), resident.getId())) {
            return resident.getName();
        }
        return resident.getName();
    }

    private ParkingSpot copySpot(ParkingSpot source) {
        ParkingSpot copy = new ParkingSpot();
        copy.setId(source.getId());
        copy.setTenantId(source.getTenantId());
        copy.setCondominiumId(source.getCondominiumId());
        copy.setCode(source.getCode());
        copy.setDescription(source.getDescription());
        copy.setActive(source.isActive());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private ParkingDraw copyDraw(ParkingDraw source) {
        ParkingDraw copy = new ParkingDraw();
        copy.setId(source.getId());
        copy.setTenantId(source.getTenantId());
        copy.setCondominiumId(source.getCondominiumId());
        copy.setName(source.getName());
        copy.setRegistrationOpenAt(source.getRegistrationOpenAt());
        copy.setRegistrationCloseAt(source.getRegistrationCloseAt());
        copy.setValidFrom(source.getValidFrom());
        copy.setValidUntil(source.getValidUntil());
        copy.setStatus(source.getStatus());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setExecutedAt(source.getExecutedAt());
        copy.setExecutedBy(source.getExecutedBy());
        return copy;
    }
}
