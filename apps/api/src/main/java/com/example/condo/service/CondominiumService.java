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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Service para operações de condomínio.
 *
 * Responsabilidades:
 * - Validações de negócio
 * - Conversão de DTOs
 * - Isolamento multi-tenant
 * - Controle transacional
 */
@Service
@Transactional(readOnly = true)
public class CondominiumService {

    private final CondominiumRepository condominiumRepo;
    private final UnitRepository unitRepo;
    private final ResidentRepository residentRepo;

    public CondominiumService(
        CondominiumRepository condominiumRepo,
        UnitRepository unitRepo,
        ResidentRepository residentRepo
    ) {
        this.condominiumRepo = condominiumRepo;
        this.unitRepo = unitRepo;
        this.residentRepo = residentRepo;
    }

    /**
     * Lista condomínios com paginação e contadores.
     */
    public PageResponse<CondominiumResponse> listWithCounts(int page, int pageSize) {
        String tenantId = TenantContext.get();
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "created_at"));

        Page<Object[]> result = condominiumRepo.pageWithCounts(tenantId, pageable);

        var items = result.getContent().stream()
            .map(row -> {
                Long id = ((Number) row[0]).longValue();
                String name = (String) row[1];
                String cnpj = (String) row[2];
                Instant createdAt = ((Timestamp) row[3]).toInstant();
                long unitCount = ((Number) row[4]).longValue();
                long residentCount = ((Number) row[5]).longValue();

                return new CondominiumResponse(id, name, cnpj, createdAt, unitCount, residentCount);
            })
            .toList();

        return PageResponse.of(items, page, pageSize, result.getTotalElements());
    }

    /**
     * Busca condomínio por ID com contadores.
     */
    public CondominiumResponse getById(Long id) {
        String tenantId = TenantContext.get();

        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", id));

        long unitCount = unitRepo.countByTenantIdAndCondominiumId(tenantId, id);
        long residentCount = residentRepo.countByTenantIdAndCondominiumId(tenantId, id);

        return CondominiumResponse.withCounts(condominium, unitCount, residentCount);
    }

    /**
     * Cria um novo condomínio.
     */
    @Transactional
    public CondominiumResponse create(CreateCondominiumRequest request) {
        String tenantId = TenantContext.get();

        Condominium condominium = new Condominium();
        condominium.setTenantId(tenantId);
        condominium.setName(request.name().trim());
        condominium.setCnpj(request.cnpj() != null ? request.cnpj().trim() : "");

        condominium = condominiumRepo.save(condominium);

        return CondominiumResponse.from(condominium);
    }

    /**
     * Atualiza um condomínio existente.
     */
    @Transactional
    public CondominiumResponse update(Long id, UpdateCondominiumRequest request) {
        String tenantId = TenantContext.get();

        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", id));

        // Atualização parcial: só atualiza campos não-nulos
        if (request.name() != null) {
            condominium.setName(request.name().trim());
        }
        if (request.cnpj() != null) {
            condominium.setCnpj(request.cnpj().trim());
        }

        condominium = condominiumRepo.save(condominium);

        return CondominiumResponse.from(condominium);
    }

    /**
     * Deleta um condomínio.
     *
     * Regra de negócio: não permite deletar se houver unidades ou moradores vinculados.
     */
    @Transactional
    public void delete(Long id) {
        String tenantId = TenantContext.get();

        Condominium condominium = condominiumRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", id));

        // Validação: não pode deletar se tiver vínculos
        long unitCount = unitRepo.countByTenantIdAndCondominiumId(tenantId, id);
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

        condominiumRepo.delete(condominium);
    }
}
