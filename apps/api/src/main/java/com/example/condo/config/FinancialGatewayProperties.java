package com.example.condo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "condohub.financial")
public class FinancialGatewayProperties {

    private Asaas asaas = new Asaas();

    public Asaas getAsaas() {
        return asaas;
    }

    public void setAsaas(Asaas asaas) {
        this.asaas = asaas;
    }

    public static class Asaas {
        private boolean enabled;
        private String baseUrl = "https://api-sandbox.asaas.com";
        private String apiKey;
        private String webhookToken;
        private boolean sandbox = true;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getWebhookToken() { return webhookToken; }
        public void setWebhookToken(String webhookToken) { this.webhookToken = webhookToken; }

        public boolean isSandbox() { return sandbox; }
        public void setSandbox(boolean sandbox) { this.sandbox = sandbox; }

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }
}
