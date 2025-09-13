-- Tabela de moradores
create table if not exists resident (
  id               bigserial primary key,
  tenant_id        varchar(64)  not null,
  condominium_id   bigint       not null,
  unit_id          bigint       null,
  name             varchar(200) not null,
  email            varchar(200) not null,
  phone            varchar(60)  not null
);

-- (opcional mas recomendado) FKs
-- Se suas tabelas já existem com esses nomes/PKs:
alter table resident
  add constraint fk_resident_condominium
  foreign key (condominium_id) references condominium(id) on delete cascade;

alter table resident
  add constraint fk_resident_unit
  foreign key (unit_id) references unit(id) on delete set null;

-- Índices para performance de busca/paginação por tenant/condomínio
create index if not exists idx_resident_tenant_condo
  on resident (tenant_id, condominium_id);

-- Índices auxiliares para buscas por nome/email/telefone (dependendo do uso):
-- create index if not exists idx_resident_name  on resident (lower(name));
-- create index if not exists idx_resident_email on resident (lower(email));
-- create index if not exists idx_resident_phone on resident (lower(phone));