-- Módulo 1: Áreas Comuns e Reservas
create table if not exists common_area (
  id                      bigserial primary key,
  tenant_id               varchar(64) not null,
  condominium_id          bigint not null references condominium(id),
  name                    varchar(120) not null,
  capacity                int,
  rules                   text,
  max_hours_per_reservation int not null default 4,
  requires_approval       boolean not null default false,
  active                  boolean not null default true,
  created_at              timestamptz default now()
);
create index if not exists idx_common_area_tenant on common_area(tenant_id);
create index if not exists idx_common_area_condo  on common_area(condominium_id);

create table if not exists reservation (
  id              bigserial primary key,
  tenant_id       varchar(64) not null,
  condominium_id  bigint not null references condominium(id),
  common_area_id  bigint not null references common_area(id),
  unit_id         bigint not null references unit(id),
  resident_id     bigint references resident(id),
  start_datetime  timestamptz not null,
  end_datetime    timestamptz not null,
  title           varchar(200),
  notes           text,
  status          varchar(32) not null default 'PENDING',
  approved_by     bigint references users(id),
  approved_at     timestamptz,
  rejection_reason text,
  cancelled_at    timestamptz,
  cancelled_by    bigint references users(id),
  created_by      bigint references users(id),
  created_at      timestamptz default now()
);
create index if not exists idx_reservation_tenant    on reservation(tenant_id);
create index if not exists idx_reservation_condo     on reservation(condominium_id);
create index if not exists idx_reservation_area      on reservation(common_area_id);
create index if not exists idx_reservation_unit      on reservation(unit_id);
create index if not exists idx_reservation_dates     on reservation(common_area_id, start_datetime, end_datetime);
