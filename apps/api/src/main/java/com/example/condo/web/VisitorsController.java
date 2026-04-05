package com.example.condo.web;

import com.example.condo.dto.visitor.*;
import com.example.condo.service.VisitorService;
import com.example.condo.tenant.UserContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Controller de visitantes e entregas.
 *
 * Regras de visibilidade por role (aplicadas no service):
 * - SUPERUSER: vê tudo
 * - PORTARIA: vê todas as visitas e entregas do condomínio
 * - MORADOR: vê apenas visitas da sua unidade
 * - ADMIN/SINDICO/ZELADOR: veem apenas entregas (type=DELIVERY)
 */
@RestController
@RequestMapping({"/visitors", "/api/visitors"})
public class VisitorsController {

    private final VisitorService visitorService;

    public VisitorsController(VisitorService visitorService) {
        this.visitorService = visitorService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','OPERADOR','ZELADOR','PORTARIA','MORADOR')")
    public Page<VisitorResponse> list(
        @RequestParam(value = "condoId",       required = false) Long condominiumId,
        @RequestParam(value = "condominiumId", required = false) Long condominiumIdAlt,
        @RequestParam(required = false) Long unitId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Instant dateFrom,
        @RequestParam(required = false) Instant dateTo,
        Pageable pageable
    ) {
        Long effectiveCondoId = condominiumId != null ? condominiumId : condominiumIdAlt;
        return visitorService.search(effectiveCondoId, unitId, status, type, dateFrom, dateTo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','OPERADOR','ZELADOR','PORTARIA','MORADOR')")
    public ResponseEntity<VisitorResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(visitorService.getById(id));
    }

    /**
     * POST /visitors — cria visita ou entrega.
     * MORADOR: unitId forçado para a própria unidade no service.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','PORTARIA','MORADOR')")
    public ResponseEntity<VisitorResponse> create(
        @Valid @RequestBody CreateVisitorRequest request,
        Authentication authentication
    ) {
        // Guarda de segurança: MORADOR não pode especificar outra unidade
        if (isMorador(authentication)) {
            Long moradorUnitId = UserContext.unitId();
            if (request.unitId() != null && moradorUnitId != null
                && !Objects.equals(request.unitId(), moradorUnitId)) {
                throw new AccessDeniedException(
                    "Morador só pode registrar visitantes para sua própria unidade");
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(visitorService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','PORTARIA')")
    public ResponseEntity<VisitorResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateVisitorRequest request
    ) {
        return ResponseEntity.ok(visitorService.update(id, request));
    }

    /**
     * PATCH /visitors/{id}/status — transição unificada de status.
     *
     * Regras aplicadas no service:
     * - Visita pessoal: apenas MORADOR da unidade de destino ou SUPERUSER
     * - Entrega: PORTARIA, SINDICO, ZELADOR ou SUPERUSER
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','PORTARIA','MORADOR')")
    public ResponseEntity<VisitorResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateVisitorStatusRequest request
    ) {
        return ResponseEntity.ok(visitorService.updateStatus(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','MORADOR')")
    public ResponseEntity<VisitorResponse> approve(
        @PathVariable Long id,
        @RequestBody(required = false) ApproveVisitorRequest request
    ) {
        Long approvedBy = UserContext.userId();
        return ResponseEntity.ok(visitorService.approve(id, approvedBy, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','MORADOR')")
    public ResponseEntity<VisitorResponse> reject(
        @PathVariable Long id,
        @Valid @RequestBody RejectVisitorRequest request
    ) {
        return ResponseEntity.ok(visitorService.reject(id, request));
    }

    @PostMapping("/{id}/checkout")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','PORTARIA')")
    public ResponseEntity<VisitorResponse> checkout(@PathVariable Long id) {
        return ResponseEntity.ok(visitorService.checkout(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        visitorService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ========== Helpers ==========

    private boolean isMorador(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_MORADOR"));
    }
}
