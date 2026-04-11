package com.example.condo.service;

import com.example.condo.dto.financial.FinancialSummaryResponse;
import com.example.condo.entity.FinancialConfig;
import com.example.condo.entity.FinancialNotification;
import com.example.condo.entity.Invoice;
import com.example.condo.entity.Resident;
import com.example.condo.entity.Unit;
import com.example.condo.exception.BusinessException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialServiceTest {

    @Mock
    private FinancialConfigRepository configRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private CondominiumRepository condominiumRepository;

    @Mock
    private InvoiceEventRepository invoiceEventRepository;

    @Mock
    private InvoiceWebhookEventRepository invoiceWebhookEventRepository;

    @Mock
    private FinancialNotificationService financialNotificationService;

    @Mock
    private AuditService auditService;

    @Mock
    private AsaasGatewayService asaasGatewayService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FinancialService financialService;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-a");
        UserContext.set(new UserContext.Data("ADMIN", 1L, null, 99L));
        when(configRepository.findByTenantIdAndCondominiumId("tenant-a", 1L)).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (Exception ignored) {
            // Mockito stub setup only
        }
        when(financialNotificationService.logInvoiceNotification(any(Invoice.class), any(FinancialNotification.Type.class), anyString(), anyMap()))
                .thenAnswer(invocation -> new FinancialNotification());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    @Test
    void superuserSummaryWithoutCondominiumShouldAggregateTenantInsteadOfReturningEmptyShape() {
        UserContext.set(new UserContext.Data("SUPERUSER", null, null, 1L));
        when(invoiceRepository.summaryAdvanced("tenant-a", null, null, null, null))
                .thenReturn(new Object[]{2L, 300.0, 100.0, 120.0, 80.0});

        FinancialSummaryResponse summary = financialService.summary(null);

        assertEquals(2L, summary.totalInvoices());
        assertEquals(300.0, summary.totalAmount().doubleValue());
        assertEquals(100.0, summary.paidAmount().doubleValue());
        assertEquals(120.0, summary.pendingAmount().doubleValue());
        assertEquals(80.0, summary.overdueAmount().doubleValue());
        verify(invoiceRepository).summaryAdvanced("tenant-a", null, null, null, null);
    }

    @Test
    void summaryShouldIncludeBreakdownsWithoutBreakingLegacyFields() {
        when(invoiceRepository.summaryAdvanced("tenant-a", 1L, null, "2026-01", "2026-04"))
                .thenReturn(new Object[]{4L, 900.0, 300.0, 400.0, 200.0});
        when(invoiceRepository.summaryByStatus("tenant-a", 1L, null, "2026-01", "2026-04"))
                .thenReturn(List.<Object[]>of(
                        new Object[]{Invoice.Status.PENDING, 2L, 400.0},
                        new Object[]{Invoice.Status.OVERDUE, 1L, 200.0}
                ));
        when(invoiceRepository.delinquencyByBlock("tenant-a", 1L, null, "2026-01", "2026-04"))
                .thenReturn(List.<Object[]>of(new Object[]{"Bloco A", 1L, 200.0, 600.0}));
        when(invoiceRepository.summaryByReferenceMonth("tenant-a", 1L, null, "2026-01", "2026-04"))
                .thenReturn(List.<Object[]>of(new Object[]{"2026-04", 3L, 700.0, 300.0, 200.0}));

        FinancialSummaryResponse summary = financialService.summary(1L, "2026-01", "2026-04");

        assertEquals(4L, summary.totalInvoices());
        assertEquals(2, summary.totalsByStatus().size());
        assertEquals("PENDING", summary.totalsByStatus().get(0).status());
        assertEquals(1, summary.delinquencyByBlock().size());
        assertEquals("Bloco A", summary.delinquencyByBlock().get(0).block());
        assertEquals(1, summary.totalsByReferenceMonth().size());
        assertEquals("2026-04", summary.totalsByReferenceMonth().get(0).referenceMonth());
    }

    @Test
    void searchInvoicesShouldValidateResidentOwnershipWithinCondominium() {
        Resident resident = new Resident();
        resident.setId(88L);
        resident.setTenantId("tenant-a");
        resident.setCondominiumId(2L);
        resident.setUnitId(300L);
        when(residentRepository.findByTenantIdAndId("tenant-a", 88L)).thenReturn(Optional.of(resident));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                financialService.searchInvoices(
                        1L,
                        null,
                        88L,
                        null,
                        null,
                null,
                null,
                null,
                null,
                null,
                "dueDate",
                "DESC",
                PageRequest.of(0, 20)
        )
        );

        assertEquals("O morador informado não pertence ao condomínio selecionado", exception.getMessage());
        verify(invoiceRepository, never()).searchAdvanced(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listResidentInvoicesShouldReturnOnlyScopedUnitAndExposeExternalLinks() {
        UserContext.set(new UserContext.Data("MORADOR", 1L, 244L, 55L));
        Invoice invoice = invoice(930L, Invoice.Status.AWAITING_PAYMENT, new BigDecimal("300.00"));
        invoice.setBoletoUrl("https://asaas.example/boleto/930");
        invoice.setInvoiceUrl("https://asaas.example/checkout/930");
        invoice.setPixCopyPaste("000201...");

        when(invoiceRepository.searchAdvanced(
                eq("tenant-a"),
                eq(1L),
                eq(244L),
                eq(null),
                eq("AWAITING_PAYMENT"),
                eq(null),
                eq("2026-04"),
                eq("2026-04"),
                eq(null),
                eq(null),
                eq("%multa%"),
                eq("dueDate"),
                eq("DESC"),
                any()
        )).thenReturn(new PageImpl<>(List.of(invoice), PageRequest.of(0, 10), 1));
        Resident resident = new Resident();
        resident.setId(55L);
        resident.setTenantId("tenant-a");
        resident.setCondominiumId(1L);
        resident.setUnitId(244L);
        resident.setName("Rafaela Prado");
        when(residentRepository.findByTenantIdAndCondominiumIdAndUnitIdIn("tenant-a", 1L, List.of(244L)))
                .thenReturn(List.of(resident));

        var page = financialService.listResidentInvoices(
                "AWAITING_PAYMENT",
                "multa",
                "2026-04",
                "2026-04",
                "dueDate",
                "DESC",
                PageRequest.of(0, 10)
        );

        assertEquals(1, page.getTotalElements());
        assertEquals("https://asaas.example/boleto/930", page.getContent().get(0).boletoUrl());
        assertEquals("https://asaas.example/checkout/930", page.getContent().get(0).invoiceUrl());
        assertEquals("000201...", page.getContent().get(0).pixCopyPaste());
        assertEquals("Rafaela Prado", page.getContent().get(0).residentName());
    }

    @Test
    void listInvoicesShouldPassNullSearchPatternWhenQueryIsMissing() {
        when(invoiceRepository.searchAdvanced(
                eq("tenant-a"),
                eq(1L),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("dueDate"),
                eq("DESC"),
                any()
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        financialService.searchInvoices(
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "dueDate",
                "DESC",
                PageRequest.of(0, 20)
        );

        verify(invoiceRepository).searchAdvanced(
                eq("tenant-a"),
                eq(1L),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("dueDate"),
                eq("DESC"),
                any()
        );
    }

    @Test
    void searchInvoicesShouldWrapTextQueryBeforeCallingRepository() {
        when(invoiceRepository.searchAdvanced(
                eq("tenant-a"),
                eq(1L),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("%barulho%"),
                eq("dueDate"),
                eq("DESC"),
                any()
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        financialService.searchInvoices(
                1L,
                null,
                null,
                null,
                null,
                "  barulho  ",
                null,
                null,
                null,
                null,
                "dueDate",
                "DESC",
                PageRequest.of(0, 20)
        );

        verify(invoiceRepository).searchAdvanced(
                eq("tenant-a"),
                eq(1L),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("%barulho%"),
                eq("dueDate"),
                eq("DESC"),
                any()
        );
    }

    @Test
    void listResidentInvoicesShouldAllowSindicoWithUnitScopedOwnership() {
        UserContext.set(new UserContext.Data("SINDICO", 1L, 244L, 77L));
        Invoice invoice = invoice(931L, Invoice.Status.PENDING, new BigDecimal("180.00"));

        when(invoiceRepository.searchAdvanced(
                eq("tenant-a"),
                eq(1L),
                eq(244L),
                eq(null),
                eq("PENDING"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("dueDate"),
                eq("DESC"),
                any()
        )).thenReturn(new PageImpl<>(List.of(invoice), PageRequest.of(0, 10), 1));

        var page = financialService.listResidentInvoices("PENDING", null, null, null, "dueDate", "DESC", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals(244L, page.getContent().get(0).unitId());
    }

    @Test
    void saveConfigShouldPersistWebhookTokenAndAuditMaskedValue() {
        when(configRepository.save(any(FinancialConfig.class))).thenAnswer(invocation -> {
            FinancialConfig config = invocation.getArgument(0);
            config.setId(41L);
            return config;
        });

        FinancialConfig saved = financialService.saveConfig(
                1L,
                new BigDecimal("620.00"),
                12,
                new BigDecimal("2.00"),
                new BigDecimal("1.00"),
                "admin@bossanova.com",
                "EMAIL",
                "BOLETO",
                true,
                false,
                true,
                " token-secreto-1234 "
        );

        assertEquals("token-secreto-1234", saved.getAsaasWebhookToken());

        ArgumentCaptor<FinancialConfig> configCaptor = ArgumentCaptor.forClass(FinancialConfig.class);
        verify(configRepository).save(configCaptor.capture());
        assertEquals("token-secreto-1234", configCaptor.getValue().getAsaasWebhookToken());

        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> detailsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).log(
                eq(com.example.condo.audit.AuditModule.FINANCIAL),
                eq(com.example.condo.audit.AuditAction.UPDATE_FINANCIAL_CONFIG),
                eq("FinancialConfig"),
                eq(41L),
                eq(1L),
                anyString(),
                beforeCaptor.capture(),
                afterCaptor.capture(),
                detailsCaptor.capture()
        );

        FinancialConfig auditedAfter = (FinancialConfig) afterCaptor.getValue();
        assertTrue(String.valueOf(auditedAfter.getAsaasWebhookToken()).endsWith("1234"));
        assertTrue(!"token-secreto-1234".equals(auditedAfter.getAsaasWebhookToken()));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) detailsCaptor.getValue();
        assertEquals(true, details.get("asaasWebhookTokenConfigured"));
    }

    @Test
    void handleAsaasWebhookShouldRejectInvalidCondominiumTokenBeforePersistingEvent() {
        Invoice invoice = invoice(950L, Invoice.Status.PENDING, new BigDecimal("300.00"));
        invoice.setExternalProvider(Invoice.Provider.ASAAS);
        invoice.setExternalChargeId("pay_123");

        FinancialConfig config = new FinancialConfig();
        config.setId(5L);
        config.setTenantId("tenant-a");
        config.setCondominiumId(1L);
        config.setAsaasEnabled(true);
        config.setAsaasWebhookToken("token-correto");

        when(invoiceRepository.findByExternalProviderAndExternalChargeId(Invoice.Provider.ASAAS, "pay_123"))
                .thenReturn(Optional.of(invoice));
        when(configRepository.findByTenantIdAndCondominiumId("tenant-a", 1L)).thenReturn(Optional.of(config));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                financialService.handleAsaasWebhook(
                        Map.of(
                                "event", "PAYMENT_RECEIVED",
                                "payment", Map.of("id", "pay_123")
                        ),
                        "token-incorreto"
                )
        );

        assertEquals(401, exception.getStatusCode().value());
        verify(invoiceWebhookEventRepository, never()).save(any());
    }

    @Test
    void launchChargesShouldCreateNewInvoiceForSingleUnit() {
        Unit unit = unit(244L, 1L, "244", "A");
        when(unitRepository.findByTenantIdAndCondominiumIdOrderByBlockAscNumberAsc("tenant-a", 1L))
                .thenReturn(List.of(unit));
        when(unitRepository.findByTenantIdAndId("tenant-a", 244L)).thenReturn(Optional.of(unit));
        when(invoiceRepository.existsByTenantIdAndUnitIdAndLaunchKey("tenant-a", 244L, "ONE_TIME:MULTA:2026-04:2026-04-10:multa-por-barulho:SINGLE_UNIT:PER_UNIT"))
                .thenReturn(false);
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(501L);
            return invoice;
        });

        Map<String, Object> response = financialService.launchCharges(
                1L,
                "ONE_TIME",
                "SINGLE_UNIT",
                "MULTA",
                "PER_UNIT",
                "PIX_AND_BOLETO",
                new BigDecimal("300.00"),
                "Multa por barulho",
                "Uso da churrasqueira com caixa de som ligada, após as 22h",
                "2026-04",
                LocalDate.of(2026, 4, 10),
                244L,
                List.of(),
                List.of()
        );

        assertEquals(1, response.get("createdCount"));
        assertEquals(0, response.get("skippedCount"));

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).saveAndFlush(captor.capture());
        Invoice persisted = captor.getValue();
        assertEquals(244L, persisted.getUnitId());
        assertEquals("2026-04", persisted.getReferenceMonth());
        assertEquals(Invoice.ChargeType.MULTA, persisted.getChargeType());
        assertEquals(Invoice.BillingType.PIX_AND_BOLETO, persisted.getBillingType());
        assertEquals(new BigDecimal("300.00"), persisted.getAmount());
        assertEquals(Invoice.ApportionmentMode.NONE, persisted.getApportionmentMode());
    }

    @Test
    void launchChargesShouldUseShortAuditEntityIdAndKeepFullLaunchKeyInDetails() {
        String longTitle = "Multa por barulho com descrição operacional bastante detalhada para validação da auditoria";
        String launchKey = "ONE_TIME:MULTA:2026-04:2026-04-10:multa-por-barulho-com-descrição-operacional-bastante-detalhada-para-validação-da-auditoria:SINGLE_UNIT:PER_UNIT";
        Unit unit = unit(244L, 1L, "244", "A");
        when(unitRepository.findByTenantIdAndCondominiumIdOrderByBlockAscNumberAsc("tenant-a", 1L))
                .thenReturn(List.of(unit));
        when(unitRepository.findByTenantIdAndId("tenant-a", 244L)).thenReturn(Optional.of(unit));
        when(invoiceRepository.existsByTenantIdAndUnitIdAndLaunchKey("tenant-a", 244L, launchKey))
                .thenReturn(false);
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(777L);
            return invoice;
        });

        financialService.launchCharges(
                1L,
                "ONE_TIME",
                "SINGLE_UNIT",
                "MULTA",
                "PER_UNIT",
                "PIX_AND_BOLETO",
                new BigDecimal("300.00"),
                longTitle,
                "Descrição longa",
                "2026-04",
                LocalDate.of(2026, 4, 10),
                244L,
                List.of(),
                List.of()
        );

        ArgumentCaptor<Object> entityIdCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> detailsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).log(
                eq(com.example.condo.audit.AuditModule.FINANCIAL),
                eq(com.example.condo.audit.AuditAction.GENERATE_CHARGE_BATCH),
                eq("Invoice"),
                entityIdCaptor.capture(),
                eq(1L),
                anyString(),
                any(),
                any(),
                detailsCaptor.capture()
        );

        String auditEntityId = String.valueOf(entityIdCaptor.getValue());
        assertTrue(auditEntityId.startsWith("batch:"));
        assertTrue(auditEntityId.length() <= 64);

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) detailsCaptor.getValue();
        assertEquals(launchKey, details.get("launchKey"));
        assertTrue(String.valueOf(details.get("launchKey")).length() > 64);
    }

    @Test
    void launchChargesShouldSkipWhenLaunchKeyAlreadyExists() {
        Unit unit = unit(244L, 1L, "244", "A");
        when(unitRepository.findByTenantIdAndCondominiumIdOrderByBlockAscNumberAsc("tenant-a", 1L))
                .thenReturn(List.of(unit));
        when(invoiceRepository.existsByTenantIdAndUnitIdAndLaunchKey("tenant-a", 244L, "ONE_TIME:MULTA:2026-04:2026-04-10:multa-por-barulho:SINGLE_UNIT:PER_UNIT"))
                .thenReturn(true);

        Map<String, Object> response = financialService.launchCharges(
                1L,
                "ONE_TIME",
                "SINGLE_UNIT",
                "MULTA",
                "PER_UNIT",
                "PIX_AND_BOLETO",
                new BigDecimal("300.00"),
                "Multa por barulho",
                null,
                "2026-04",
                LocalDate.of(2026, 4, 10),
                244L,
                List.of(),
                List.of()
        );

        assertEquals(0, response.get("createdCount"));
        assertEquals(1, response.get("skippedCount"));
        verify(invoiceRepository, never()).saveAndFlush(any(Invoice.class));
    }

    @Test
    void launchChargesShouldFailWhenUnitDoesNotBelongToCondominium() {
        Unit unit = unit(10L, 1L, "10", "A");
        when(unitRepository.findByTenantIdAndCondominiumIdOrderByBlockAscNumberAsc("tenant-a", 1L))
                .thenReturn(List.of(unit));

        BusinessException exception = assertThrows(BusinessException.class, () -> financialService.launchCharges(
                1L,
                "ONE_TIME",
                "SINGLE_UNIT",
                "MULTA",
                "PER_UNIT",
                "PIX_AND_BOLETO",
                new BigDecimal("300.00"),
                "Multa por barulho",
                null,
                "2026-04",
                LocalDate.of(2026, 4, 10),
                244L,
                List.of(),
                List.of()
        ));

        assertEquals("A unidade selecionada não pertence ao condomínio informado", exception.getMessage());
    }

    @Test
    void launchChargesShouldSkipWhenDuplicateConstraintHappensDuringSave() {
        Unit unit = unit(244L, 1L, "244", "A");
        when(unitRepository.findByTenantIdAndCondominiumIdOrderByBlockAscNumberAsc("tenant-a", 1L))
                .thenReturn(List.of(unit));
        when(invoiceRepository.existsByTenantIdAndUnitIdAndLaunchKey("tenant-a", 244L, "ONE_TIME:MULTA:2026-04:2026-04-10:multa-por-barulho:SINGLE_UNIT:PER_UNIT"))
                .thenReturn(false);
        when(invoiceRepository.saveAndFlush(any(Invoice.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate",
                        new RuntimeException("duplicate key value violates unique constraint \"uq_invoice_unit_launch_key\"")
                ));

        Map<String, Object> response = financialService.launchCharges(
                1L,
                "ONE_TIME",
                "SINGLE_UNIT",
                "MULTA",
                "PER_UNIT",
                "PIX_AND_BOLETO",
                new BigDecimal("300.00"),
                "Multa por barulho",
                null,
                "2026-04",
                LocalDate.of(2026, 4, 10),
                244L,
                List.of(),
                List.of()
        );

        assertEquals(0, response.get("createdCount"));
        assertEquals(1, response.get("skippedCount"));
        verify(financialNotificationService, never()).logInvoiceNotification(any(Invoice.class), any(FinancialNotification.Type.class), anyString(), anyMap());
    }

    @Test
    void launchChargesShouldFailWhenReferenceMonthIsInvalid() {
        Unit unit = unit(244L, 1L, "244", "A");
        when(unitRepository.findByTenantIdAndCondominiumIdOrderByBlockAscNumberAsc("tenant-a", 1L))
                .thenReturn(List.of(unit));

        BusinessException exception = assertThrows(BusinessException.class, () -> financialService.launchCharges(
                1L,
                "ONE_TIME",
                "SINGLE_UNIT",
                "MULTA",
                "PER_UNIT",
                "PIX_AND_BOLETO",
                new BigDecimal("300.00"),
                "Multa por barulho",
                null,
                "abril/2026",
                LocalDate.of(2026, 4, 10),
                244L,
                List.of(),
                List.of()
        ));

        assertEquals("Competência inválida. Use o formato yyyy-MM", exception.getMessage());
    }

    @Test
    void launchChargesShouldApportionTotalAmountWithoutLosingTheSum() {
        Unit first = unit(244L, 1L, "244", "A");
        Unit second = unit(245L, 1L, "245", "A");
        when(unitRepository.findByTenantIdAndCondominiumIdOrderByBlockAscNumberAsc("tenant-a", 1L))
                .thenReturn(List.of(first, second));
        when(unitRepository.findByTenantIdAndId("tenant-a", 244L)).thenReturn(Optional.of(first));
        when(unitRepository.findByTenantIdAndId("tenant-a", 245L)).thenReturn(Optional.of(second));
        when(invoiceRepository.existsByTenantIdAndUnitIdAndLaunchKey("tenant-a", 244L, "ONE_TIME:EXTRA:2026-04:2026-04-10:rateio-extraordinário:SPECIFIC_UNITS:TOTAL"))
                .thenReturn(false);
        when(invoiceRepository.existsByTenantIdAndUnitIdAndLaunchKey("tenant-a", 245L, "ONE_TIME:EXTRA:2026-04:2026-04-10:rateio-extraordinário:SPECIFIC_UNITS:TOTAL"))
                .thenReturn(false);

        AtomicLong ids = new AtomicLong(900L);
        List<Invoice> savedInvoices = new ArrayList<>();
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(ids.incrementAndGet());
            savedInvoices.add(invoice);
            return invoice;
        });

        Map<String, Object> response = financialService.launchCharges(
                1L,
                "ONE_TIME",
                "SPECIFIC_UNITS",
                "EXTRA",
                "TOTAL",
                "BOLETO",
                new BigDecimal("100.01"),
                "Rateio extraordinário",
                "Despesa extraordinária",
                "2026-04",
                LocalDate.of(2026, 4, 10),
                null,
                List.of(244L, 245L),
                List.of()
        );

        assertEquals(2, response.get("createdCount"));
        assertEquals(0, response.get("skippedCount"));
        assertEquals(2, savedInvoices.size());
        assertEquals(Invoice.ApportionmentMode.EQUAL, savedInvoices.get(0).getApportionmentMode());
        assertTrue(savedInvoices.get(0).getApportionmentGroup() != null && !savedInvoices.get(0).getApportionmentGroup().isBlank());

        BigDecimal total = savedInvoices.stream()
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.01"), total);
        assertEquals(new BigDecimal("50.01"), savedInvoices.get(0).getAmount());
        assertEquals(new BigDecimal("50.00"), savedInvoices.get(1).getAmount());
        verify(invoiceRepository, times(2)).saveAndFlush(any(Invoice.class));
    }

    @Test
    void registerPaymentShouldAccumulatePartialAndThenSettleInvoice() {
        Invoice invoice = invoice(901L, Invoice.Status.PENDING, new BigDecimal("300.00"));
        when(invoiceRepository.findByTenantIdAndId("tenant-a", 901L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findOverdue(any())).thenReturn(List.of());

        Invoice partial = financialService.registerPayment(901L, new BigDecimal("120.00"), "PIX", "Primeira parcela");

        assertEquals(Invoice.Status.PARTIALLY_PAID, partial.getStatus());
        assertEquals(new BigDecimal("120.00"), partial.getPaidAmount());
        assertEquals(Invoice.PaymentMethod.PIX, partial.getPaymentMethod());
        assertNotNull(partial.getPaidAt());
        verify(auditService).log(
                eq(com.example.condo.audit.AuditModule.FINANCIAL),
                eq(com.example.condo.audit.AuditAction.REGISTER_PAYMENT),
                eq("Invoice"),
                eq(901L),
                eq(1L),
                anyString(),
                any(),
                any(),
                any()
        );

        Invoice settled = financialService.registerPayment(901L, null, "TRANSFER", "Saldo final");

        assertEquals(Invoice.Status.PAID, settled.getStatus());
        assertEquals(new BigDecimal("300.00"), settled.getPaidAmount());
        assertEquals(Invoice.PaymentMethod.TRANSFER, settled.getPaymentMethod());
        verify(financialNotificationService).logInvoiceNotification(
                eq(invoice),
                eq(FinancialNotification.Type.PAYMENT_CONFIRMED),
                anyString(),
                anyMap()
        );
    }

    @Test
    void registerPaymentShouldRejectAmountGreaterThanInvoiceTotal() {
        Invoice invoice = invoice(902L, Invoice.Status.PENDING, new BigDecimal("300.00"));
        invoice.setPaidAmount(new BigDecimal("250.00"));
        when(invoiceRepository.findByTenantIdAndId("tenant-a", 902L)).thenReturn(Optional.of(invoice));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                financialService.registerPayment(902L, new BigDecimal("60.00"), "PIX", null)
        );

        assertEquals("Valor pago não pode ser maior que o valor da cobrança", exception.getMessage());
    }

    @Test
    void cancelInvoiceShouldPersistStatusAndAcceptEmptyReason() {
        Invoice invoice = invoice(903L, Invoice.Status.PENDING, new BigDecimal("180.00"));
        when(invoiceRepository.findByTenantIdAndId("tenant-a", 903L)).thenReturn(Optional.of(invoice));

        var response = financialService.cancelInvoice(903L, null);

        assertEquals(Invoice.Status.CANCELLED.name(), response.status());
        assertEquals("MANUAL_CANCELLED", invoice.getExternalStatus());
        assertNotNull(invoice.getCancelledAt());
        verify(auditService).log(
                eq(com.example.condo.audit.AuditModule.FINANCIAL),
                eq(com.example.condo.audit.AuditAction.CANCEL),
                eq("Invoice"),
                eq(903L),
                eq(1L),
                anyString(),
                any(),
                any(),
                eq(Map.of())
        );
    }

    @Test
    void waiveInvoiceShouldPersistStatusAndAuditReasonWhenProvided() {
        Invoice invoice = invoice(904L, Invoice.Status.OVERDUE, new BigDecimal("80.00"));
        when(invoiceRepository.findByTenantIdAndId("tenant-a", 904L)).thenReturn(Optional.of(invoice));

        var response = financialService.waiveInvoice(904L, "Acordo administrativo");

        assertEquals(Invoice.Status.WAIVED.name(), response.status());
        assertEquals("MANUAL_WAIVED", invoice.getExternalStatus());
        ArgumentCaptor<Object> detailsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).log(
                eq(com.example.condo.audit.AuditModule.FINANCIAL),
                eq(com.example.condo.audit.AuditAction.STATUS_CHANGE),
                eq("Invoice"),
                eq(904L),
                eq(1L),
                anyString(),
                any(),
                any(),
                detailsCaptor.capture()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) detailsCaptor.getValue();
        assertEquals("Acordo administrativo", details.get("reason"));
        assertEquals(Invoice.Status.WAIVED.name(), details.get("status"));
    }

    @Test
    void cancelInvoiceShouldBlockPartiallyPaidInvoice() {
        Invoice invoice = invoice(905L, Invoice.Status.PARTIALLY_PAID, new BigDecimal("180.00"));
        invoice.setPaidAmount(new BigDecimal("50.00"));
        when(invoiceRepository.findByTenantIdAndId("tenant-a", 905L)).thenReturn(Optional.of(invoice));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                financialService.cancelInvoice(905L, "Encerrar")
        );

        assertEquals("Não é possível cancelar uma cobrança com pagamento parcial registrado", exception.getMessage());
    }

    @Test
    void registerPayment_shouldBlockIfAlreadyPaidViaGateway() {
        // Invoice paga via webhook (externalStatus = RECEIVED) não pode receber pagamento manual duplicado
        Invoice invoice = invoice(920L, Invoice.Status.PAID, new BigDecimal("300.00"));
        invoice.setPaidAmount(new BigDecimal("300.00"));
        invoice.setExternalProvider(Invoice.Provider.ASAAS);
        invoice.setExternalChargeId("pay_abc");
        invoice.setExternalStatus("RECEIVED");
        when(invoiceRepository.findByTenantIdAndId("tenant-a", 920L)).thenReturn(Optional.of(invoice));

        // Tentar registrar pagamento manual em invoice já paga deve lançar BusinessException
        var exception = assertThrows(com.example.condo.exception.BusinessException.class, () ->
                financialService.registerPayment(920L, new java.math.BigDecimal("300.00"), "PIX", "Duplicata")
        );

        // A mensagem deve indicar que a invoice já está paga
        assertNotNull(exception.getMessage());
        assertTrue(
                exception.getMessage().toLowerCase().contains("pag") || exception.getMessage().toLowerCase().contains("paid"),
                "Mensagem deve indicar que a invoice já está paga: " + exception.getMessage()
        );
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void launchCharges_shouldValidateUnitOwnership_andRejectForeignUnits() {
        // Unidade 244 pertence ao condomínio 1, mas não ao condomínio 2
        Unit unitFromAnotherCondo = unit(999L, 2L, "99", "Z"); // condominiumId = 2 (diferente do contexto = 1)
        when(unitRepository.findByTenantIdAndCondominiumIdOrderByBlockAscNumberAsc("tenant-a", 1L))
                .thenReturn(List.of(unit(100L, 1L, "100", "A"))); // condo 1 tem unidade 100, não 999

        var exception = assertThrows(com.example.condo.exception.BusinessException.class, () ->
                financialService.launchCharges(
                        1L,
                        "ONE_TIME",
                        "SINGLE_UNIT",
                        "MULTA",
                        "PER_UNIT",
                        "BOLETO",
                        new BigDecimal("200.00"),
                        "Multa",
                        null,
                        "2026-04",
                        LocalDate.of(2026, 4, 15),
                        999L,      // unitId que não pertence ao condomínio 1
                        List.of(),
                        List.of()
                )
        );

        assertEquals("A unidade selecionada não pertence ao condomínio informado", exception.getMessage());
        verify(invoiceRepository, never()).saveAndFlush(any(Invoice.class));
    }

    private Unit unit(Long id, Long condominiumId, String number, String block) {
        Unit unit = new Unit();
        unit.setId(id);
        unit.setTenantId("tenant-a");
        unit.setCondominiumId(condominiumId);
        unit.setCode(number + (block == null ? "" : "-" + block));
        unit.setNumber(number);
        unit.setBlock(block);
        return unit;
    }

    private Invoice invoice(Long id, Invoice.Status status, BigDecimal amount) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setTenantId("tenant-a");
        invoice.setCondominiumId(1L);
        invoice.setUnitId(244L);
        invoice.setReferenceMonth("2026-04");
        invoice.setChargeType(Invoice.ChargeType.MULTA);
        invoice.setTitle("Cobrança manual");
        invoice.setLaunchKey("MANUAL:" + id);
        invoice.setAmount(amount);
        invoice.setDueDate(LocalDate.of(2026, 4, 10));
        invoice.setStatus(status);
        invoice.setCreatedAt(java.time.Instant.now());
        return invoice;
    }
}
