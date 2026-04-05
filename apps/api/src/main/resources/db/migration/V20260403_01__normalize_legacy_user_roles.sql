-- Normaliza roles legadas para os nomes atuais aceitos pelo backend.
-- Mantém a base consistente mesmo se alguma instância tiver ficado entre migrations.

UPDATE users SET role = 'SUPERUSER' WHERE role = 'SUPER_ADMIN';
UPDATE users SET role = 'SINDICO'   WHERE role = 'MANAGER';
UPDATE users SET role = 'PORTARIA'  WHERE role = 'STAFF';
UPDATE users SET role = 'MORADOR'   WHERE role IN ('RESIDENT', 'GUEST');
