package com.example.condo.service;

import com.example.condo.entity.FinancialConfig;
import com.example.condo.entity.FinancialNotification;
import com.example.condo.entity.Invoice;
import com.example.condo.entity.InvoiceWebhookEvent;
import com.example.condo.entity.Unit;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.FinancialConfigRepository;
import com.example.condo.repo.InvoiceEventRepository;
import com.example.condo.repo.InvoiceRepository;
import com.example.condo.repo.InvoiceWebhookEventRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do processamento de webhooks Asaas em {@link FinancialService}.
 *
 * Cobre: autenticação (token correto/inválido/ausente), mapeamento de eventos,
 * deduplicação por externalEventId e resiliência a eventos desconhecidos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialWebhookTest {

    @Mock private FinancialConfigRepository configRepo;
    @Mock private InvoiceRepository invoiceRepo;
    @Mock private UnitRepository unitRepo;
    @Mock private ResidentRepository residentRepo;
    @Mock private CondominiumRepository condominiumRepo;
    @Mock private InvoiceEventRepository invoiceEventRepo;
    @Mock private InvoiceWebhookEventRepository invoiceWebhookEventRepo;
    @Mock private FinancialNotificationService financialNotificationService;
    @Mock private AuditService auditService;
    @Mock private AsaasGatewayService asaasGatewayService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private FinancialService financialService;

    private static final String TENANT = "tenant-a";
    private static final Long CONDO_ID = 1L;
    private static final String TOKEN = "webhook-token-secreto";

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.set(TENANT);
        UserContext.set(new UserContext.Data("ADMIN", CONDO_ID, null, 99L));

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(invoiceEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(financialNotificationService.logInvoiceNotification(
            any(Invoice.class), any(FinancialNotification.Type.class), anyString(), anyMap()))
            .thenReturn(new FinancialNotification());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    // ===== Autenticação =====

    @Test
    void handleWebhook_shouldReturnUnauthorized_whenTokenMissing() {
        Invoice invoice = invoice(100L, Invoice.Status.AWAITING_PAYMENT);
        invoice.setExternalChargeId("pay_001");
        invoice.setExternalProvider(Invoice.Provider.ASAAS);

        FinancialConfig config = config(CONDO_ID, TOKEN);
        when(invoiceRepo.findByExternalProviderAndExternalChargeId(Invoice.Provider.ASAAS, "pay_001"))
            .thenReturn(Optional.of(invoice));
        when(configRepo.findByTenantIdAndCondominiumId(TENANT, CONDO_ID))
            .thenReturn(Optional.of(config));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            financialService.handleAsaasWebhook(
                Map.of("event", "PAYMENT_RECEIVED", "payment", Map.of("id", "pay_001")),
                null   // token ausente
            )
        );
        assertEquals(401, ex.getStatusCode().value());
        verify(invoiceWebhookEventRepo, never()).save(any());
    }

    @Test
    void handleWebhook_shouldReturnUnauthorized_whenTokenInvalid() {
        Invoice invoice = invoice(101L, Invoice.Status.AWAITING_PAYMENT);
        invoice.setExternalChargeId("pay_002");
        invoice.setExternalProvider(Invoice.Provider.ASAAS);

        FinancialConfig config = config(CONDO_ID, TOKEN);
        when(invoiceRepo.findByExternalProviderAndExternalChargeId(Invoice.Provider.ASAAS, "pay_002"))
            .thenReturn(Optional.of(invoice));
        when(configRepo.findByTenantIdAndCondominiumId(TENANT, CONDO_ID))
            .thenReturn(Optional.of(config));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            financialService.handleAsaasWebhook(
                Map.of("event", "PAYMENT_RECEIVED", "payment", Map.of("id", "pay_002")),
                "token-errado"
            )
        );
        assertEquals(401, ex.getStatusCode().value());
        verify(invoiceWebhookEventRepo, never()).save(any());
    }

    // ===== Mapeamento de eventos =====

    @Test
    void handleWebhook_shouldMarkPaid_whenPaymentReceived() {
        Invoice invoice = invoiceWithCharge("pay_010", Invoice.Status.AWAITING_PAYMENT);
        setupWebhookMocks(invoice, TOKEN);

        Map<String, Object> result = financialService.handleAsaasWebhook(
            Map.of(
                "id", "evt_001",
                "event", "PAYMENT_RECEIVED",
                "payment", Map.of("id", "pay_010", "value", "300.00", "status", "RECEIVED")
            ),
            TOKEN
        );

        assertEquals("processed", result.get("status"));
        assertEquals(Invoice.Status.PAID, invoice.getStatus());
        assertNotNull(invoice.getPaidAt());
    }

    @Test
    void handleWebhook_shouldMarkPaid_whenPaymentConfirmed() {
        Invoice invoice = invoiceWithCharge("pay_011", Invoice.Status.AWAITING_PAYMENT);
        setupWebhookMocks(invoice, TOKEN);

        Map<String, Object> result = financialService.handleAsaasWebhook(
            Map.of(
                "id", "evt_002",
                "event", "PAYMENT_CONFIRMED",
                "payment", Map.of("id", "pay_011", "value", "300.00")
            ),
            TOKEN
        );

        assertEquals("processed", result.get("status"));
        assertEquals(Invoice.Status.PAID, invoice.getStatus());
    }

    @Test
    void handleWebhook_shouldMarkOverdue_whenPaymentOverdue() {
        Invoice invoice = invoiceWithCharge("pay_020", Invoice.Status.AWAITING_PAYMENT);
        setupWebhookMocks(invoice, TOKEN);

        Map<String, Object> result = financialService.handleAsaasWebhook(
            Map.of(
                "id", "evt_003",
                "event", "PAYMENT_OVERDUE",
                "payment", Map.of("id", "pay_020")
            ),
            TOKEN
        );

        assertEquals("processed", result.get("status"));
        assertEquals(Invoice.Status.OVERDUE, invoice.getStatus());
    }

    @Test
    void handleWebhook_shouldMarkCancelled_whenPaymentDeleted() {
        Invoice invoice = invoiceWithCharge("pay_030", Invoice.Status.AWAITING_PAYMENT);
        setupWebhookMocks(invoice, TOKEN);

        Map<String, Object> result = financialService.handleAsaasWebhook(
            Map.of(
                "id", "evt_004",
                "event", "PAYMENT_DELETED",
                "payment", Map.of("id", "pay_030")
            ),
            TOKEN
        );

        assertEquals("processed", result.get("status"));
        assertEquals(Invoice.Status.CANCELLED, invoice.getStatus());
        assertNotNull(invoice.getCancelledAt());
    }

    @Test
    void handleWebhook_shouldMarkCancelled_whenPaymentRefunded() {
        Invoice invoice = invoiceWithCharge("pay_031", Invoice.Status.PAID);
        setupWebhookMocks(invoice, TOKEN);

        Map<String, Object> result = financialService.handleAsaasWebhook(
            Map.of(
                "id", "evt_005",
                "event", "PAYMENT_REFUNDED",
                "payment", Map.of("id", "pay_031")
            ),
            TOKEN
        );

        assertEquals("processed", result.get("status"));
        assertEquals(Invoice.Status.CANCELLED, invoice.getStatus());
        assertNotNull(invoice.getCancelledAt());
    }

    // ===== Deduplicação =====

    @Test
    void handleWebhook_shouldIgnoreDuplicateEvent_whenAlreadyProcessed() {
        Invoice invoice = invoiceWithCharge("pay_040", Invoice.Status.PAID);
        setupWebhookMocks(invoice, TOKEN);

        // Evento já existe no banco
        InvoiceWebhookEvent existing = new InvoiceWebhookEvent();
        existing.setId(99L);
        when(invoiceWebhookEventRepo.findByProviderAndExternalEventId(eq("ASAAS"), anyString()))
            .thenReturn(Optional.of(existing));

        Map<String, Object> result = financialService.handleAsaasWebhook(
            Map.of(
                "id", "evt_dup_001",
                "event", "PAYMENT_RECEIVED",
                "payment", Map.of("id", "pay_040")
            ),
            TOKEN
        );

        assertEquals("duplicate", result.get("status"));
        // Status não deve mudar
        assertEquals(Invoice.Status.PAID, invoice.getStatus());
    }

    @Test
    void handleWebhook_shouldNotChangePaidStatus_whenOverdueArrivesForPaidInvoice() {
        Invoice invoice = invoiceWithCharge("pay_050", Invoice.Status.PAID);
        setupWebhookMocks(invoice, TOKEN);

        financialService.handleAsaasWebhook(
            Map.of(
                "id", "evt_late_001",
                "event", "PAYMENT_OVERDUE",
                "payment", Map.of("id", "pay_050")
            ),
            TOKEN
        );

        // OVERDUE não pode sobrescrever PAID
        assertEquals(Invoice.Status.PAID, invoice.getStatus());
    }

    // ===== Resiliência a eventos desconhecidos =====

    @Test
    void handleWebhook_shouldReturn200_whenEventUnknown() {
        Invoice invoice = invoiceWithCharge("pay_060", Invoice.Status.AWAITING_PAYMENT);
        setupWebhookMocks(invoice, TOKEN);

        // Evento desconhecido não deve lançar exceção
        assertDoesNotThrow(() -> {
            Map<String, Object> result = financialService.handleAsaasWebhook(
                Map.of(
                    "id", "evt_unk_001",
                    "event", "PAYMENT_SOME_FUTURE_EVENT",
                    "payment", Map.of("id", "pay_060")
                ),
                TOKEN
            );
            assertEquals("processed", result.get("status"));
        });
    }

    @Test
    void handleWebhook_shouldIgnore_whenInvoiceNotFound() {
        // Cobrança não encontrada no banco
        when(invoiceRepo.findByExternalProviderAndExternalChargeId(any(), anyString()))
            .thenReturn(Optional.empty());
        when(invoiceRepo.findByTenantIdAndExternalReference(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(asaasGatewayService.isWebhookTokenConfigured()).thenReturn(true);
        when(asaasGatewayService.validateWebhookToken("token-global")).thenReturn(true);
        when(invoiceWebhookEventRepo.findByProviderAndExternalEventId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(invoiceWebhookEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = financialService.handleAsaasWebhook(
            Map.of(
                "id", "evt_nf_001",
                "event", "PAYMENT_RECEIVED",
                "payment", Map.of("id", "pay_unknown_999")
            ),
            "token-global"
        );

        assertEquals("ignored", result.get("status"));
        assertEquals("invoice_not_found", result.get("reason"));
    }

    // ===== validateWebhookTokenForCondo =====

    @Test
    void validateWebhookTokenForCondo_shouldPass_whenTokenMatches() {
        FinancialConfig config = config(CONDO_ID, TOKEN);
        when(configRepo.findByTenantIdAndCondominiumId(TENANT, CONDO_ID))
            .thenReturn(Optional.of(config));

        assertDoesNotThrow(() -> financialService.validateWebhookTokenForCondo(CONDO_ID, TOKEN));
    }

    @Test
    void validateWebhookTokenForCondo_shouldThrow_whenTokenDoesNotMatch() {
        FinancialConfig config = config(CONDO_ID, TOKEN);
        when(configRepo.findByTenantIdAndCondominiumId(TENANT, CONDO_ID))
            .thenReturn(Optional.of(config));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> financialService.validateWebhookTokenForCondo(CONDO_ID, "token-errado"));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void validateWebhookTokenForCondo_shouldThrow_whenCondominiumNotFound() {
        when(configRepo.findByTenantIdAndCondominiumId(TENANT, 999L)).thenReturn(Optional.empty());
        when(configRepo.findFirstByCondominiumId(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> financialService.validateWebhookTokenForCondo(999L, TOKEN));
        assertEquals(401, ex.getStatusCode().value());
    }

    // ===== Helpers =====

    private void setupWebhookMocks(Invoice invoice, String token) {
        FinancialConfig config = config(invoice.getCondominiumId(), token);
        when(invoiceRepo.findByExternalProviderAndExternalChargeId(
            Invoice.Provider.ASAAS, invoice.getExternalChargeId()))
            .thenReturn(Optional.of(invoice));
        when(configRepo.findByTenantIdAndCondominiumId(TENANT, invoice.getCondominiumId()))
            .thenReturn(Optional.of(config));
        when(invoiceWebhookEventRepo.findByProviderAndExternalEventId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(invoiceWebhookEventRepo.save(any(InvoiceWebhookEvent.class)))
            .thenAnswer(i -> i.getArgument(0));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(financialNotificationService.hasNotification(anyLong(), any())).thenReturn(false);
    }

    private Invoice invoiceWithCharge(String chargeId, Invoice.Status status) {
        Invoice invoice = invoice(Long.parseLong(chargeId.replace("pay_", "")), status);
        invoice.setExternalChargeId(chargeId);
        invoice.setExternalProvider(Invoice.Provider.ASAAS);
        return invoice;
    }

    private Invoice invoice(Long id, Invoice.Status status) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setTenantId(TENANT);
        invoice.setCondominiumId(CONDO_ID);
        invoice.setUnitId(200L);
        invoice.setReferenceMonth("2026-04");
        invoice.setChargeType(Invoice.ChargeType.CONDOMINIO);
        invoice.setTitle("Taxa condominial");
        invoice.setLaunchKey("TEST:" + id);
        invoice.setAmount(new BigDecimal("300.00"));
        invoice.setDueDate(LocalDate.of(2026, 4, 30));
        invoice.setStatus(status);
        invoice.setCreatedAt(Instant.now());
        return invoice;
    }

    private FinancialConfig config(Long condominiumId, String token) {
        FinancialConfig config = new FinancialConfig();
        config.setId(1L);
        config.setTenantId(TENANT);
        config.setCondominiumId(condominiumId);
        config.setAsaasEnabled(true);
        config.setAsaasWebhookToken(token);
        config.setMonthlyFee(new BigDecimal("500.00"));
        config.setDueDay(10);
        config.setLateFeePct(new BigDecimal("2.00"));
        config.setInterestPct(new BigDecimal("1.00"));
        return config;
    }
}
