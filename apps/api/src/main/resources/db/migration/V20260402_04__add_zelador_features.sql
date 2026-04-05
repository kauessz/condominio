-- Adiciona timeout de visitas pendentes por condomínio (padrão: 15 minutos)
ALTER TABLE condominium
    ADD COLUMN IF NOT EXISTS visitor_pending_timeout_minutes INTEGER NOT NULL DEFAULT 15;

-- Adiciona flag de troca de senha obrigatória no primeiro login
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
