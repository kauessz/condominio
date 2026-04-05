package com.example.condo.service;

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
    private final AuditService auditService;

    public ParkingService(CondominiumRepository condominiumRepo,
                           ParkingSpotRepository spotRepo,
                           ParkingDrawRepository drawRepo,
                           ParkingDrawRegistrationRepository regRepo,
                           ParkingSpotAssignmentRepository assignRepo,
                           AuditService auditService) {
        this.condominiumRepo = condominiumRepo;
        this.spotRepo = spotRepo;
        this.drawRepo = drawRepo;
        this.regRepo = regRepo;
        this.assignRepo = assignRepo;
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

    public List<ParkingDrawRegistration> getRegistrations(Long drawId) {
        getDraw(drawId);
        return regRepo.findByDrawId(drawId);
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

        List<ParkingSpot> availableSpots = spotRepo.findAllActive(tenant, condoId);
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
        List<ParkingDrawRegistration> shuffled = new ArrayList<>(registrations);
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

    public List<ParkingSpotAssignment> getAllAssignments(Long condoIdParam) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (UserContext.isSuperuser() && condoId == null) {
            return assignRepo.findAllActiveByTenant(tenant);
        }
        if (condoId == null) return List.of();
        return assignRepo.findAllActiveForCondo(tenant, condoId);
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
