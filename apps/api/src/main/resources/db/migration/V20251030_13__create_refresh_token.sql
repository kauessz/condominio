-- Tabela de refresh tokens para sessão JWT
-- Cada refresh token pertence a um usuário e a um tenant específico
-- Suporta revogação e auditoria

CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGSERIAL PRIMARY KEY,

    -- Refresh token persistido no banco
    token VARCHAR(255) NOT NULL UNIQUE,

    -- Dono do token (Tabela users, PK bigint)
    user_id BIGINT NOT NULL,

    -- Multi-tenant / condomínio
    tenant_id VARCHAR(64) NOT NULL,

    -- Até quando esse refresh token é válido
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    -- Flag lógico indicando se já foi revogado
    revoked BOOLEAN NOT NULL DEFAULT FALSE,

    -- Quando ele foi revogado (null = ainda ativo)
    revoked_at TIMESTAMP WITHOUT TIME ZONE,

    -- Quando esse token foi emitido
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

-- Índices para buscas/exclusões/limpeza
CREATE INDEX IF NOT EXISTS idx_refresh_token_token
    ON refresh_token (token);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user
    ON refresh_token (user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_token_tenant
    ON refresh_token (tenant_id);
