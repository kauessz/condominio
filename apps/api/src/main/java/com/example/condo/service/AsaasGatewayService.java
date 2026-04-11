package com.example.condo.service;

import com.example.condo.config.CondoHubProperties;
import com.example.condo.entity.Invoice;
import com.example.condo.entity.Resident;
import com.example.condo.entity.Unit;
import com.example.condo.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço de integração com o gateway Asaas.
 *
 * Suporta API key por condomínio via variável de ambiente ASAAS_API_KEY_CONDO_{id}
 * com fallback para a chave global ASAAS_API_KEY.
 *
 * Timeout padrão: conexão 10s, leitura 30s.
 */
@Service
public class AsaasGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AsaasGatewayService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** Timeout de conexão padrão (ms). Pode ser sobrescrito via ASAAS_CONNECT_TIMEOUT_MS. */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    /** Timeout de leitura padrão (ms). Pode ser sobrescrito via ASAAS_READ_TIMEOUT_MS. */
    private static final int DEFAULT_READ_TIMEOUT_MS = 30_000;

    private final CondoHubProperties properties;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    // =====================================================================
    // DTOs tipados para respostas do Asaas
    // =====================================================================

    /**
     * Resposta de cliente do Asaas.
     * Campos relevantes retornados por GET /v3/customers e POST /v3/customers.
     */
    public record AsaasCustomerResponse(
        String id,
        String name,
        String cpfCnpj,
        String email
    ) {}

    /**
     * Resposta de cobrança do Asaas.
     * Campos relevantes retornados por GET/POST /v3/payments.
     */
    public record AsaasChargeResponse(
        String id,
        String status,
        String billingType,
        BigDecimal value,
        String dueDate,
        String description,
        String bankSlipUrl,       // URL do boleto
        String nossoNumero,       // código de barras / número do boleto
        String pixQrCode,         // QR code base64 (via pixQrCode endpoint)
        String pixQrCodeBase64    // alias alternativo retornado pelo endpoint pixQrCode
    ) {}

    /**
     * Resultado completo da criação de cobrança no Asaas.
     * Retornado por {@link #createCharge}.
     */
    public record GatewayChargeResult(
        String customerId,
        String chargeId,
        String invoiceNumber,
        String status,
        String boletoUrl,
        String pixQrCode,
        String pixCopyPaste,
        Map<String, Object> rawPayload
    ) {}

    // =====================================================================
    // Construtor
    // =====================================================================

    public AsaasGatewayService(CondoHubProperties properties, ObjectMapper objectMapper, Environment environment) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    // =====================================================================
    // Resolução de API Key
    // =====================================================================

    /**
     * Resolve a API Key do Asaas para um condomínio específico.
     *
     * Ordem de resolução:
     *   1. Variável de ambiente ASAAS_API_KEY_CONDO_{condominiumId}
     *   2. Variável global ASAAS_API_KEY (condohub.financial.asaas.api-key)
     *
     * Uso no Railway: defina ASAAS_API_KEY_CONDO_1=$aact_... para o condo 1,
     * ou use ASAAS_API_KEY para todos os condomínios compartilharem a mesma conta.
     */
    public String resolveApiKeyForCondo(Long condominiumId) {
        if (condominiumId != null) {
            String condoKey = environment.getProperty("ASAAS_API_KEY_CONDO_" + condominiumId);
            if (condoKey != null && !condoKey.isBlank()) {
                log.debug("Usando API key por condomínio para condoId={}", condominiumId);
                return condoKey.trim();
            }
        }
        String globalKey = properties.getFinancial().getAsaas().getApiKey();
        if (globalKey == null || globalKey.isBlank()) {
            throw new BusinessException(
                "API Key do Asaas não configurada. " +
                "Configure ASAAS_API_KEY (global) ou ASAAS_API_KEY_CONDO_" + condominiumId + " nas variáveis de ambiente."
            );
        }
        return globalKey;
    }

    /**
     * Verifica se há uma API key configurada para o condomínio (per-condo ou global).
     * Não lança exceção — retorna false se nenhuma chave estiver configurada.
     */
    public boolean isApiKeyConfiguredForCondo(Long condominiumId) {
        if (condominiumId != null) {
            String condoKey = environment.getProperty("ASAAS_API_KEY_CONDO_" + condominiumId);
            if (condoKey != null && !condoKey.isBlank()) return true;
        }
        String globalKey = properties.getFinancial().getAsaas().getApiKey();
        return globalKey != null && !globalKey.isBlank();
    }

    // =====================================================================
    // Operações de cliente (Customer)
    // =====================================================================

    /**
     * Busca um cliente no Asaas pelo CPF/CNPJ.
     * Retorna empty se não encontrado ou se CPF for nulo/vazio.
     *
     * @param cpfCnpj       CPF ou CNPJ (pode conter pontuação — será limpado)
     * @param condominiumId ID do condomínio para resolver a API key
     */
    public Optional<AsaasCustomerResponse> findCustomerByCpf(String cpfCnpj, Long condominiumId) {
        if (cpfCnpj == null || cpfCnpj.isBlank()) {
            return Optional.empty();
        }
        String digits = cpfCnpj.replaceAll("\\D", "");
        if (digits.isBlank()) return Optional.empty();

        RestClient client = buildClient(condominiumId);
        try {
            Map<String, Object> response = get(client, "/v3/customers?cpfCnpj=" + digits);
            Object dataRaw = response.get("data");
            if (dataRaw instanceof List<?> list && !list.isEmpty()) {
                Map<String, Object> first = asMap(list.get(0));
                return Optional.of(new AsaasCustomerResponse(
                    text(first.get("id")),
                    text(first.get("name")),
                    text(first.get("cpfCnpj")),
                    text(first.get("email"))
                ));
            }
        } catch (BusinessException ex) {
            log.warn("Erro ao buscar cliente por CPF no Asaas — cpfCnpj={}...: {}", digits.substring(0, Math.min(4, digits.length())), ex.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Cria um cliente no Asaas (com CPF/CNPJ).
     * Use {@link #findOrCreateCustomer} para o fluxo completo.
     *
     * @param name          Nome do cliente
     * @param cpfCnpj       CPF/CNPJ (pode ser nulo)
     * @param email         E-mail (pode ser nulo)
     * @param condominiumId ID do condomínio para resolver a API key
     */
    public AsaasCustomerResponse createCustomer(String name, String cpfCnpj, String email, Long condominiumId) {
        RestClient client = buildClient(condominiumId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        putIfText(payload, "email", email);
        putIfText(payload, "cpfCnpj", cpfCnpj != null ? cpfCnpj.replaceAll("\\D", "") : null);
        Map<String, Object> response = post(client, "/v3/customers", payload);
        String id = text(response.get("id"));
        if (id == null || id.isBlank()) {
            throw new BusinessException("Asaas não retornou o identificador do cliente criado");
        }
        return new AsaasCustomerResponse(id, text(response.get("name")), text(response.get("cpfCnpj")), text(response.get("email")));
    }

    /**
     * Cria um cliente no Asaas com nome, telefone e e-mail (sem CPF).
     * Usado internamente por {@link #findOrCreateCustomer}.
     */
    private AsaasCustomerResponse createCustomerWithPhone(String name, String phone, String email, Long condominiumId) {
        RestClient client = buildClient(condominiumId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        putIfText(payload, "email", email);
        if (phone != null) {
            String digits = phone.replaceAll("\\D", "");
            if (!digits.isBlank()) payload.put("phone", digits);
        }
        Map<String, Object> response = post(client, "/v3/customers", payload);
        String id = text(response.get("id"));
        if (id == null || id.isBlank()) {
            throw new BusinessException("Asaas não retornou o identificador do cliente criado");
        }
        return new AsaasCustomerResponse(id, text(response.get("name")), text(response.get("cpfCnpj")), text(response.get("email")));
    }

    /**
     * Busca ou cria um cliente no Asaas para o morador informado.
     *
     * Ordem de resolução:
     *   1. Se a invoice já tem externalCustomerId → retorna diretamente
     *   2. Verifica se existe um cliente anterior para a mesma unidade/tenant (via InvoiceRepository — feito no FinancialService)
     *   3. Cria novo cliente com nome, e-mail e telefone do morador
     *
     * Nota: a entidade {@link Resident} não possui campo CPF. Caso seja adicionado no futuro,
     * adicionar chamada a {@link #findCustomerByCpf} antes da criação para evitar duplicatas.
     *
     * @param resident      Morador (pode ser null — nesse caso o cliente é criado com nome genérico)
     * @param unit          Unidade (para construir nome genérico quando resident == null)
     * @param invoice       Invoice (para verificar externalCustomerId já salvo)
     * @param condominiumId ID do condomínio para resolver a API key
     * @return Asaas customer ID
     */
    public String findOrCreateCustomer(Resident resident, Unit unit, Invoice invoice, Long condominiumId) {
        // 1. Já temos o customerId salvo na invoice
        if (invoice.getExternalCustomerId() != null && !invoice.getExternalCustomerId().isBlank()) {
            return invoice.getExternalCustomerId();
        }
        String name  = resident != null ? resident.getName()  : "Unidade " + safeUnitLabel(unit, invoice);
        String email = resident != null ? resident.getEmail() : null;
        String phone = resident != null ? resident.getPhone() : null;
        String cpf   = resident != null ? resident.getCpf()   : null;

        // 2. Se morador tem CPF, buscar cliente existente por CPF antes de criar
        if (cpf != null && !cpf.isBlank()) {
            Optional<AsaasCustomerResponse> existing = findCustomerByCpf(cpf, condominiumId);
            if (existing.isPresent()) {
                log.info("Cliente Asaas reutilizado por CPF — asaasId={} condoId={}", existing.get().id(), condominiumId);
                return existing.get().id();
            }
            // Criar com CPF para permitir dedup futuro
            AsaasCustomerResponse created = createCustomer(name, cpf, email, condominiumId);
            log.info("Cliente criado no Asaas (com CPF) — asaasId={} condoId={}", created.id(), condominiumId);
            return created.id();
        }

        // 3. Sem CPF — criar usando phone/email
        AsaasCustomerResponse created = createCustomerWithPhone(name, phone, email, condominiumId);
        log.info("Cliente criado no Asaas (sem CPF) — asaasId={} condoId={}", created.id(), condominiumId);
        return created.id();
    }

    // =====================================================================
    // Operações de cobrança (Payment / Charge)
    // =====================================================================

    /**
     * Cria uma cobrança no Asaas para a invoice informada.
     * Internamente usa {@link #findOrCreateCustomer} para evitar duplicatas de cliente.
     *
     * @param invoice   Invoice com dados da cobrança
     * @param resident  Morador titular da unidade (pode ser null)
     * @param unit      Unidade (para descrição e nome genérico)
     */
    public GatewayChargeResult createCharge(Invoice invoice, Resident resident, Unit unit) {
        CondoHubProperties.Asaas cfg = properties.getFinancial().getAsaas();
        if (!cfg.isEnabled()) {
            throw new BusinessException("Integração Asaas não está habilitada no ambiente (ASAAS_ENABLED=false)");
        }

        Long condominiumId = invoice.getCondominiumId();

        String customerId = findOrCreateCustomer(resident, unit, invoice, condominiumId);
        RestClient client = buildClient(condominiumId);

        Map<String, Object> paymentPayload = new LinkedHashMap<>();
        paymentPayload.put("customer", customerId);
        paymentPayload.put("billingType", mapBillingType(invoice.getBillingType()));
        paymentPayload.put("value", invoice.getAmount());
        paymentPayload.put("dueDate", invoice.getDueDate().toString());
        paymentPayload.put("description", buildDescription(invoice, unit));
        paymentPayload.put("externalReference", invoice.getExternalReference());

        Map<String, Object> payment = post(client, "/v3/payments", paymentPayload);
        String paymentId = text(payment.get("id"));
        Map<String, Object> pixPayload = Map.of();
        if (invoice.getBillingType() == Invoice.BillingType.PIX && paymentId != null) {
            pixPayload = get(client, "/v3/payments/" + paymentId + "/pixQrCode");
        }

        return new GatewayChargeResult(
            customerId,
            paymentId,
            text(payment.get("invoiceNumber")),
            text(payment.get("status")),
            text(payment.get("bankSlipUrl")),
            text(pixPayload.get("encodedImage")),
            text(pixPayload.get("payload")),
            payment
        );
    }

    /**
     * Consulta o status atual de uma cobrança no Asaas.
     * Usado pelo job de reconciliação para detectar pagamentos recebidos sem webhook.
     *
     * @param externalChargeId  ID da cobrança no Asaas
     * @param condominiumId     ID do condomínio para resolver a API key
     * @return Dados atuais da cobrança ou empty se não encontrada (404)
     */
    public Optional<AsaasChargeResponse> getCharge(String externalChargeId, Long condominiumId) {
        if (externalChargeId == null || externalChargeId.isBlank()) return Optional.empty();
        CondoHubProperties.Asaas cfg = properties.getFinancial().getAsaas();
        if (!cfg.isEnabled()) return Optional.empty();

        RestClient client = buildClient(condominiumId);
        try {
            Map<String, Object> response = get(client, "/v3/payments/" + externalChargeId);
            return Optional.of(mapToChargeResponse(response));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("Cobrança não encontrada no Asaas — externalChargeId={}", externalChargeId);
                return Optional.empty();
            }
            throw new BusinessException("Falha ao consultar cobrança no Asaas: " + sanitizeGatewayMessage(e));
        }
    }

    /**
     * Cancela uma cobrança no Asaas.
     * Chamado quando uma invoice com externalChargeId é cancelada ou dispensada.
     *
     * @param externalChargeId  ID da cobrança no Asaas
     * @param condominiumId     ID do condomínio para resolver a API key
     * @throws BusinessException se a chamada ao Asaas falhar por motivo irrecuperável
     */
    public void cancelCharge(String externalChargeId, Long condominiumId) {
        CondoHubProperties.Asaas cfg = properties.getFinancial().getAsaas();
        if (!cfg.isEnabled()) return; // noop se Asaas desabilitado globalmente

        RestClient client = buildClient(condominiumId);
        try {
            client.delete()
                .uri("/v3/payments/" + externalChargeId)
                .retrieve()
                .toBodilessEntity();
            log.info("Cobrança cancelada no Asaas — externalChargeId={} condoId={}", externalChargeId, condominiumId);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                log.warn("Tentativa de cancelamento de cobrança não encontrada no Asaas — externalChargeId={}", externalChargeId);
                return;
            }
            log.error("Falha ao cancelar cobrança no Asaas — externalChargeId={} status={}", externalChargeId, status);
            throw new BusinessException("Falha ao cancelar cobrança no Asaas: " + sanitizeGatewayMessage(e));
        }
    }

    // =====================================================================
    // Validação de Webhook
    // =====================================================================

    public boolean validateWebhookToken(String token) {
        String expected = properties.getFinancial().getAsaas().getWebhookToken();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(token);
    }

    public boolean isWebhookTokenConfigured() {
        String expected = properties.getFinancial().getAsaas().getWebhookToken();
        return expected != null && !expected.isBlank();
    }

    // =====================================================================
    // Utilitários públicos
    // =====================================================================

    public Map<String, Object> asMap(Object raw) {
        return objectMapper.convertValue(raw, MAP_TYPE);
    }

    // =====================================================================
    // Métodos privados internos
    // =====================================================================

    /**
     * Constrói um RestClient configurado com a API key do condomínio e timeouts padrão.
     */
    private RestClient buildClient(Long condominiumId) {
        String apiKey = resolveApiKeyForCondo(condominiumId);
        String baseUrl = trimTrailingSlash(properties.getFinancial().getAsaas().getBaseUrl());

        int connectMs = resolveIntEnv("ASAAS_CONNECT_TIMEOUT_MS", DEFAULT_CONNECT_TIMEOUT_MS);
        int readMs    = resolveIntEnv("ASAAS_READ_TIMEOUT_MS",    DEFAULT_READ_TIMEOUT_MS);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectMs));
        factory.setReadTimeout(Duration.ofMillis(readMs));

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader("access_token", apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    private Map<String, Object> post(RestClient client, String path, Map<String, Object> payload) {
        try {
            Object body = client.post().uri(path).body(payload).retrieve().body(Object.class);
            return asMap(body);
        } catch (RestClientResponseException e) {
            log.warn("Asaas POST {} falhou — status={}", path, e.getStatusCode().value());
            throw new BusinessException("Falha ao integrar com Asaas: " + sanitizeGatewayMessage(e));
        }
    }

    private Map<String, Object> get(RestClient client, String path) {
        try {
            Object body = client.get().uri(path).retrieve().body(Object.class);
            return asMap(body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw e; // lançar direto para o chamador tratar 404
            }
            log.warn("Asaas GET {} falhou — status={}", path, e.getStatusCode().value());
            throw new BusinessException("Falha ao consultar dados no Asaas: " + sanitizeGatewayMessage(e));
        }
    }

    private AsaasChargeResponse mapToChargeResponse(Map<String, Object> m) {
        return new AsaasChargeResponse(
            text(m.get("id")),
            text(m.get("status")),
            text(m.get("billingType")),
            asBigDecimal(m.get("value")),
            text(m.get("dueDate")),
            text(m.get("description")),
            text(m.get("bankSlipUrl")),
            text(m.get("nossoNumero")),
            text(m.get("pixQrCode")),
            text(m.get("pixQrCodeBase64"))
        );
    }

    private String mapBillingType(Invoice.BillingType billingType) {
        if (billingType == null) return "UNDEFINED";
        return switch (billingType) {
            case PIX -> "PIX";
            case BOLETO -> "BOLETO";
            case PIX_AND_BOLETO, UNDEFINED -> "UNDEFINED";
        };
    }

    private String buildDescription(Invoice invoice, Unit unit) {
        String unitLabel = safeUnitLabel(unit, invoice);
        return (invoice.getTitle() != null && !invoice.getTitle().isBlank() ? invoice.getTitle() : "Cobrança")
            + " - " + unitLabel
            + " - " + invoice.getReferenceMonth();
    }

    private String safeUnitLabel(Unit unit, Invoice invoice) {
        if (unit == null) return "Unidade #" + invoice.getUnitId();
        String base = unit.getNumber() != null && !unit.getNumber().isBlank() ? unit.getNumber() : unit.getCode();
        return unit.getBlock() != null && !unit.getBlock().isBlank()
            ? "Unidade " + base + " Bloco " + unit.getBlock()
            : "Unidade " + base;
    }

    private String sanitizeGatewayMessage(RestClientResponseException e) {
        if (e.getResponseBodyAsString() == null || e.getResponseBodyAsString().isBlank()) {
            return "HTTP " + e.getStatusCode().value();
        }
        try {
            Map<String, Object> body = objectMapper.readValue(e.getResponseBodyAsString(), MAP_TYPE);
            Object errors = body.get("errors");
            if (errors instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    Map<String, Object> error = asMap(item);
                    String description = text(error.get("description"));
                    if (description != null && !description.isBlank()) return description;
                }
            }
        } catch (Exception ignored) {
            // fall-through
        }
        return "HTTP " + e.getStatusCode().value();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "https://api-sandbox.asaas.com";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String text(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(String.valueOf(value)); }
        catch (Exception e) { return null; }
    }

    private int resolveIntEnv(String envVar, int defaultValue) {
        String raw = environment.getProperty(envVar);
        if (raw != null && !raw.isBlank()) {
            try { return Integer.parseInt(raw.trim()); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private void putIfText(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isBlank()) body.put(key, value.trim());
    }
}
