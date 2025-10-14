-- V20251011_08_add_visitor_email.sql
-- Adiciona a coluna email se ainda não existir (mantém padrão do seu schema: varchar, nullable).
ALTER TABLE IF EXISTS visitor
  ADD COLUMN IF NOT EXISTS email varchar(200);
