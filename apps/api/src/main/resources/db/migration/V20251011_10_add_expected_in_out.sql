-- V20251011_10_add_expected_in_out.sql
-- Adiciona expected_in_at e expected_out_at na tabela visitor (idempotente)

ALTER TABLE IF EXISTS visitor
  ADD COLUMN IF NOT EXISTS expected_in_at  timestamp without time zone;

ALTER TABLE IF EXISTS visitor
  ADD COLUMN IF NOT EXISTS expected_out_at timestamp without time zone;
