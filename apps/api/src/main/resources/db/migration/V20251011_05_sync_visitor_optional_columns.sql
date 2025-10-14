-- Garante que a tabela visitor tenha as colunas opcionais usadas pela entidade Visitor

BEGIN;

ALTER TABLE visitor
  ADD COLUMN IF NOT EXISTS approved_at      timestamptz,
  ADD COLUMN IF NOT EXISTS approved_by      varchar(255),
  ADD COLUMN IF NOT EXISTS rejection_reason varchar(1000),
  ADD COLUMN IF NOT EXISTS deleted_at       timestamptz;

COMMIT;
