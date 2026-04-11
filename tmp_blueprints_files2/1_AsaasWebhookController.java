package com.condohub.financial.controller;

import com.condohub.financial.service.FinancialService;
import com.condohub.financial.service.AsaasWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/financial/webhooks")
@RequiredArgsConstructor
public class AsaasWebhookController {

    private final AsaasWebhookService asaasWebhookService;

    /**
     * Endpoint público para receber eventos do Asaas.
     * A autenticação é feita via header asaas-access-token validado por condomínio.
     * NÃO adicionar ao SecurityConfig como autenticado — deve permanecer permitAll().
     */
    @PostMapping("/asaas/{condominiumId}")
    public ResponseEntity<Void> handleAsaasWebhook(
            @PathVariable Long condominiumId,
            @RequestHeader(value = "asaas-access-token", required = false) String webhookToken,
            @RequestBody Map<String, Object> payload) {

        log.info("Webhook Asaas recebido — condominiumId={} event={}",
                condominiumId, payload.get("event"));

        // Validação do token ANTES de qualquer processamento
        boolean tokenValid = asaasWebhookService.validateToken(condominiumId, webhookToken);
        if (!tokenValid) {
            log.warn("Webhook Asaas rejeitado — token inválido para condominiumId={}", condominiumId);
            return ResponseEntity.status(401).build();
        }

        try {
            asaasWebhookService.processEvent(condominiumId, payload);
        } catch (Exception e) {
            // Asaas reenvía webhooks em caso de erro — logar e retornar 200
            // para evitar loop de reenvio por erros não-críticos
            log.error("Erro ao processar webhook Asaas condominiumId={} event={}: {}",
                    condominiumId, payload.get("event"), e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }
}
