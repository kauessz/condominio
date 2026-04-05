-- Módulo 2: Vagas de Estacionamento por Sorteio
create table if not exists parking_spot (
  id              bigserial primary key,
  tenant_id       varchar(64) not null,
  condominium_id  bigint not null references condominium(id),
  code            varchar(32) not null,
  description     varchar(200),
  active          boolean not null default true,
  created_at      timestamptz default now()
);
create index if not exists idx_parking_spot_tenant on parking_spot(tenant_id);
create index if not exists idx_parking_spot_condo  on parking_spot(condominium_id);

create table if not exists parking_draw (
  id                bigserial primary key,
  tenant_id         varchar(64) not null,
  condominium_id    bigint not null references condominium(id),
  name              varchar(200) not null,
  registration_open_at  timestamptz not null,
  registration_close_at timestamptz not null,
  valid_from        date not null,
  valid_until       date not null,
  status            varchar(32) not null default 'OPEN',
  executed_at       timestamptz,
  executed_by       bigint references users(id),
  created_by        bigint references users(id),
  created_at        timestamptz default now()
);
create index if not exists idx_parking_draw_tenant on parking_draw(tenant_id);
create index if not exists idx_parking_draw_condo  on parking_draw(condominium_id);

create table if not exists parking_draw_registration (
  id          bigserial primary key,
  draw_id     bigint not null references parking_draw(id),
  unit_id     bigint not null references unit(id),
  resident_id bigint references resident(id),
  registered_at timestamptz default now(),
  constraint uq_draw_unit unique(draw_id, unit_id)
);
create index if not exists idx_parking_reg_draw on parking_draw_registration(draw_id);

create table if not exists parking_spot_assignment (
  id              bigserial primary key,
  tenant_id       varchar(64) not null,
  condominium_id  bigint not null references condominium(id),
  spot_id         bigint not null references parking_spot(id),
  unit_id         bigint not null references unit(id),
  draw_id         bigint references parking_draw(id),
  valid_from      date not null,
  valid_until     date not null,
  status          varchar(32) not null default 'ACTIVE',
  created_at      timestamptz default now()
);
create index if not exists idx_parking_assignment_tenant on parking_spot_assignment(tenant_id);
create index if not exists idx_parking_assignment_condo  on parking_spot_assignment(condominium_id);
create index if not exists idx_parking_assignment_spot   on parking_spot_assignment(spot_id);
create index if not exists idx_parking_assignment_unit   on parking_spot_assignment(unit_id);
