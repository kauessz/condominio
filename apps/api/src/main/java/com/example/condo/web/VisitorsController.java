package com.example.condo.web;

import com.example.condo.dto.visitor.*;
import com.example.condo.entity.User;
import com.example.condo.repo.UserRepository;
import com.example.condo.service.VisitorService;
import com.example.condo.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Controller de visitantes e entregas.
 *
 * Endpoints:
 * - GET    /visitors                    -> Lista com filtros e paginação
 * - GET    /visitors/{id}               -> Detalhes de um visitante
 * - POST   /visitors                    -> Criar visitante (STAFF+ only)
 * - PUT    /visitors/{id}               -> Atualizar visitante (STAFF+ only)
 * - POST   /visitors/{id}/approve       -> Aprovar visitante (STAFF+ only)
 * - POST   /visitors/{id}/reject        -> Rejeitar visitante (STAFF+ only)
 * - POST   /visitors/{id}/checkout      -> Marcar saída (STAFF+ only)
 * - DELETE /visitors/{id}               -> Soft delete (ADMIN only)
 */
@RestController
@RequestMapping({"/visitors", "/api/visitors"})
public class VisitorsController {

    private final VisitorService visitorService;
    private final UserRepository userRepo;

    public VisitorsController(VisitorService visitorService, UserRepository userRepo) {
        this.visitorService = visitorService;
        this.userRepo = userRepo;
    }

    /**
     * GET /visitors
     *
     * Lista visitantes com filtros e paginação.
     *
     * Query params:
     * - condoId: ID do condomínio (obrigatório)
     * - unitId: filtrar por unidade (opcional)
     * - status: PENDING, APPROVED, REJECTED, CHECKED_OUT (opcional)
     * - type: VISITOR, DELIVERY, SERVICE (opcional)
     * - dateFrom: data início (ISO-8601) (opcional)
     * - dateTo: data fim (ISO-8601) (opcional)
     * - page, size, sort: paginação Spring Data
     *
     * Resposta: Page<VisitorResponse> (com dados da unidade)
     */
    @GetMapping
    public Page<VisitorResponse> list(
        @RequestParam("condoId") Long condominiumId,
        @RequestParam(required = false) Long unitId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Instant dateFrom,
        @RequestParam(required = false) Instant dateTo,
        Pageable pageable
    ) {
        return visitorService.search(condominiumId, unitId, status, type, dateFrom, dateTo, pageable);
    }

    /**
     * GET /visitors/{id}
     *
     * Busca detalhes de um visitante.
     *
     * Erros:
     * - 404: Visitante não encontrado
     */
    @GetMapping("/{id}")
    public ResponseEntity<VisitorResponse> getById(@PathVariable Long id) {
        VisitorResponse response = visitorService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /visitors
     *
     * Cria um novo visitante/entrega.
     *
     * Requer role: STAFF, MANAGER, ADMIN
     *
     * Body:
     * {
     *   "condominiumId": 1,
     *   "unitId": 10,
     *   "name": "João Visitante",
     *   "document": "123.456.789-00",
     *   "plate": "ABC-1234",
     *   "phone": "(11) 98765-4321",
     *   "email": "joao@exemplo.com",
     *   "note": "Observações...",
     *   "carrier": "Correios" (para entregas),
     *   "packages": 2 (para entregas),
     *   "expectedInAt": "2024-01-15T10:00:00Z",
     *   "expectedOutAt": "2024-01-15T12:00:00Z",
     *   "type": "VISITOR" | "DELIVERY" | "SERVICE"
     * }
     *
     * Validações:
     * - condominiumId: obrigatório
     * - unitId: obrigatório
     * - name: obrigatório
     * - type: obrigatório (VISITOR, DELIVERY, SERVICE)
     *
     * Resposta (201): VisitorResponse (status inicial: PENDING)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<VisitorResponse> create(
        @Valid @RequestBody CreateVisitorRequest request
    ) {
        VisitorResponse response = visitorService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /visitors/{id}
     *
     * Atualiza um visitante existente (apenas se status = PENDING).
     *
     * Requer role: STAFF, MANAGER, ADMIN
     *
     * Body (todos campos opcionais):
     * {
     *   "name": "João Pedro Visitante",
     *   "document": "987.654.321-00",
     *   ...
     * }
     *
     * Erros:
     * - 404: Visitante não encontrado
     * - 422: Visitante já foi aprovado/rejeitado
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<VisitorResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateVisitorRequest request
    ) {
        VisitorResponse response = visitorService.update(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /visitors/{id}/approve
     *
     * Aprova um visitante (muda status de PENDING para APPROVED).
     *
     * Requer role: STAFF, MANAGER, ADMIN
     *
     * Body (opcional):
     * {
     *   "note": "Observação da aprovação"
     * }
     *
     * Erros:
     * - 404: Visitante não encontrado
     * - 422: Visitante já foi aprovado ou rejeitado
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<VisitorResponse> approve(
        @PathVariable Long id,
        @RequestBody(required = false) ApproveVisitorRequest request,
        Authentication authentication
    ) {
        // Busca ID do usuário autenticado
        Long approvedBy = getCurrentUserId(authentication.getName());

        VisitorResponse response = visitorService.approve(id, approvedBy, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /visitors/{id}/reject
     *
     * Rejeita um visitante (muda status de PENDING para REJECTED).
     *
     * Requer role: STAFF, MANAGER, ADMIN
     *
     * Body:
     * {
     *   "reason": "Motivo da rejeição (obrigatório)"
     * }
     *
     * Erros:
     * - 404: Visitante não encontrado
     * - 422: Visitante já foi aprovado ou rejeitado
     * - 400: Motivo não fornecido
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<VisitorResponse> reject(
        @PathVariable Long id,
        @Valid @RequestBody RejectVisitorRequest request
    ) {
        VisitorResponse response = visitorService.reject(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /visitors/{id}/checkout
     *
     * Marca saída do visitante (muda status para CHECKED_OUT).
     *
     * Requer role: STAFF, MANAGER, ADMIN
     *
     * Erros:
     * - 404: Visitante não encontrado
     * - 422: Visitante já realizou checkout
     */
    @PostMapping("/{id}/checkout")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<VisitorResponse> checkout(@PathVariable Long id) {
        VisitorResponse response = visitorService.checkout(id);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /visitors/{id}
     *
     * Soft delete de visitante (marca deleted_at).
     *
     * Requer role: ADMIN
     *
     * Resposta (204): No Content
     *
     * Erros:
     * - 404: Visitante não encontrado
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        visitorService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ========== Métodos auxiliares ==========

    /**
     * Busca ID do usuário autenticado pelo email.
     */
    private Long getCurrentUserId(String email) {
        String tenantId = TenantContext.get();
        return userRepo.findByTenantAndEmail(tenantId, email)
            .map(User::getId)
            .orElse(null);
    }
}
