package com.example.condo.web;

import com.example.condo.dto.unit.CreateUnitRequest;
import com.example.condo.dto.unit.UpdateUnitRequest;
import com.example.condo.dto.unit.UnitResponse;
import com.example.condo.service.UnitService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de unidades (apartamentos/casas).
 *
 * Endpoints:
 * - GET    /units              -> Lista com paginação e busca
 * - GET    /units/{id}         -> Detalhes de uma unidade
 * - POST   /units              -> Criar unidade (ADMIN only)
 * - PUT    /units/{id}         -> Atualizar unidade (ADMIN only)
 * - DELETE /units/{id}         -> Deletar unidade (ADMIN only)
 */
@RestController
@RequestMapping({"/units", "/api/units"})
public class UnitsController {

    private final UnitService unitService;

    public UnitsController(UnitService unitService) {
        this.unitService = unitService;
    }

    /**
     * GET /units
     *
     * Lista unidades com paginação, busca e contador de moradores.
     *
     * Query params:
     * - condoId: ID do condomínio (obrigatório)
     * - q: termo de busca (opcional - busca em number, block, code)
     * - page, size, sort: parâmetros de paginação Spring Data
     *
     * Resposta: Page<UnitResponse>
     */
    @GetMapping
    public Page<UnitResponse> list(
        @RequestParam("condoId") Long condominiumId,
        @RequestParam(value = "q", required = false) String query,
        Pageable pageable
    ) {
        return unitService.search(condominiumId, query, pageable);
    }

    /**
     * GET /units/{id}
     *
     * Busca detalhes de uma unidade.
     *
     * Erros:
     * - 404: Unidade não encontrada
     */
    @GetMapping("/{id}")
    public ResponseEntity<UnitResponse> getById(@PathVariable Long id) {
        UnitResponse response = unitService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /units
     *
     * Cria uma nova unidade.
     *
     * Requer role: ADMIN
     *
     * Body:
     * {
     *   "condominiumId": 1,
     *   "number": "101",
     *   "block": "A",
     *   "code": "A-101" (opcional, gerado automaticamente)
     * }
     *
     * Validações:
     * - condominiumId: obrigatório
     * - number: obrigatório, max 20 caracteres
     * - block: opcional, max 20 caracteres
     * - Não pode duplicar number + block no mesmo condomínio
     *
     * Resposta (201): UnitResponse
     *
     * Erros:
     * - 404: Condomínio não encontrado
     * - 422: Unidade duplicada
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UnitResponse> create(
        @Valid @RequestBody CreateUnitRequest request
    ) {
        UnitResponse response = unitService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /units/{id}
     *
     * Atualiza uma unidade existente (atualização parcial).
     *
     * Requer role: ADMIN
     *
     * Body (todos os campos opcionais):
     * {
     *   "number": "102",
     *   "block": "B"
     * }
     *
     * Erros:
     * - 404: Unidade não encontrada
     * - 422: Número/bloco duplicado
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UnitResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUnitRequest request
    ) {
        UnitResponse response = unitService.update(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /units/{id}
     *
     * Deleta uma unidade.
     *
     * Requer role: ADMIN
     *
     * Resposta (204): No Content
     *
     * Erros:
     * - 404: Unidade não encontrada
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        unitService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
