-- Tabela responsável pelo fluxo "esqueci minha senha"
-- Cada token pertence a 1 usuário e 1 tenant (condomínio)
-- Inclui trilha de auditoria e controle de uso

CREATE TABLE IF NOT EXISTS password_reset_token (
    id BIGSERIAL PRIMARY KEY,

    -- Token único que será enviado por e-mail para resetar a senha
    token VARCHAR(255) NOT NULL UNIQUE,

    -- Quem pediu o reset. users.id = bigint
    user_id BIGINT NOT NULL,

    -- Multi-tenant: identifica qual condomínio / cliente
    tenant_id VARCHAR(64) NOT NULL,

    -- Guardar o e-mail usado no fluxo (facilita lookup sem join)
    email VARCHAR(200) NOT NULL,

    -- Validade do token (depois disso não pode mais usar)
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    -- Marcador lógico se já foi usado
    used BOOLEAN NOT NULL DEFAULT FALSE,

    -- Quando efetivamente foi utilizado (null enquanto não usou)
    used_at TIMESTAMP WITHOUT TIME ZONE,

    -- Carimbo de criação do token
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

-- Índices de lookup rápidos
CREATE INDEX IF NOT EXISTS idx_password_reset_token_token
    ON password_reset_token (token);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_user
    ON password_reset_token (user_id);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_tenant
    ON password_reset_token (tenant_id);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_email
    ON password_reset_token (email);
