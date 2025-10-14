-- Apenas garante a coluna deleted_at em visitor
BEGIN;

ALTER TABLE visitor
  ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

COMMIT;
