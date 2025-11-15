package com.example.condo.service;

import com.example.condo.dto.resident.CreateResidentRequest;
import com.example.condo.dto.resident.ResidentResponse;
import com.example.condo.dto.resident.UpdateResidentRequest;
import com.example.condo.entity.Resident;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service para operações de moradores.
 *
 * Responsabilidades:
 * - Validação de vínculos (condomínio e unidade)
 * - Conversão de DTOs
 * - Isolamento multi-tenant
 */
@Service
@Transactional(readOnly = true)
public class ResidentService {

    private final ResidentRepository residentRepo;
    private final CondominiumRepository condominiumRepo;
    private final UnitRepository unitRepo;

    public ResidentService(
        ResidentRepository residentRepo,
        CondominiumRepository condominiumRepo,
        UnitRepository unitRepo
    ) {
        this.residentRepo = residentRepo;
        this.condominiumRepo = condominiumRepo;
        this.unitRepo = unitRepo;
    }

    /**
     * Lista moradores com paginação e busca.
     */
    public Page<ResidentResponse> search(Long condominiumId, String query, Pageable pageable) {
        String tenantId = TenantContext.get();

        Page<Object[]> page = residentRepo.searchWithUnit(tenantId, condominiumId, query, pageable);

        return page.map(row -> {
            Resident resident = (Resident) row[0];
            String unitCode = (String) row[1];
            String unitNumber = (String) row[2];
            String unitBlock = (String) row[3];

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

        return ResidentResponse.from(resident);
    }

    /**
     * Cria um novo morador.
     *
     * Validações:
     * - Condomínio deve existir e pertencer ao tenant
     * - Unidade deve existir e pertencer ao tenant e condomínio
     */
    @Transactional
    public ResidentResponse create(CreateResidentRequest request) {
        String tenantId = TenantContext.get();

        // Valida condomínio
        validateCondominiumExists(tenantId, request.condominiumId());

        // Valida unidade
        validateUnitExists(tenantId, request.condominiumId(), request.unitId());

        // Cria morador
        Resident resident = new Resident();
        resident.setTenantId(tenantId);
        resident.setCondominiumId(request.condominiumId());
        resident.setUnitId(request.unitId());
        resident.setName(request.name().trim());
        resident.setEmail(request.email() != null ? request.email().trim() : null);
        resident.setPhone(request.phone() != null ? request.phone().trim() : null);

        resident = residentRepo.save(resident);

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

        // Se mudar de unidade, valida
        if (request.unitId() != null && !request.unitId().equals(resident.getUnitId())) {
            validateUnitExists(tenantId, resident.getCondominiumId(), request.unitId());
            resident.setUnitId(request.unitId());
        }

        // Atualização parcial
        if (request.name() != null) {
            resident.setName(request.name().trim());
        }
        if (request.email() != null) {
            resident.setEmail(request.email().trim());
        }
        if (request.phone() != null) {
            resident.setPhone(request.phone().trim());
        }

        resident = residentRepo.save(resident);

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

        residentRepo.delete(resident);
    }

    // ========== Métodos auxiliares ==========

    private void validateCondominiumExists(String tenantId, Long condominiumId) {
        condominiumRepo.findByTenantIdAndId(tenantId, condominiumId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condominiumId));
    }

    private void validateUnitExists(String tenantId, Long condominiumId, Long unitId) {
        var unit = unitRepo.findByTenantIdAndId(tenantId, unitId)
            .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", unitId));

        // Valida que a unidade pertence ao condomínio
        if (!unit.getCondominiumId().equals(condominiumId)) {
            throw new ResourceNotFoundException("Unidade", "id no condomínio especificado", unitId);
        }
    }
}
