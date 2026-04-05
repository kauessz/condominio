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
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service para operações de unidades.
 *
 * Isolamento de tenant:
 * - SUPERUSER: usa o condominiumId fornecido na requisição.
 * - Outros roles: ignora o condominiumId da requisição e usa o do JWT (UserContext).
 */
@Service
@Transactional(readOnly = true)
public class UnitService {

    private final UnitRepository unitRepo;
    private final CondominiumRepository condominiumRepo;
    private final AuditService auditService;

    public UnitService(UnitRepository unitRepo, CondominiumRepository condominiumRepo, AuditService auditService) {
        this.unitRepo = unitRepo;
        this.condominiumRepo = condominiumRepo;
        this.auditService = auditService;
    }

    /**
     * Lista unidades com paginação e busca.
     *
     * @param condominiumIdParam condominiumId vindo do request (usado apenas para SUPERUSER)
     */
    public Page<UnitResponse> search(Long condominiumIdParam, String query, Pageable pageable) {
        String tenantId = TenantContext.get();
        Long condominiumId = UserContext.resolveCondominiumId(condominiumIdParam);

        if (condominiumId == null) {
            return Page.empty(pageable);
        }

        validateCondominiumExists(tenantId, condominiumId);

        if (isMorador()) {
            Long unitId = UserContext.unitId();
            if (unitId == null) {
                return Page.empty(pageable);
            }
            Unit unit = unitRepo.findByTenantIdAndId(tenantId, unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", unitId));
            if (!condominiumId.equals(unit.getCondominiumId())) {
                return Page.empty(pageable);
            }
            boolean matchesQuery = query == null
                || query.isBlank()
                || unit.getNumber().toLowerCase().contains(query.toLowerCase())
                || (unit.getBlock() != null && unit.getBlock().toLowerCase().contains(query.toLowerCase()))
                || (unit.getCode() != null && unit.getCode().toLowerCase().contains(query.toLowerCase()));
            if (!matchesQuery) {
                return Page.empty(pageable);
            }
            UnitResponse response = UnitResponse.from(unit);
            return new PageImpl<>(List.of(response), pageable, 1);
        }

        Page<UnitCountView> page = unitRepo.searchWithCount(tenantId, condominiumId, query, pageable);

        final Long finalCondoId = condominiumId;
        return page.map(view -> new UnitResponse(
            view.getId(),
            finalCondoId,
            null,
            buildCode(view.getNumber(), view.getBlock()),
            view.getNumber(),
            view.getBlock(),
            null
        ));
    }

    /**
     * Busca unidade por ID.
     */
    public UnitResponse getById(Long id) {
        String tenantId = TenantContext.get();

        Unit unit = unitRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", id));

        // Para não-SUPERUSER, valida que a unidade pertence ao condomínio do usuário
        Long condominiumId = UserContext.resolveCondominiumId(unit.getCondominiumId());
        if (condominiumId != null && !condominiumId.equals(unit.getCondominiumId())) {
            throw new ResourceNotFoundException("Unidade", "id", id);
        }
        enforceMoradorOwnUnit(unit);

        return UnitResponse.from(unit);
    }

    /**
     * Cria uma nova unidade.
     */
    @Transactional
    public UnitResponse create(CreateUnitRequest request) {
        String tenantId = TenantContext.get();

        // Para não-SUPERUSER, usa condominiumId do JWT e ignora o do request
        Long condominiumId = UserContext.resolveCondominiumId(request.condominiumId());
        if (condominiumId == null) {
            throw new BusinessException("Usuário sem condomínio configurado. Contate o administrador.");
        }

        validateCondominiumExists(tenantId, condominiumId);

        String number = normalize(request.number());
        String block = normalize(request.block());

        validateNotDuplicate(tenantId, condominiumId, number, block, null);

        Unit unit = new Unit();
        unit.setTenantId(tenantId);
        unit.setCondominiumId(condominiumId);
        unit.setNumber(number);
        unit.setBlock(block.isEmpty() ? null : block);
        unit.setCode(buildCode(number, block));

        unit = unitRepo.save(unit);
        auditService.log("CREATE", "Unit", unit.getId(), unit.getCondominiumId(), null, UnitResponse.from(unit));

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
        UnitResponse before = UnitResponse.from(unit);

        // Para não-SUPERUSER, garante que a unidade pertence ao condomínio do usuário
        Long effectiveCondoId = UserContext.resolveCondominiumId(unit.getCondominiumId());
        if (effectiveCondoId != null && !effectiveCondoId.equals(unit.getCondominiumId())) {
            throw new ResourceNotFoundException("Unidade", "id", id);
        }

        String number = request.number() != null ? normalize(request.number()) : unit.getNumber();
        String block = request.block() != null ? normalize(request.block()) : (unit.getBlock() != null ? unit.getBlock() : "");

        validateNotDuplicate(tenantId, unit.getCondominiumId(), number, block, id);

        unit.setNumber(number);
        unit.setBlock(block.isEmpty() ? null : block);
        unit.setCode(buildCode(number, block));

        unit = unitRepo.save(unit);
        auditService.log("UPDATE", "Unit", unit.getId(), unit.getCondominiumId(), before, UnitResponse.from(unit));

        return UnitResponse.from(unit);
    }

    /**
     * Deleta uma unidade.
     */
    @Transactional
    public void delete(Long id) {
        String tenantId = TenantContext.get();

        Unit unit = unitRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", id));

        // Para não-SUPERUSER, garante que a unidade pertence ao condomínio do usuário
        Long effectiveCondoId = UserContext.resolveCondominiumId(unit.getCondominiumId());
        if (effectiveCondoId != null && !effectiveCondoId.equals(unit.getCondominiumId())) {
            throw new ResourceNotFoundException("Unidade", "id", id);
        }

        UnitResponse before = UnitResponse.from(unit);
        unitRepo.delete(unit);
        auditService.log("DELETE", "Unit", id, unit.getCondominiumId(), before, null);
    }

    // ========== Métodos auxiliares ==========

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildCode(String number, String block) {
        String normalizedNumber = normalize(number);
        String normalizedBlock = normalize(block);

        if (normalizedBlock.isEmpty()) {
            return normalizedNumber;
        }

        return normalizedBlock.replaceAll("\\s+", "").toUpperCase() + "-" + normalizedNumber;
    }

    private void validateCondominiumExists(String tenantId, Long condominiumId) {
        condominiumRepo.findByTenantIdAndId(tenantId, condominiumId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condominiumId));
    }

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

    private void enforceMoradorOwnUnit(Unit unit) {
        if (!isMorador()) {
            return;
        }
        Long currentUnitId = UserContext.unitId();
        if (currentUnitId == null || !currentUnitId.equals(unit.getId())) {
            throw new ResourceNotFoundException("Unidade", "id", unit.getId());
        }
    }

    private boolean isMorador() {
        UserContext.Data ctx = UserContext.get();
        return ctx != null && "MORADOR".equalsIgnoreCase(ctx.role());
    }
}
