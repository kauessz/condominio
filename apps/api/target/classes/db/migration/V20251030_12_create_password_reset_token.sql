-- Criação da tabela para tokens de recuperação de senha
-- Essa tabela serve para fluxo "esqueci minha senha"
-- Cada token pertence a um usuário específico e a um tenant específico

CREATE TABLE password_reset_token (
    id BIGSERIAL PRIMARY KEY,

    -- Token aleatório enviado por e-mail / link de recuperação
    token VARCHAR(255) NOT NULL UNIQUE,

    -- Usuário dono do token (vem de public.users.id)
    user_id BIGINT NOT NULL,

    -- Controle multi-condomínio / multi-tenant
    tenant_id VARCHAR(64) NOT NULL,

    -- Quando esse token expira (depois disso não pode mais usar)
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    -- Marcar se já foi utilizado
    used BOOLEAN NOT NULL DEFAULT FALSE,

    -- Carimbo de criação
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

-- Índice para procurar token rápido (login de recuperação normalmente usa token direto)
CREATE INDEX idx_password_reset_token_token
    ON password_reset_token (token);

-- Índice para permitir listar/invalidar tokens de um usuário específico
CREATE INDEX idx_password_reset_token_user
    ON password_reset_token (user_id);

-- Índice para garantir isolamento e auditoria por condomínio
CREATE INDEX idx_password_reset_token_tenant
    ON password_reset_token (tenant_id);