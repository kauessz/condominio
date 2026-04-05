-- Tabela de solicitações de cadastro de condomínio (onboarding público)
CREATE TABLE IF NOT EXISTS condominium_requests (
    id                 BIGSERIAL PRIMARY KEY,
    condominium_name   VARCHAR(255) NOT NULL,
    cnpj               VARCHAR(18),
    address            TEXT,
    requester_name     VARCHAR(255) NOT NULL,
    requester_email    VARCHAR(255) NOT NULL,
    requester_phone    VARCHAR(20),
    requester_role     VARCHAR(50),
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    rejection_reason   TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reviewed_at        TIMESTAMPTZ,
    reviewed_by        BIGINT REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_condo_requests_status ON condominium_requests(status);
CREATE INDEX IF NOT EXISTS idx_condo_requests_email  ON condominium_requests(requester_email);
