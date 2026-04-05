-- Migration: renomeia a role STAFF → PORTARIA no banco de dados.
-- O campo role é VARCHAR(32), então um UPDATE simples é suficiente.
-- Não há coluna ENUM nativa no PostgreSQL para esta tabela.

UPDATE users
SET role = 'PORTARIA'
WHERE role = 'STAFF';

-- Garante que nenhum usuário fique com role GUEST ativa
-- (GUEST era role de compatibilidade, não deve ter usuários reais com ela)
-- Se houver, promove para MORADOR por segurança.
UPDATE users
SET role = 'MORADOR'
WHERE role = 'GUEST';
