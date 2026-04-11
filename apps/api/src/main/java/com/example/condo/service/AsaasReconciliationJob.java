package com.example.condo.service;

import com.example.condo.audit.AuditAction;
import com.example.condo.audit.AuditModule;
import com.example.condo.entity.Invoice;
import com.example.condo.entity.InvoiceEvent;
import com.example.condo.repo.InvoiceEventRepository;
import com.example.condo.repo.InvoiceRepository;
import com.example.condo.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Job de reconciliação de cobranças externas do Asaas.
 *
 * <p>Problema que resolve: se o servidor do CondoHub estiver off no momento em que o Asaas
 * disparar o webhook de pagamento, a invoice fica permanentemente em AWAITING_PAYMENT
 * mesmo o pagamento já tendo sido realizado.
 *
 * <p>Solução: todo dia às 8h, busca invoices com status EXTERNAL_CREATED ou AWAITING_PAYMENT
 * criadas há mais de 1 dia e consulta o status atual no Asaas via
 * {@link AsaasGatewayService#getCharge}. Se o status divergir, atualiza e registra auditoria.
 *
 * <p>Exemplo de uso no Railway: a cron já está configurada. Não é necessário nenhuma ação
 * para ativá-la além de ter {@code ASAAS_ENABLED=true}.
 */
@Component
public class AsaasReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(AsaasReconciliationJob.class);

    /**
     * Número de horas que uma invoice deve ter antes de ser elegível para reconciliação.
     * Evita reconciliar cobranças recém-criadas onde o webhook pode chegar a qualquer momento.
     */
    private static final long MIN_AGE_HOURS = 24;

    private final InvoiceRepository invoiceRepo;
    private final InvoiceEventRepository invoiceEventRepo;
    private final AsaasGatewayService asaasGatewayService;
    private final AuditService auditService;

    public AsaasReconciliationJob(
        InvoiceRepository invoiceRepo,
        InvoiceEventRepository invoiceEventRepo,
        AsaasGatewayService asaasGatewayService,
        AuditService auditService
    ) {
        this.invoiceRepo = invoiceRepo;
        this.invoiceEventRepo = invoiceEventRepo;
        this.asaasGatewayService = asaasGatewayService;
        this.auditService = auditService;
    }

    /**
     * Executa a reconciliação todos os dias às 8h.
     *
     * <p>Para executar manualmente em produção via Railway CLI:
     * <pre>railway run --service api java -Dspring.main.web-application-type=none
     *   -Dreconciliation.run-once=true -jar app.jar</pre>
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void reconcile() {
        log.info("[Reconciliação Asaas] Iniciando job de reconciliação de cobranças externas");

        Instant cutoff = Instant.now().minus(MIN_AGE_HOURS, ChronoUnit.HOURS);
        List<Invoice> pendingInvoices = invoiceRepo.findPendingExternalChargesOlderThan(cutoff);

        if (pendingInvoices.isEmpty()) {
            log.info("[Reconciliação Asaas] Nenhuma invoice pendente encontrada para reconciliação.");
            return;
        }

        log.info("[Reconciliação Asaas] {} invoice(s) encontrada(s) para verificação.", pendingInvoices.size());

        int reconciled = 0;
        int skipped = 0;
        int errors = 0;

        for (Invoice invoice : pendingInvoices) {
            try {
                boolean updated = reconcileInvoice(invoice);
                if (updated) reconciled++;
                else skipped++;
            } catch (Exception ex) {
                errors++;
                log.warn("[Reconciliação Asaas] Erro ao reconciliar invoice #{}: {}",
                    invoice.getId(), ex.getMessage());
            }
        }

        log.info("[Reconciliação Asaas] Concluído — reconciliadas={}, sem_alteração={}, erros={}",
            reconciled, skipped, errors);
    }

    /**
     * Reconcilia uma invoice individual com o Asaas.
     *
     * @return {@code true} se o status foi atualizado, {@code false} se já estava sincronizado
     */
    @Transactional
    public boolean reconcileInvoice(Invoice invoice) {
        Optional<AsaasGatewayService.AsaasChargeResponse> chargeOpt =
            asaasGatewayService.getCharge(invoice.getExternalChargeId(), invoice.getCondominiumId());

        if (chargeOpt.isEmpty()) {
            log.debug("[Reconciliação Asaas] Cobrança não encontrada no Asaas — invoiceId={} chargeId={}",
                invoice.getId(), invoice.getExternalChargeId());
            return false;
        }

        AsaasGatewayService.AsaasChargeResponse charge = chargeOpt.get();
        Invoice.Status newStatus = mapAsaasStatus(charge.status(), invoice);

        if (newStatus == null || newStatus == invoice.getStatus()) {
            log.debug("[Reconciliação Asaas] Status já sincronizado — invoiceId={} status={}",
                invoice.getId(), invoice.getStatus());
            return false;
        }

        Invoice.Status previousStatus = invoice.getStatus();
        invoice.setStatus(newStatus);
        invoice.setExternalStatus(charge.status());
        invoice.setExternalUpdatedAt(Instant.now());

        if (newStatus == Invoice.Status.PAID) {
            invoice.setPaidAmount(invoice.getPaidAmount() != null ? invoice.getPaidAmount() : invoice.getAmount());
            invoice.setPaidAt(Instant.now());
        } else if (newStatus == Invoice.Status.CANCELLED) {
            invoice.setCancelledAt(Instant.now());
        }

        invoiceRepo.save(invoice);

        // Registra evento na linha do tempo da invoice
        recordReconciliationEvent(invoice, previousStatus, newStatus, charge.status());

        // Audita com actor null (sistema) usando TenantContext do invoice
        try {
            TenantContext.set(invoice.getTenantId());
            auditService.log(
                AuditModule.FINANCIAL,
                AuditAction.STATUS_CHANGE,
                "Invoice",
                invoice.getId(),
                invoice.getCondominiumId(),
                String.format("Reconciliação Asaas: status atualizado de %s para %s (externo: %s).",
                    previousStatus, newStatus, charge.status()),
                null,
                invoice,
                java.util.Map.of(
                    "source", "RECONCILIATION_JOB",
                    "externalChargeId", invoice.getExternalChargeId(),
                    "asaasStatus", charge.status(),
                    "previousStatus", previousStatus.name(),
                    "newStatus", newStatus.name()
                )
            );
        } finally {
            TenantContext.clear();
        }

        log.info("[Reconciliação Asaas] Invoice #{} atualizada: {} → {} (Asaas: {})",
            invoice.getId(), previousStatus, newStatus, charge.status());
        return true;
    }

    /**
     * Mapeia o status retornado pelo Asaas para o status interno do CondoHub.
     *
     * @param asaasStatus Status retornado pela API do Asaas (ex: "RECEIVED", "OVERDUE", "DELETED")
     * @param invoice     Invoice para contexto (ex: não regredir PAID)
     * @return Novo status interno ou {@code null} se o status não for mapeável
     */
    Invoice.Status mapAsaasStatus(String asaasStatus, Invoice invoice) {
        if (asaasStatus == null) return null;
        return switch (asaasStatus.toUpperCase()) {
            case "RECEIVED", "CONFIRMED" -> {
                // Não regredir se já está paga
                if (invoice.getStatus() == Invoice.Status.PAID) yield null;
                yield Invoice.Status.PAID;
            }
            case "OVERDUE" -> {
                // Não regredir se já está paga
                if (invoice.getStatus() == Invoice.Status.PAID) yield null;
                yield Invoice.Status.OVERDUE;
            }
            case "DELETED", "CANCELLED", "REFUNDED" -> Invoice.Status.CANCELLED;
            case "PENDING", "AWAITING_RISK_ANALYSIS" -> {
                // Manter AWAITING_PAYMENT se ainda não chegou o pagamento
                if (invoice.getStatus() == Invoice.Status.EXTERNAL_CREATED) {
                    yield Invoice.Status.AWAITING_PAYMENT;
                }
                yield null; // sem alteração necessária
            }
            default -> null; // status desconhecido — não alterar
        };
    }

    private void recordReconciliationEvent(
        Invoice invoice,
        Invoice.Status previousStatus,
        Invoice.Status newStatus,
        String asaasStatus
    ) {
        String message = String.format(
            "Reconciliação automática: status atualizado de %s para %s (Asaas: %s).",
            previousStatus, newStatus, asaasStatus
        );
        InvoiceEvent event = new InvoiceEvent();
        event.setInvoiceId(invoice.getId());
        event.setTenantId(invoice.getTenantId());
        event.setCondominiumId(invoice.getCondominiumId());
        event.setEventType("RECONCILIATION");
        event.setTitle(message);
        event.setMessage(message);
        event.setMetadata(String.format(
            "{\"source\":\"RECONCILIATION_JOB\",\"asaasStatus\":\"%s\",\"previousStatus\":\"%s\",\"newStatus\":\"%s\"}",
            asaasStatus, previousStatus.name(), newStatus.name()
        ));
        event.setCreatedAt(Instant.now());
        invoiceEventRepo.save(event);
    }
}
