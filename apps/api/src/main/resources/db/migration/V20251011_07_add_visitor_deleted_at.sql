-- V20251011_07_add_visitor_deleted_at.sql
-- Adiciona a coluna deleted_at se ainda não existir.
ALTER TABLE IF EXISTS visitor
  ADD COLUMN IF NOT EXISTS deleted_at timestamp without time zone;
