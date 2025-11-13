-- Migration: Adicionar campo name em users e preparar para roles
-- V20251031_01__add_name_to_users.sql

-- 1. Adicionar coluna name (se não existir)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'users' AND column_name = 'name'
    ) THEN
        ALTER TABLE users ADD COLUMN name VARCHAR(255);
    END IF;
END $$;

-- 2. Atualizar nomes vazios com base no email (antes do @)
UPDATE users 
SET name = split_part(email, '@', 1) 
WHERE name IS NULL OR name = '';

-- 3. Comentar a coluna role para documentar as opções disponíveis
COMMENT ON COLUMN users.role IS 'Roles disponíveis: SUPER_ADMIN, ADMIN, MANAGER, STAFF, RESIDENT, GUEST';

-- 4. Criar índices para otimizar buscas por role
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_tenant_role ON users(tenant_id, role);

-- 5. Atualizar o admin padrão para SUPER_ADMIN (se existir)
UPDATE users 
SET role = 'SUPER_ADMIN',
    name = COALESCE(name, 'Super Admin')
WHERE email = 'admin@demo.com';

-- 6. Adicionar coluna unit_id para vincular usuários RESIDENT às unidades
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'users' AND column_name = 'unit_id'
    ) THEN
        ALTER TABLE users ADD COLUMN unit_id BIGINT;
        ALTER TABLE users ADD CONSTRAINT fk_users_unit 
            FOREIGN KEY (unit_id) REFERENCES unit(id) ON DELETE SET NULL;
    END IF;
END $$;

-- 7. Criar índice na unit_id
CREATE INDEX IF NOT EXISTS idx_users_unit ON users(unit_id);