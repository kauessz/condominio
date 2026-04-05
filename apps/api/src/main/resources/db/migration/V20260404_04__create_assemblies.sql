-- Módulo 4: Assembleias e Votações
create table if not exists assembly (
  id              bigserial primary key,
  tenant_id       varchar(64) not null,
  condominium_id  bigint not null references condominium(id),
  title           varchar(200) not null,
  description     text,
  scheduled_at    timestamptz not null,
  location        varchar(300),
  status          varchar(32) not null default 'SCHEDULED',
  opened_at       timestamptz,
  closed_at       timestamptz,
  created_by      bigint references users(id),
  created_at      timestamptz default now()
);
create index if not exists idx_assembly_tenant on assembly(tenant_id);
create index if not exists idx_assembly_condo  on assembly(condominium_id);

create table if not exists assembly_agenda_item (
  id          bigserial primary key,
  assembly_id bigint not null references assembly(id),
  title       varchar(200) not null,
  description text,
  requires_vote boolean not null default true,
  sort_order  int not null default 0
);
create index if not exists idx_agenda_item_assembly on assembly_agenda_item(assembly_id);

create table if not exists assembly_vote (
  id              bigserial primary key,
  agenda_item_id  bigint not null references assembly_agenda_item(id),
  unit_id         bigint not null references unit(id),
  vote_value      varchar(16) not null,
  voted_by        bigint references users(id),
  voted_at        timestamptz default now(),
  constraint uq_vote_unit_item unique(agenda_item_id, unit_id)
);
create index if not exists idx_assembly_vote_item on assembly_vote(agenda_item_id);
