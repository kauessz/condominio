package com.example.condo.service;

import com.example.condo.config.CondoHubProperties;
import com.example.condo.entity.Invoice;
import com.example.condo.entity.Resident;
import com.example.condo.entity.Unit;
import com.example.condo.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para {@link AsaasGatewayService}.
 *
 * Não fazem chamadas HTTP reais — testam a lógica de resolução de API key,
 * validação de configuração e comportamento defensivo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsaasGatewayServiceTest {

    @Mock
    private Environment environment;

    private CondoHubProperties properties;
    private AsaasGatewayService service;

    @BeforeEach
    void setUp() {
        properties = new CondoHubProperties();
        // ObjectMapper real — não precisa de mock para este teste
        service = new AsaasGatewayService(properties, new ObjectMapper(), environment);
    }

    // ===== resolveApiKeyForCondo =====

    @Test
    void resolveApiKey_shouldReturnCondoSpecificKey_whenEnvVarExists() {
        when(environment.getProperty("ASAAS_API_KEY_CONDO_42"))
            .thenReturn("$aact_condo_42_specific_key");

        String key = service.resolveApiKeyForCondo(42L);

        assertEquals("$aact_condo_42_specific_key", key);
        verify(environment).getProperty("ASAAS_API_KEY_CONDO_42");
    }

    @Test
    void resolveApiKey_shouldFallbackToGlobal_whenCondoKeyMissing() {
        when(environment.getProperty("ASAAS_API_KEY_CONDO_7")).thenReturn(null);
        properties.getFinancial().getAsaas().setApiKey("$aact_global_key");

        String key = service.resolveApiKeyForCondo(7L);

        assertEquals("$aact_global_key", key);
    }

    @Test
    void resolveApiKey_shouldFallbackToGlobal_whenCondoKeyIsBlank() {
        when(environment.getProperty("ASAAS_API_KEY_CONDO_7")).thenReturn("   ");
        properties.getFinancial().getAsaas().setApiKey("$aact_global_key");

        String key = service.resolveApiKeyForCondo(7L);

        assertEquals("$aact_global_key", key);
    }

    @Test
    void resolveApiKey_shouldThrow_whenNeitherKeyExists() {
        when(environment.getProperty("ASAAS_API_KEY_CONDO_5")).thenReturn(null);
        // globalKey fica null (default de CondoHubProperties)

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.resolveApiKeyForCondo(5L));

        assertTrue(ex.getMessage().contains("ASAAS_API_KEY"),
            "Mensagem deve mencionar a variável de ambiente");
    }

    @Test
    void resolveApiKey_shouldThrow_whenCondominiumIdIsNullAndGlobalKeyMissing() {
        // sem condominiumId e sem globalKey

        assertThrows(BusinessException.class, () -> service.resolveApiKeyForCondo(null));
    }

    // ===== isApiKeyConfiguredForCondo =====

    @Test
    void isApiKeyConfigured_shouldReturnTrue_whenCondoKeyExists() {
        when(environment.getProperty("ASAAS_API_KEY_CONDO_1")).thenReturn("$aact_key");

        assertTrue(service.isApiKeyConfiguredForCondo(1L));
    }

    @Test
    void isApiKeyConfigured_shouldReturnTrue_whenOnlyGlobalKeyExists() {
        when(environment.getProperty("ASAAS_API_KEY_CONDO_1")).thenReturn(null);
        properties.getFinancial().getAsaas().setApiKey("$aact_global");

        assertTrue(service.isApiKeyConfiguredForCondo(1L));
    }

    @Test
    void isApiKeyConfigured_shouldReturnFalse_whenNeitherKeyExists() {
        when(environment.getProperty("ASAAS_API_KEY_CONDO_1")).thenReturn(null);
        // globalKey = null

        assertFalse(service.isApiKeyConfiguredForCondo(1L));
    }

    // ===== cancelCharge — comportamento sem chamada HTTP =====

    @Test
    void cancelCharge_shouldBeNoop_whenAsaasDisabled() {
        // ASAAS_ENABLED=false (padrão)
        properties.getFinancial().getAsaas().setEnabled(false);

        // Não deve lançar exceção — e não vai tentar resolver API key
        assertDoesNotThrow(() -> service.cancelCharge("pay_abc123", 1L));
        verifyNoInteractions(environment); // nenhuma consulta de key necessária
    }

    @Test
    void cancelCharge_shouldThrow_whenApiKeyMissing() {
        properties.getFinancial().getAsaas().setEnabled(true);
        when(environment.getProperty("ASAAS_API_KEY_CONDO_1")).thenReturn(null);
        // globalKey = null → lançará BusinessException ao tentar resolver a key

        assertThrows(BusinessException.class, () -> service.cancelCharge("pay_abc123", 1L));
    }

    // ===== getCharge — comportamento sem chamada HTTP =====

    @Test
    void getCharge_shouldReturnEmpty_whenAsaasDisabled() {
        properties.getFinancial().getAsaas().setEnabled(false);

        var result = service.getCharge("pay_xyz", 1L);

        assertTrue(result.isEmpty());
        verifyNoInteractions(environment);
    }

    @Test
    void getCharge_shouldReturnEmpty_whenExternalChargeIdIsBlank() {
        var result = service.getCharge("", 1L);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCharge_shouldReturnEmpty_whenExternalChargeIdIsNull() {
        var result = service.getCharge(null, 1L);
        assertTrue(result.isEmpty());
    }

    // ===== validateWebhookToken =====

    @Test
    void validateWebhookToken_shouldReturnTrue_whenTokenMatches() {
        properties.getFinancial().getAsaas().setWebhookToken("my-secret");

        assertTrue(service.validateWebhookToken("my-secret"));
    }

    @Test
    void validateWebhookToken_shouldReturnFalse_whenTokenDoesNotMatch() {
        properties.getFinancial().getAsaas().setWebhookToken("my-secret");

        assertFalse(service.validateWebhookToken("wrong-token"));
    }

    @Test
    void validateWebhookToken_shouldReturnTrue_whenTokenNotConfigured() {
        // Sem token configurado: qualquer chamada é aceita
        properties.getFinancial().getAsaas().setWebhookToken(null);

        assertTrue(service.validateWebhookToken("qualquer-coisa"));
    }

    @Test
    void validateWebhookToken_shouldReturnTrue_whenTokenIsBlank() {
        properties.getFinancial().getAsaas().setWebhookToken("");

        assertTrue(service.validateWebhookToken("qualquer-coisa"));
    }

    @Test
    void isWebhookTokenConfigured_shouldReturnFalse_whenTokenIsNull() {
        properties.getFinancial().getAsaas().setWebhookToken(null);
        assertFalse(service.isWebhookTokenConfigured());
    }

    @Test
    void isWebhookTokenConfigured_shouldReturnTrue_whenTokenIsSet() {
        properties.getFinancial().getAsaas().setWebhookToken("token-real");
        assertTrue(service.isWebhookTokenConfigured());
    }

    // ===== findOrCreateCustomer — sem chamada HTTP (cobre o caminho externalCustomerId já preenchido) =====

    @Test
    void findOrCreateCustomer_shouldReturnExistingId_whenInvoiceAlreadyHasCustomerId() {
        Invoice invoice = invoice(1L);
        invoice.setExternalCustomerId("cus_existente_123");

        // O método retorna o ID sem tentar criar — não faz chamada HTTP
        // Configuramos a chave para que não lance antes de retornar
        when(environment.getProperty("ASAAS_API_KEY_CONDO_1")).thenReturn("$aact_key");

        String result = service.findOrCreateCustomer(null, null, invoice, 1L);

        assertEquals("cus_existente_123", result);
        // Sem interação de network — o ID veio da própria invoice
    }

    // ===== Helpers =====

    private Invoice invoice(Long id) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setTenantId("tenant-a");
        invoice.setCondominiumId(1L);
        invoice.setUnitId(100L);
        invoice.setReferenceMonth("2026-04");
        invoice.setChargeType(Invoice.ChargeType.CONDOMINIO);
        invoice.setTitle("Cobrança de teste");
        invoice.setLaunchKey("TEST:" + id);
        invoice.setAmount(new BigDecimal("300.00"));
        invoice.setDueDate(LocalDate.of(2026, 4, 30));
        invoice.setStatus(Invoice.Status.PENDING);
        invoice.setCreatedAt(Instant.now());
        return invoice;
    }
}
