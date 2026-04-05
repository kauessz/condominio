alter table condominium
  add column if not exists parking_policy_mode varchar(16) not null default 'DRAW',
  add column if not exists parking_draw_frequency varchar(16) not null default 'QUARTERLY',
  add column if not exists draw_interval_months integer,
  add column if not exists allow_manual_assignments boolean not null default true,
  add column if not exists allow_resident_registration boolean not null default true,
  add column if not exists max_vehicles_per_unit integer not null default 1,
  add column if not exists parking_rules text;

create table if not exists audit_log (
  id bigserial primary key,
  tenant_id varchar(64) not null,
  condominium_id bigint references condominium(id),
  actor_user_id bigint references users(id),
  actor_role varchar(32),
  entity_name varchar(64) not null,
  entity_id varchar(64) not null,
  action varchar(64) not null,
  before_state jsonb,
  after_state jsonb,
  details jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_audit_log_tenant on audit_log(tenant_id);
create index if not exists idx_audit_log_condo on audit_log(condominium_id);
create index if not exists idx_audit_log_entity on audit_log(entity_name, entity_id);
create index if not exists idx_audit_log_created_at on audit_log(created_at desc);
