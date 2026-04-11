-- Tarefa 4: Adiciona CPF ao morador (campo opcional, indexado por tenant)
ALTER TABLE resident ADD COLUMN IF NOT EXISTS cpf VARCHAR(14);

CREATE INDEX IF NOT EXISTS idx_resident_cpf
    ON resident(tenant_id, cpf)
    WHERE cpf IS NOT NULL;
