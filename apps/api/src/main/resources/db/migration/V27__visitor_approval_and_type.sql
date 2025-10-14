-- Aprovação de visitante e suporte a entregas (tipo)
-- Estados: PENDING | APPROVED | DENIED | CHECKED_IN | CHECKED_OUT
-- Tipos:   VISITOR | DELIVERY

ALTER TABLE visitor
  ADD COLUMN IF NOT EXISTS status      VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN IF NOT EXISTS type        VARCHAR(24) NOT NULL DEFAULT 'VISITOR',
  ADD COLUMN IF NOT EXISTS resident_id BIGINT NULL,
  ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64) NULL,
  ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP NULL,
  ADD COLUMN IF NOT EXISTS carrier     VARCHAR(80) NULL,
  ADD COLUMN IF NOT EXISTS packages    INT NULL;

-- índices úteis para filtros frequentes
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname = 'public' AND indexname = 'idx_visitor_tenant_status'
  ) THEN
    CREATE INDEX idx_visitor_tenant_status ON visitor(tenant_id, status);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname = 'public' AND indexname = 'idx_visitor_condo_unit'
  ) THEN
    CREATE INDEX idx_visitor_condo_unit ON visitor(condominium_id, unit_id);
  END IF;
END$$;