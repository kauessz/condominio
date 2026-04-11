-- Migration: adiciona asaas_webhook_token à financial_config
-- Necessário para validação dos webhooks do Asaas por condomínio

ALTER TABLE financial_config
    ADD COLUMN IF NOT EXISTS asaas_webhook_token VARCHAR(255);

COMMENT ON COLUMN financial_config.asaas_webhook_token
    IS 'Token configurado no painel Asaas para validação de autenticidade dos webhooks';
