package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.condominium.CondominiumResponse;
import com.example.condo.dto.condominium.CreateCondominiumRequest;
import com.example.condo.dto.condominium.UpdateCondominiumRequest;
import com.example.condo.service.CondominiumService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de condomínios.
 *
 * Endpoints:
 * - GET    /condominiums           -> Lista com paginação
 * - GET    /condominiums/{id}      -> Detalhes de um condomínio
 * - POST   /condominiums           -> Criar condomínio (ADMIN only)
 * - PUT    /condominiums/{id}      -> Atualizar condomínio (ADMIN only)
 * - DELETE /condominiums/{id}      -> Deletar condomínio (ADMIN only)
 */
@RestController
@RequestMapping({"/condominiums", "/api/condominiums"})
public class CondominiumController {

    private final CondominiumService condominiumService;

    public CondominiumController(CondominiumService condominiumService) {
        this.condominiumService = condominiumService;
    }

    /**
     * GET /condominiums
     *
     * Lista condomínios com paginação e contadores.
     *
     * Query params:
     * - page: número da página (default: 0)
     * - pageSize: tamanho da página (default: 20)
     *
     * Resposta:
     * {
     *   "content": [...],
     *   "page": 0,
     *   "size": 20,
     *   "totalElements": 50,
     *   "totalPages": 3,
     *   "first": true,
     *   "last": false
     * }
     */
    @GetMapping
    public ResponseEntity<PageResponse<CondominiumResponse>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        PageResponse<CondominiumResponse> response = condominiumService.listWithCounts(page, pageSize);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /condominiums/{id}
     *
     * Busca detalhes de um condomínio com contadores.
     *
     * Resposta:
     * {
     *   "id": 1,
     *   "name": "Edifício Solar",
     *   "cnpj": "12.345.678/0001-90",
     *   "createdAt": "2024-01-15T10:30:00Z",
     *   "unitCount": 50,
     *   "residentCount": 120
     * }
     *
     * Erros:
     * - 404: Condomínio não encontrado
     */
    @GetMapping("/{id}")
    public ResponseEntity<CondominiumResponse> getById(@PathVariable Long id) {
        CondominiumResponse response = condominiumService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /condominiums
     *
     * Cria um novo condomínio.
     *
     * Requer role: ADMIN
     *
     * Body:
     * {
     *   "name": "Edifício Solar",
     *   "cnpj": "12.345.678/0001-90"
     * }
     *
     * Validações:
     * - name: obrigatório, 3-200 caracteres
     * - cnpj: formato XX.XXX.XXX/XXXX-XX (opcional)
     *
     * Resposta (200):
     * {
     *   "id": 1,
     *   "name": "Edifício Solar",
     *   "cnpj": "12.345.678/0001-90",
     *   "createdAt": "2024-01-15T10:30:00Z"
     * }
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CondominiumResponse> create(
        @Valid @RequestBody CreateCondominiumRequest request
    ) {
        CondominiumResponse response = condominiumService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /condominiums/{id}
     *
     * Atualiza um condomínio existente (atualização parcial).
     *
     * Requer role: ADMIN
     *
     * Body (todos os campos opcionais):
     * {
     *   "name": "Novo Nome",
     *   "cnpj": "98.765.432/0001-10"
     * }
     *
     * Erros:
     * - 404: Condomínio não encontrado
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CondominiumResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateCondominiumRequest request
    ) {
        CondominiumResponse response = condominiumService.update(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /condominiums/{id}
     *
     * Deleta um condomínio.
     *
     * Requer role: ADMIN
     *
     * Regra de negócio: não permite deletar se houver unidades ou moradores vinculados.
     *
     * Resposta (204): No Content
     *
     * Erros:
     * - 404: Condomínio não encontrado
     * - 422: Condomínio tem vínculos (unidades/moradores)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        condominiumService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
