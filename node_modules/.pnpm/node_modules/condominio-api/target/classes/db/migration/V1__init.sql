create table if not exists users (
  id bigserial primary key,
  tenant_id varchar(64) not null,
  email varchar(200) not null unique,
  password_hash varchar(255) not null,
  role varchar(32) not null,
  created_at timestamp default now()
);
create index if not exists idx_users_tenant on users(tenant_id);

create table if not exists condominium (
  id bigserial primary key,
  tenant_id varchar(64) not null,
  name varchar(200) not null,
  cnpj varchar(32),
  created_at timestamp default now()
);
create index if not exists idx_condominium_tenant on condominium(tenant_id);

create table if not exists unit (
  id bigserial primary key,
  tenant_id varchar(64) not null,
  condominium_id bigint not null references condominium(id),
  code varchar(64) not null,
  owner_name varchar(200),
  created_at timestamp default now()
);
create index if not exists idx_unit_tenant on unit(tenant_id);

create table if not exists amenity (
  id bigserial primary key,
  tenant_id varchar(64) not null,
  name varchar(120) not null,
  rules text,
  created_at timestamp default now()
);
create index if not exists idx_amenity_tenant on amenity(tenant_id);

create table if not exists amenity_reservation (
  id bigserial primary key,
  tenant_id varchar(64) not null,
  amenity_id bigint not null references amenity(id),
  unit_id bigint not null references unit(id),
  start_at timestamp not null,
  end_at timestamp not null,
  status varchar(32) not null default 'PENDING'
);
create index if not exists idx_reservation_tenant on amenity_reservation(tenant_id);

create table if not exists ticket (
  id bigserial primary key,
  tenant_id varchar(64) not null,
  unit_id bigint references unit(id),
  title varchar(200) not null,
  description text,
  status varchar(32) not null default 'OPEN',
  created_by bigint references users(id),
  created_at timestamp default now()
);
create index if not exists idx_ticket_tenant on ticket(tenant_id);

create table if not exists invoice (
  id bigserial primary key,
  tenant_id varchar(64) not null,
  unit_id bigint not null references unit(id),
  due_date date not null,
  amount numeric(12,2) not null,
  status varchar(32) not null default 'PENDING',
  external_charge_id varchar(128)
);
create index if not exists idx_invoice_tenant on invoice(tenant_id);

create table if not exists payment (
  id bigserial primary key,
  tenant_id varchar(64) not null,
  invoice_id bigint not null references invoice(id),
  paid_at timestamp not null default now(),
  amount numeric(12,2) not null,
  method varchar(32) not null,
  raw_payload jsonb
);
create index if not exists idx_payment_tenant on payment(tenant_id);
