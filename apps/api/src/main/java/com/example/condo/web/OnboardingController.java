package com.example.condo.web;

import com.example.condo.dto.onboarding.CondominiumRequestDto;
import com.example.condo.dto.onboarding.CondominiumRequestResponse;
import com.example.condo.dto.onboarding.RejectRequestDto;
import com.example.condo.service.OnboardingService;
import com.example.condo.tenant.UserContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller de onboarding de condomínios.
 *
 * Endpoints públicos (sem autenticação):
 *   POST /api/onboarding/request — enviar formulário de solicitação
 *
 * Endpoints para SUPERUSER:
 *   GET  /api/onboarding/requests         — listar solicitações
 *   GET  /api/onboarding/requests/count   — contagem de pendentes (para badge)
 *   POST /api/onboarding/requests/{id}/approve — aprovar
 *   POST /api/onboarding/requests/{id}/reject  — rejeitar
 */
@RestController
@RequestMapping({"/onboarding", "/api/onboarding"})
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    /**
     * Público — qualquer pessoa pode enviar uma solicitação de cadastro.
     */
    @PostMapping("/request")
    public ResponseEntity<Void> submitRequest(
        @Valid @RequestBody CondominiumRequestDto dto
    ) {
        onboardingService.createRequest(dto);
        return ResponseEntity.accepted().build();
    }

    /**
     * SUPERUSER — lista solicitações.
     */
    @GetMapping("/requests")
    @PreAuthorize("hasRole('SUPERUSER')")
    public Page<CondominiumRequestResponse> listRequests(
        @RequestParam(defaultValue = "PENDING") String status,
        Pageable pageable
    ) {
        return onboardingService.list(status, pageable);
    }

    /**
     * SUPERUSER — contagem de pendentes (para badge/notificação).
     */
    @GetMapping("/requests/count")
    @PreAuthorize("hasRole('SUPERUSER')")
    public Map<String, Long> countPending() {
        return Map.of("pending", onboardingService.countPending());
    }

    /**
     * SUPERUSER — aprovar solicitação.
     * Cria condomínio + usuário ADMIN com senha temporária.
     */
    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("hasRole('SUPERUSER')")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        Long reviewerId = UserContext.userId();
        onboardingService.approve(id, reviewerId);
        return ResponseEntity.ok().build();
    }

    /**
     * SUPERUSER — rejeitar solicitação com motivo.
     */
    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasRole('SUPERUSER')")
    public ResponseEntity<Void> reject(
        @PathVariable Long id,
        @Valid @RequestBody RejectRequestDto dto
    ) {
        Long reviewerId = UserContext.userId();
        onboardingService.reject(id, dto.reason(), reviewerId);
        return ResponseEntity.ok().build();
    }
}
