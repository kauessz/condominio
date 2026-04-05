-- Migration: Atualizar nomenclatura de roles para o sistema CondoHub Fase 1
-- Mapeamento:
--   SUPER_ADMIN -> SUPERUSER  (dono do SaaS)
--   ADMIN       -> ADMIN      (sem alteração — administrador de condomínio)
--   MANAGER     -> SINDICO    (síndico com acesso financeiro/assembleias)
--   STAFF       -> STAFF      (sem alteração — portaria)
--   RESIDENT    -> MORADOR    (morador da unidade)
--   GUEST       -> GUEST      (sem alteração — visitante sem conta)

UPDATE users SET role = 'SUPERUSER' WHERE role = 'SUPER_ADMIN';
UPDATE users SET role = 'SINDICO'   WHERE role = 'MANAGER';
UPDATE users SET role = 'MORADOR'   WHERE role = 'RESIDENT';

-- Atualizar comentário da coluna para refletir novas roles
COMMENT ON COLUMN users.role IS 'Roles: SUPERUSER, SINDICO, ADMIN, STAFF, MORADOR, GUEST';

-- Garantir que o usuário de seed padrão seja SUPERUSER (caso ainda seja ADMIN)
UPDATE users
SET role = 'SUPERUSER',
    name = COALESCE(name, 'Super Admin')
WHERE email = 'admin@demo.com'
  AND role IN ('ADMIN', 'SUPER_ADMIN', 'SUPERUSER');
