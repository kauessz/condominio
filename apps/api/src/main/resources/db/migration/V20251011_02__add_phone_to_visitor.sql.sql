-- V20251011_02__add_phone_to_visitor.sql
-- Adiciona a coluna 'phone' que existe na entidade Visitor, mas não no banco.

ALTER TABLE visitor
  ADD COLUMN IF NOT EXISTS phone varchar(32);
