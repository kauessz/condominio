package com.example.condo.service;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.unit.CreateUnitRequest;
import com.example.condo.dto.unit.UpdateUnitRequest;
import com.example.condo.dto.unit.UnitResponse;
import com.example.condo.entity.Unit;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.UnitRepository.UnitCountView;
import com.example.condo.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service para operações de unidades.
 *
 * Responsabilidades:
 * - Validação de duplicidade (número + bloco por condomínio)
 * - Geração automática de código
 * - Conversão de DTOs
 * - Isolamento multi-tenant
 */
@Service
@Transactional(readOnly = true)
public class UnitService {

    private final UnitRepository unitRepo;
    private final CondominiumRepository condominiumRepo;

    public UnitService(UnitRepository unitRepo, CondominiumRepository condominiumRepo) {
        this.unitRepo = unitRepo;
        this.condominiumRepo = condominiumRepo;
    }

    /**
     * Lista unidades com paginação e busca.
     */
    public Page<UnitResponse> search(Long condominiumId, String query, Pageable pageable) {
        String tenantId = TenantContext.get();

        // Valida que o condomínio existe e pertence ao tenant
        validateCondominiumExists(tenantId, condominiumId);

        Page<UnitCountView> page = unitRepo.searchWithCount(tenantId, condominiumId, query, pageable);

        return page.map(view -> new UnitResponse(
            view.getId(),
            condominiumId,
            null, // condominiumName não disponível nesta query
            buildCode(view.getNumber(), view.getBlock()),
            view.getNumber(),
            view.getBlock(),
            null // createdAt não disponível nesta projection
        ));
    }

    /**
     * Busca unidade por ID.
     */
    public UnitResponse getById(Long id) {
        String tenantId = TenantContext.get();

        Unit unit = unitRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", id));

        return UnitResponse.from(unit);
    }

    /**
     * Cria uma nova unidade.
     *
     * Regras:
     * - Condomínio deve existir e pertencer ao tenant
     * - Não pode duplicar número + bloco no mesmo condomínio
     * - Gera código automaticamente
     */
    @Transactional
    public UnitResponse create(CreateUnitRequest request) {
        String tenantId = TenantContext.get();

        // Valida que o condomínio existe e pertence ao tenant
        validateCondominiumExists(tenantId, request.condominiumId());

        // Normaliza campos
        String number = normalize(request.number());
        String block = normalize(request.block());

        // Valida duplicidade
        validateNotDuplicate(tenantId, request.condominiumId(), number, block, null);

        // Cria unidade
        Unit unit = new Unit();
        unit.setTenantId(tenantId);
        unit.setCondominiumId(request.condominiumId());
        unit.setNumber(number);
        unit.setBlock(block.isEmpty() ? null : block);
        unit.setCode(buildCode(number, block));

        unit = unitRepo.save(unit);

        return UnitResponse.from(unit);
    }

    /**
     * Atualiza uma unidade existente.
     */
    @Transactional
    public UnitResponse update(Long id, UpdateUnitRequest request) {
        String tenantId = TenantContext.get();

        Unit unit = unitRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", id));

        // Atualização parcial
        String number = request.number() != null ? normalize(request.number()) : unit.getNumber();
        String block = request.block() != null ? normalize(request.block()) : (unit.getBlock() != null ? unit.getBlock() : "");

        // Valida duplicidade (excluindo a própria unidade)
        validateNotDuplicate(tenantId, unit.getCondominiumId(), number, block, id);

        // Atualiza
        unit.setNumber(number);
        unit.setBlock(block.isEmpty() ? null : block);
        unit.setCode(buildCode(number, block));

        unit = unitRepo.save(unit);

        return UnitResponse.from(unit);
    }

    /**
     * Deleta uma unidade.
     *
     * Nota: atualmente sem validação de vínculos (ex: moradores).
     * Pode adicionar validação no futuro se necessário.
     */
    @Transactional
    public void delete(Long id) {
        String tenantId = TenantContext.get();

        Unit unit = unitRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", id));

        unitRepo.delete(unit);
    }

    // ========== Métodos auxiliares ==========

    /**
     * Normaliza string (trim + lowercase para comparações).
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Gera código da unidade no formato: BLOCO-NUMERO ou apenas NUMERO.
     */
    private String buildCode(String number, String block) {
        String normalizedNumber = normalize(number);
        String normalizedBlock = normalize(block);

        if (normalizedBlock.isEmpty()) {
            return normalizedNumber;
        }

        return normalizedBlock.replaceAll("\\s+", "").toUpperCase() + "-" + normalizedNumber;
    }

    /**
     * Valida que condomínio existe e pertence ao tenant.
     */
    private void validateCondominiumExists(String tenantId, Long condominiumId) {
        condominiumRepo.findByTenantIdAndId(tenantId, condominiumId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condominiumId));
    }

    /**
     * Valida que não existe duplicidade de número + bloco no condomínio.
     */
    private void validateNotDuplicate(
        String tenantId,
        Long condominiumId,
        String number,
        String block,
        Long excludeId
    ) {
        String blockLowerOrNull = block.isEmpty() ? null : block.toLowerCase();

        boolean isDuplicate = unitRepo.existsDuplicate(
            tenantId,
            condominiumId,
            number,
            blockLowerOrNull,
            excludeId
        );

        if (isDuplicate) {
            throw new BusinessException(
                "Já existe uma unidade com este número e bloco neste condomínio"
            );
        }
    }
}
