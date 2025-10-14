-- Recria check_in_at como timestamptz, copiando dados e garantindo default/constraints.
-- Também ajusta check_out_at para timestamptz por consistência.

BEGIN;

-- check_in_at → nova coluna com o tipo certo
ALTER TABLE visitor
  ADD COLUMN check_in_at_tz timestamptz;

-- Copia dados antigos. Se os valores antigos eram "timestamp" (sem fuso),
-- interpretamos como UTC para não deslocar o horário. Troque 'UTC' por
-- 'America/Sao_Paulo' se quiser considerar como horário local do Brasil.
UPDATE visitor
   SET check_in_at_tz = COALESCE(check_in_at AT TIME ZONE 'UTC', now());

-- Garante NOT NULL + default
ALTER TABLE visitor
  ALTER COLUMN check_in_at_tz SET NOT NULL,
  ALTER COLUMN check_in_at_tz SET DEFAULT now();

-- Remove a coluna antiga e renomeia a nova
ALTER TABLE visitor
  DROP COLUMN check_in_at,
  RENAME COLUMN check_in_at_tz TO check_in_at;

-- (Opcional) Ajusta check_out_at para timestamptz também
ALTER TABLE visitor
  ADD COLUMN check_out_at_tz timestamptz;

UPDATE visitor
   SET check_out_at_tz =
       CASE
         WHEN check_out_at IS NULL THEN NULL
         ELSE check_out_at AT TIME ZONE 'UTC'
       END;

ALTER TABLE visitor
  DROP COLUMN check_out_at,
  RENAME COLUMN check_out_at_tz TO check_out_at;

COMMIT;
