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

    public Visitor getVisitor() {
        return visitor;
    }

    public void setVisitor(Visitor visitor) {
        this.visitor = visitor;
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
}
