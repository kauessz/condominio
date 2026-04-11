alter table if exists financial_config
    add column if not exists asaas_webhook_token varchar(255);

comment on column financial_config.asaas_webhook_token is
    'Token do webhook Asaas por condominio, usado para autenticar chamadas recebidas sem depender de JWT.';
