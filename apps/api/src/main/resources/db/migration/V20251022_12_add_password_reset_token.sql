-- Criação da tabela de tokens de reset de senha
CREATE TABLE IF NOT EXISTS password_reset_token (
  id          BIGSERIAL PRIMARY KEY,
  token       VARCHAR(128) NOT NULL UNIQUE,
  tenant_id   VARCHAR(64)  NOT NULL,
  email       VARCHAR(320) NOT NULL,
  created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
  expires_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  used_at     TIMESTAMP WITHOUT TIME ZONE NULL
);

-- Índices úteis
CREATE INDEX IF NOT EXISTS idx_prt_tenant_email   ON password_reset_token (tenant_id, email);
CREATE INDEX IF NOT EXISTS idx_prt_token_expires  ON password_reset_token (token, expires_at);
