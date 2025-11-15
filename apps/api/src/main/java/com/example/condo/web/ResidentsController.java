package com.example.condo.web;

import com.example.condo.dto.resident.CreateResidentRequest;
import com.example.condo.dto.resident.ResidentResponse;
import com.example.condo.dto.resident.UpdateResidentRequest;
import com.example.condo.service.ResidentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de moradores.
 *
 * Endpoints:
 * - GET    /residents          -> Lista com paginação e busca
 * - GET    /residents/{id}     -> Detalhes de um morador
 * - POST   /residents          -> Criar morador (ADMIN only)
 * - PUT    /residents/{id}     -> Atualizar morador (ADMIN only)
 * - DELETE /residents/{id}     -> Deletar morador (ADMIN only)
 */
@RestController
@RequestMapping({"/residents", "/api/residents"})
public class ResidentsController {

    private final ResidentService residentService;

    public ResidentsController(ResidentService residentService) {
        this.residentService = residentService;
    }

    /**
     * GET /residents
     *
     * Lista moradores com paginação e busca.
     *
     * Query params:
     * - condoId: ID do condomínio (obrigatório)
     * - q: termo de busca (opcional - busca em name, email, phone)
     * - page, size, sort: parâmetros de paginação Spring Data
     *
     * Resposta: Page<ResidentResponse> (com dados da unidade)
     */
    @GetMapping
    public Page<ResidentResponse> list(
        @RequestParam("condoId") Long condominiumId,
        @RequestParam(value = "q", required = false) String query,
        Pageable pageable
    ) {
        return residentService.search(condominiumId, query, pageable);
    }

    /**
     * GET /residents/{id}
     *
     * Busca detalhes de um morador.
     *
     * Erros:
     * - 404: Morador não encontrado
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResidentResponse> getById(@PathVariable Long id) {
        ResidentResponse response = residentService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /residents
     *
     * Cria um novo morador.
     *
     * Requer role: ADMIN
     *
     * Body:
     * {
     *   "condominiumId": 1,
     *   "unitId": 10,
     *   "name": "João Silva",
     *   "email": "joao@exemplo.com",
     *   "phone": "(11) 98765-4321"
     * }
     *
     * Validações:
     * - condominiumId: obrigatório
     * - unitId: obrigatório
     * - name: obrigatório, 3-200 caracteres
     * - email: formato válido, max 200 caracteres
     * - phone: opcional, max 20 caracteres
     *
     * Resposta (201): ResidentResponse
     *
     * Erros:
     * - 404: Condomínio ou unidade não encontrados
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResidentResponse> create(
        @Valid @RequestBody CreateResidentRequest request
    ) {
        ResidentResponse response = residentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /residents/{id}
     *
     * Atualiza um morador existente (atualização parcial).
     *
     * Requer role: ADMIN
     *
     * Body (todos os campos opcionais):
     * {
     *   "unitId": 20,
     *   "name": "João Pedro Silva",
     *   "email": "joaopedro@exemplo.com",
     *   "phone": "(11) 99999-8888"
     * }
     *
     * Erros:
     * - 404: Morador ou nova unidade não encontrados
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResidentResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateResidentRequest request
    ) {
        ResidentResponse response = residentService.update(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /residents/{id}
     *
     * Deleta um morador.
     *
     * Requer role: ADMIN
     *
     * Resposta (204): No Content
     *
     * Erros:
     * - 404: Morador não encontrado
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        residentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
