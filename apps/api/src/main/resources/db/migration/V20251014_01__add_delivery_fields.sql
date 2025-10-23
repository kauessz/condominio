-- adiciona colunas somente se ainda não existirem
ALTER TABLE visitor
  ADD COLUMN IF NOT EXISTS carrier  VARCHAR(255);

ALTER TABLE visitor
  ADD COLUMN IF NOT EXISTS packages INTEGER;