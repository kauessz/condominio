-- Corrige o tipo das colunas de data/hora do visitor para timestamptz (timestamp with time zone)
-- e garante default/constraints coerentes com o mapeamento (Instant no Java).

-- 1) Converte check_in_at para timestamptz.
--    Se a coluna antiga era "timestamp" (sem fuso), interpretamos como horário local do servidor
--    e convertemos para timestamptz assumindo UTC para não deslocar horários existentes.
--    Se preferir considerar o timestamp antigo como "localtime do Brasil", troque 'UTC' por 'America/Sao_Paulo'.
ALTER TABLE visitor
  ALTER COLUMN check_in_at TYPE timestamptz
    USING (
      CASE
        WHEN check_in_at IS NULL THEN NULL
        ELSE check_in_at AT TIME ZONE 'UTC'
      END
    );

-- 2) Garante valores para registros antigos nulos (evita violar NOT NULL).
UPDATE visitor
   SET check_in_at = now()
 WHERE check_in_at IS NULL;

-- 3) Define default e NOT NULL.
ALTER TABLE visitor
  ALTER COLUMN check_in_at SET DEFAULT now(),
  ALTER COLUMN check_in_at SET NOT NULL;

-- 4) (Opcional, mas recomendado) Ajusta também o check_out_at para timestamptz por consistência.
--    Normalmente check_out_at pode ser NULL e sem default.
ALTER TABLE visitor
  ALTER COLUMN check_out_at TYPE timestamptz
    USING (
      CASE
        WHEN check_out_at IS NULL THEN NULL
        ELSE check_out_at AT TIME ZONE 'UTC'
      END
    );

-- Remover default/constraints antigas incoerentes (por via das dúvidas)
ALTER TABLE visitor
  ALTER COLUMN check_out_at DROP DEFAULT;
