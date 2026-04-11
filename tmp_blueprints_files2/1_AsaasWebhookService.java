package com.condohub.financial.service;

import com.condohub.financial.entity.FinancialConfig;
import com.condohub.financial.entity.Invoice;
import com.condohub.financial.enums.InvoiceStatus;
import com.condohub.financial.repository.FinancialConfigRepository;
import com.condohub.financial.repository.InvoiceRepository;
import com.condohub.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasWebhookService {

    private final FinancialConfigRepository financialConfigRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditService auditService;

    /**
     * Valida o token do webhook contra o token configurado no condomínio.
     * Retorna false se: token nulo, config não encontrada, Asaas desabilitado,
     * webhookToken não configurado, ou token não bate.
     */
    public boolean validateToken(Long condominiumId, String receivedToken) {
        if (receivedToken == null || receivedToken.isBlank()) {
            log.warn("Webhook sem token — condominiumId={}", condominiumId);
            return false;
        }

        Optional<FinancialConfig> configOpt = financialConfigRepository.findByCondominiumId(condominiumId);
        if (configOpt.isEmpty()) {
            log.warn("Webhook para condomínio sem FinancialConfig — condominiumId={}", condominiumId);
            return false;
        }

        FinancialConfig config = configOpt.get();

        if (!Boolean.TRUE.equals(config.getAsaasEnabled())) {
            log.warn("Webhook recebido mas Asaas desabilitado — condominiumId={}", condominiumId);
            return false;
        }

        if (config.getAsaasWebhookToken() == null || config.getAsaasWebhookToken().isBlank()) {
            log.warn("Webhook recebido mas asaasWebhookToken não configurado — condominiumId={}", condominiumId);
            return false;
        }

        boolean valid = config.getAsaasWebhookToken().equals(receivedToken);
        if (!valid) {
            log.warn("Webhook com token inválido — condominiumId={}", condominiumId);
        }
        return valid;
    }

    /**
     * Processa eventos do Asaas mapeando para transições de status do Invoice.
     *
     * Eventos suportados:
     * - PAYMENT_RECEIVED / PAYMENT_CONFIRMED → PAID
     * - PAYMENT_OVERDUE                      → OVERDUE
     * - PAYMENT_DELETED / PAYMENT_REFUNDED   → CANCELLED
     */
    @Transactional
    public void processEvent(Long condominiumId, Map<String, Object> payload) {
        String event = (String) payload.get("event");
        Map<String, Object> paymentData = (Map<String, Object>) payload.get("payment");

        if (event == null || paymentData == null) {
            log.warn("Webhook com payload incompleto — condominiumId={} payload={}", condominiumId, payload);
            return;
        }

        String externalChargeId = (String) paymentData.get("id");
        if (externalChargeId == null) {
            log.warn("Webhook sem payment.id — condominiumId={}", condominiumId);
            return;
        }

        log.info("Processando webhook Asaas — condominiumId={} event={} chargeId={}",
                condominiumId, event, externalChargeId);

        Optional<Invoice> invoiceOpt = invoiceRepository.findByExternalChargeIdAndCondominiumId(
                externalChargeId, condominiumId);

        if (invoiceOpt.isEmpty()) {
            log.warn("Invoice não encontrada para chargeId={} condominiumId={}", externalChargeId, condominiumId);
            return;
        }

        Invoice invoice = invoiceOpt.get();
        InvoiceStatus previousStatus = invoice.getStatus();

        switch (event) {
            case "PAYMENT_RECEIVED", "PAYMENT_CONFIRMED" -> {
                invoice.setStatus(InvoiceStatus.PAID);
                invoice.setPaidAt(LocalDateTime.now());
                auditService.log(
                        condominiumId,
                        null, // sistema
                        "Financeiro",
                        "Pagamento confirmado via Asaas",
                        String.format("Invoice #%d — %s → PAID (chargeId: %s)",
                                invoice.getId(), previousStatus, externalChargeId)
                );
            }
            case "PAYMENT_OVERDUE" -> {
                invoice.setStatus(InvoiceStatus.OVERDUE);
                auditService.log(
                        condominiumId,
                        null,
                        "Financeiro",
                        "Pagamento vencido via Asaas",
                        String.format("Invoice #%d marcada como OVERDUE (chargeId: %s)",
                                invoice.getId(), externalChargeId)
                );
            }
            case "PAYMENT_DELETED", "PAYMENT_REFUNDED" -> {
                invoice.setStatus(InvoiceStatus.CANCELLED);
                auditService.log(
                        condominiumId,
                        null,
                        "Financeiro",
                        "Pagamento cancelado/estornado via Asaas",
                        String.format("Invoice #%d cancelada (event: %s, chargeId: %s)",
                                invoice.getId(), event, externalChargeId)
                );
            }
            default -> log.info("Evento Asaas ignorado — event={} chargeId={}", event, externalChargeId);
        }

        invoiceRepository.save(invoice);
        log.info("Invoice #{}  {} → {} via webhook Asaas",
                invoice.getId(), previousStatus, invoice.getStatus());
    }
}
