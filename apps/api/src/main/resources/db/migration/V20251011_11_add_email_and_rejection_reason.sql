-- V20251011_11_add_email_and_rejection_reason.sql
-- Garante as colunas usadas por Visitor.java

ALTER TABLE IF EXISTS visitor
  ADD COLUMN IF NOT EXISTS email            varchar(200);

ALTER TABLE IF EXISTS visitor
  ADD COLUMN IF NOT EXISTS rejection_reason varchar(1000);
