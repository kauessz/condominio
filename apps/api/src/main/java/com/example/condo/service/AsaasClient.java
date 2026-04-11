package com.example.condo.service;

import com.example.condo.config.FinancialGatewayProperties;
import com.example.condo.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AsaasClient {

    private static final Logger log = LoggerFactory.getLogger(AsaasClient.class);

    private final FinancialGatewayProperties properties;
    private final RestClient restClient;

    public AsaasClient(FinancialGatewayProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getAsaas().getBaseUrl())
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                setConnectTimeout(Duration.ofMillis(properties.getAsaas().getConnectTimeoutMs()));
                setReadTimeout(Duration.ofMillis(properties.getAsaas().getReadTimeoutMs()));
            }})
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public boolean isEnabled() {
        return properties.getAsaas().isEnabled() && StringUtils.hasText(properties.getAsaas().getApiKey());
    }

    public JsonNode createCustomer(String name, String email, String phone, String cpfCnpj) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        putIfText(body, "email", email);
        putIfText(body, "phone", digitsOnly(phone));
        putIfText(body, "cpfCnpj", digitsOnly(cpfCnpj));
        return post("/v3/customers", body);
    }

    public JsonNode createCharge(
        String customerId,
        String billingType,
        BigDecimal value,
        LocalDate dueDate,
        String description,
        String externalReference
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customer", customerId);
        body.put("billingType", billingType);
        body.put("value", value);
        body.put("dueDate", dueDate.toString());
        putIfText(body, "description", description);
        putIfText(body, "externalReference", externalReference);
        return post("/v3/payments", body);
    }

    public JsonNode getPixQrCode(String chargeId) {
        return get("/v3/payments/" + chargeId + "/pixQrCode");
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return exchange(() -> restClient.post()
            .uri(path)
            .header("access_token", properties.getAsaas().getApiKey())
            .body(body)
            .retrieve()
            .body(JsonNode.class));
    }

    private JsonNode get(String path) {
        return exchange(() -> restClient.get()
            .uri(path)
            .header("access_token", properties.getAsaas().getApiKey())
            .retrieve()
            .body(JsonNode.class));
    }

    private JsonNode exchange(RequestSupplier supplier) {
        if (!isEnabled()) {
            throw new BusinessException("Integração Asaas não está configurada");
        }
        try {
            return supplier.get();
        } catch (HttpStatusCodeException ex) {
            log.warn("Asaas request failed with status {}", ex.getStatusCode().value());
            throw new BusinessException("Falha ao integrar com o gateway de cobrança");
        } catch (Exception ex) {
            throw new BusinessException("Falha ao comunicar com o gateway de cobrança");
        }
    }

    private void putIfText(Map<String, Object> body, String key, String value) {
        if (StringUtils.hasText(value)) {
            body.put(key, value.trim());
        }
    }

    private String digitsOnly(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    @FunctionalInterface
    private interface RequestSupplier {
        JsonNode get();
    }
}
