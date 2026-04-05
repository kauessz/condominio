-- Adiciona condominium_id à tabela users para isolamento de tenant por role.
-- SUPERUSER permanece null (acesso global).
-- SINDICO, ADMIN, PORTARIA, MORADOR devem ter o condominium_id do seu condomínio.
ALTER TABLE users ADD COLUMN IF NOT EXISTS condominium_id BIGINT;
