package com.example.condo.service;

import com.example.condo.dto.resident.CreateResidentRequest;
import com.example.condo.dto.resident.ResidentResponse;
import com.example.condo.dto.resident.UpdateResidentRequest;
import com.example.condo.entity.Resident;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service para operações de moradores.
 *
 * Isolamento de tenant:
 * - SUPERUSER: usa o condominiumId fornecido na requisição.
 * - Outros roles: ignora o condominiumId da requisição e usa o do JWT (UserContext).
 */
@Service
@Transactional(readOnly = true)
public class ResidentService {

    private final ResidentRepository residentRepo;
    private final CondominiumRepository condominiumRepo;
    private final UnitRepository unitRepo;
    private final AuditService auditService;

    public ResidentService(
        ResidentRepository residentRepo,
        CondominiumRepository condominiumRepo,
        UnitRepository unitRepo,
        AuditService auditService
    ) {
        this.residentRepo = residentRepo;
        this.condominiumRepo = condominiumRepo;
        this.unitRepo = unitRepo;
        this.auditService = auditService;
    }

    /**
     * Lista moradores com paginação e busca.
     *
     * @param condominiumIdParam condominiumId vindo do request (usado apenas para SUPERUSER)
     */
    public Page<ResidentResponse> search(Long condominiumIdParam, String query, Pageable pageable) {
        String tenantId = TenantContext.get();
        Long condominiumId = UserContext.resolveCondominiumId(condominiumIdParam);
        Long scopedUnitId = isMorador() ? UserContext.unitId() : null;

        if (condominiumId == null) {
            return Page.empty(pageable);
        }

        Page<Object[]> page = residentRepo.searchWithUnit(tenantId, condominiumId, scopedUnitId, query, pageable);

        return page.map(row -> {
            Resident resident = (Resident) row[0];
            String unitCode = row.length > 1 ? (String) row[1] : null;
            String unitNumber = row.length > 2 ? (String) row[2] : null;
            String unitBlock = row.length > 3 ? (String) row[3] : null;

            return ResidentResponse.withUnit(resident, unitCode, unitNumber, unitBlock);
        });
    }

    /**
     * Busca morador por ID.
     */
    public ResidentResponse getById(Long id) {
        String tenantId = TenantContext.get();

        Resident resident = residentRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Morador", "id", id));

        // Para não-SUPERUSER, valida que o morador pertence ao condomínio do usuário
        Long effectiveCondoId = UserContext.resolveCondominiumId(resident.getCondominiumId());
        if (effectiveCondoId != null && !effectiveCondoId.equals(resident.getCondominiumId())) {
            throw new ResourceNotFoundException("Morador", "id", id);
        }
        enforceResidentUnitScope(resident);

        // Busca dados da unidade para retornar unitDisplay
        if (resident.getUnitId() != null) {
            return unitRepo.findByTenantIdAndId(tenantId, resident.getUnitId())
                .map(unit -> ResidentResponse.withUnit(
                    resident, unit.getCode(), unit.getNumber(), unit.getBlock()
                ))
                .orElseGet(() -> ResidentResponse.from(resident));
        }

        return ResidentResponse.from(resident);
    }

    /**
     * Cria um novo morador.
     */
    @Transactional
    public ResidentResponse create(CreateResidentRequest request) {
        String tenantId = TenantContext.get();

        // Para não-SUPERUSER, usa condominiumId do JWT e ignora o do request
        Long condominiumId = UserContext.resolveCondominiumId(request.condominiumId());
        if (condominiumId == null) {
            throw new BusinessException("Usuário sem condomínio configurado. Contate o administrador.");
        }

        validateCondominiumExists(tenantId, condominiumId);
        Long unitId = request.unitId();
        if (isMorador()) {
            Long currentUnitId = UserContext.unitId();
            if (currentUnitId == null) {
                throw new BusinessException("Morador autenticado sem unidade vinculada.");
            }
            unitId = currentUnitId;
        }
        validateUnitExists(tenantId, condominiumId, unitId);

        Resident resident = new Resident();
        resident.setTenantId(tenantId);
        resident.setCondominiumId(condominiumId);
        resident.setUnitId(unitId);
        resident.setName(request.name().trim());
        resident.setEmail(request.email() != null ? request.email().trim() : null);
        resident.setPhone(request.phone() != null ? request.phone().trim() : null);

        resident = residentRepo.save(resident);
        auditService.log("CREATE", "Resident", resident.getId(), resident.getCondominiumId(), null, ResidentResponse.from(resident));

        return ResidentResponse.from(resident);
    }

    /**
     * Atualiza um morador existente.
     */
    @Transactional
    public ResidentResponse update(Long id, UpdateResidentRequest request) {
        String tenantId = TenantContext.get();

        Resident resident = residentRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Morador", "id", id));
        ResidentResponse before = ResidentResponse.from(resident);

        // Para não-SUPERUSER, garante que o morador pertence ao condomínio do usuário
        Long effectiveCondoId = UserContext.resolveCondominiumId(resident.getCondominiumId());
        if (effectiveCondoId != null && !effectiveCondoId.equals(resident.getCondominiumId())) {
            throw new ResourceNotFoundException("Morador", "id", id);
        }
        enforceResidentUnitScope(resident);

        if (isMorador() && request.unitId() != null && !request.unitId().equals(resident.getUnitId())) {
            throw new BusinessException("Morador não pode alterar a unidade de outro morador");
        }

        if (request.unitId() != null && !request.unitId().equals(resident.getUnitId())) {
            validateUnitExists(tenantId, resident.getCondominiumId(), request.unitId());
            resident.setUnitId(request.unitId());
        }

        if (request.name() != null) resident.setName(request.name().trim());
        if (request.email() != null) resident.setEmail(request.email().trim());
        if (request.phone() != null) resident.setPhone(request.phone().trim());

        resident = residentRepo.save(resident);
        auditService.log("UPDATE", "Resident", resident.getId(), resident.getCondominiumId(), before, ResidentResponse.from(resident));

        return ResidentResponse.from(resident);
    }

    /**
     * Deleta um morador.
     */
    @Transactional
    public void delete(Long id) {
        String tenantId = TenantContext.get();

        Resident resident = residentRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Morador", "id", id));

        Long effectiveCondoId = UserContext.resolveCondominiumId(resident.getCondominiumId());
        if (effectiveCondoId != null && !effectiveCondoId.equals(resident.getCondominiumId())) {
            throw new ResourceNotFoundException("Morador", "id", id);
        }

        ResidentResponse before = ResidentResponse.from(resident);
        residentRepo.delete(resident);
        auditService.log("DELETE", "Resident", id, resident.getCondominiumId(), before, null);
    }

    /**
     * Aggregates residents by unit for dashboard counters.
     *
     * @param condominiumIdParam condominiumId vindo do request (usado apenas para SUPERUSER)
     */
    public Map<Long, Long> countByUnit(Long condominiumIdParam) {
        String tenantId = TenantContext.get();
        Long condominiumId = UserContext.resolveCondominiumId(condominiumIdParam);

        if (condominiumId == null) {
            return Map.of();
        }

        Long scopedUnitId = isMorador() ? UserContext.unitId() : null;
        List<Object[]> rows = residentRepo.countByUnit(tenantId, condominiumId, scopedUnitId);
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            Long unitId = (Long) row[0];
            Long count = (Long) row[1];
            result.put(unitId, count);
        }
        return result;
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

    private void enforceResidentUnitScope(Resident resident) {
        if (!isMorador()) {
            return;
        }
        Long currentUnitId = UserContext.unitId();
        if (currentUnitId == null || !currentUnitId.equals(resident.getUnitId())) {
            throw new ResourceNotFoundException("Morador", "id", resident.getId());
        }
    }

    private boolean isMorador() {
        UserContext.Data ctx = UserContext.get();
        return ctx != null && "MORADOR".equalsIgnoreCase(ctx.role());
    }
}
