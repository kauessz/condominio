package com.example.condo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades configuráveis do CondoHub.
 *
 * Prefixo: condohub
 *
 * Exemplo em application.yml:
 *   condohub:
 *     visitor:
 *       pending-timeout-minutes: 15
 *       expiration-check-interval-ms: 60000
 */
@Component
@ConfigurationProperties(prefix = "condohub")
public class CondoHubProperties {

    private Visitor visitor = new Visitor();
    private Financial financial = new Financial();

    public Visitor getVisitor() {
        return visitor;
    }

    public void setVisitor(Visitor visitor) {
        this.visitor = visitor;
    }

    public Financial getFinancial() {
        return financial;
    }

    public void setFinancial(Financial financial) {
        this.financial = financial;
    }

    public static class Visitor {

        /**
         * Timeout global (em minutos) para visitas PENDING.
         * Usado quando não há timeout configurado por condomínio.
         */
        private int pendingTimeoutMinutes = 15;

        /**
         * Intervalo (em ms) entre cada execução do job de expiração.
         * Padrão: 60 segundos.
         */
        private long expirationCheckIntervalMs = 60_000L;

        public int getPendingTimeoutMinutes() {
            return pendingTimeoutMinutes;
        }

        public void setPendingTimeoutMinutes(int pendingTimeoutMinutes) {
            this.pendingTimeoutMinutes = pendingTimeoutMinutes;
        }

        public long getExpirationCheckIntervalMs() {
            return expirationCheckIntervalMs;
        }

        public void setExpirationCheckIntervalMs(long expirationCheckIntervalMs) {
            this.expirationCheckIntervalMs = expirationCheckIntervalMs;
        }
    }

    public static class Financial {

        private Asaas asaas = new Asaas();
        private int dueSoonDays = 3;

        public Asaas getAsaas() {
            return asaas;
        }

        public void setAsaas(Asaas asaas) {
            this.asaas = asaas;
        }

        public int getDueSoonDays() {
            return dueSoonDays;
        }

        public void setDueSoonDays(int dueSoonDays) {
            this.dueSoonDays = dueSoonDays;
        }
    }

    public static class Asaas {

        private boolean enabled = false;
        private String apiKey;
        private String baseUrl = "https://api-sandbox.asaas.com";
        private String webhookToken;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getWebhookToken() {
            return webhookToken;
        }

        public void setWebhookToken(String webhookToken) {
            this.webhookToken = webhookToken;
        }
    }
}
