package com.condohub.financial.service;

// ─────────────────────────────────────────────────────────────────────────────
// EXEMPLO DE INTEGRAÇÃO DO AuditService NOS 3 EVENTOS FINANCEIROS CRÍTICOS
//
// Este arquivo mostra apenas os métodos relevantes com os calls de auditoria.
// Integre esses trechos no seu FinancialService existente.
// ─────────────────────────────────────────────────────────────────────────────

import com.condohub.audit.service.AuditService;
import com.condohub.auth.model.AppUserDetails;
import com.condohub.common.context.UserContext;
import com.condohub.financial.entity.Invoice;
import com.condohub.financial.enums.InvoiceStatus;
import com.condohub.financial.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialServiceAuditExemplo {

    private final InvoiceRepository invoiceRepository;
    private final AuditService auditService;

    // ── EVENTO 1: Registro de pagamento manual ────────────────────────────────
    @Transactional
    public Invoice registerPayment(Long invoiceId, BigDecimal valorPago,
                                   String formaPagamento, AppUserDetails currentUser) {

        Long condominiumId = UserContext.resolveCondominiumId(currentUser);

        Invoice invoice = invoiceRepository
                .findByIdAndCondominiumId(invoiceId, condominiumId)
                .orElseThrow(() -> new com.condohub.common.exception.ResourceNotFoundException(
                        "Invoice não encontrada: " + invoiceId));

        InvoiceStatus statusAnterior = invoice.getStatus();

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setPaidAmount(valorPago);
        invoice.setPaymentMethod(formaPagamento);
        invoiceRepository.save(invoice);

        // ✅ AUDITORIA — registro de pagamento manual
        auditService.log(
                condominiumId,
                currentUser,
                "Financeiro",
                "Registro de pagamento",
                String.format("Invoice #%d paga manualmente — Unidade %s • R$ %.2f via %s • %s → PAID",
                        invoice.getId(),
                        invoice.getUnitDisplay(),
                        valorPago,
                        formaPagamento,
                        statusAnterior)
        );

        return invoice;
    }

    // ── EVENTO 2: Geração de cobrança externa (Asaas) ────────────────────────
    @Transactional
    public Invoice createExternalCharge(Long invoiceId, AppUserDetails currentUser) {

        Long condominiumId = UserContext.resolveCondominiumId(currentUser);

        Invoice invoice = invoiceRepository
                .findByIdAndCondominiumId(invoiceId, condominiumId)
                .orElseThrow(() -> new com.condohub.common.exception.ResourceNotFoundException(
                        "Invoice não encontrada: " + invoiceId));

        // TODO: implementar chamada real ao AsaasClient quando asaasEnabled = true
        // Por ora: registrar a intenção e retornar estado atual
        // AsaasChargeResponse asaasResponse = asaasClient.createCharge(invoice);
        // invoice.setExternalChargeId(asaasResponse.getId());
        // invoice.setExternalStatus("PENDING");
        // invoice.setPixQrCode(asaasResponse.getPixQrCode());
        // invoice.setBoletoUrl(asaasResponse.getBankSlipUrl());

        // ✅ AUDITORIA — geração de cobrança externa
        auditService.log(
                condominiumId,
                currentUser,
                "Financeiro",
                "Geração de cobrança externa",
                String.format("Cobrança Asaas solicitada para Invoice #%d — Unidade %s • R$ %.2f • Vencimento %s",
                        invoice.getId(),
                        invoice.getUnitDisplay(),
                        invoice.getValor(),
                        invoice.getVencimento())
        );

        return invoiceRepository.save(invoice);
    }

    // ── EVENTO 3: Lançamento em lote (já coberto no FinancialLaunchService) ──
    // Ver: 2_FinancialLaunchService.java — método launchCharges()
    // O auditService.log() já está implementado lá com a descrição completa.
}
