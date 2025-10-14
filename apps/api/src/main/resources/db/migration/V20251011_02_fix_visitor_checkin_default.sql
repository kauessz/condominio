-- Garante que visitor.check_in_at nunca fique nulo e tenha default de now()

-- 1) Define DEFAULT now() (timestamp do servidor)
ALTER TABLE visitor
  ALTER COLUMN check_in_at SET DEFAULT now();

-- 2) Corrige linhas antigas que eventualmente estejam NULL
UPDATE visitor
   SET check_in_at = now()
 WHERE check_in_at IS NULL;

-- 3) (Opcional mas recomendado) reforça NOT NULL
ALTER TABLE visitor
  ALTER COLUMN check_in_at SET NOT NULL;
