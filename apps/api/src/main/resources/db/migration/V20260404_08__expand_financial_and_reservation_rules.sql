alter table condominium
  add column if not exists reservation_policy_mode varchar(32) not null default 'FLEXIBLE_INTERVAL',
  add column if not exists default_max_duration_hours int not null default 4,
  add column if not exists default_start_hour int not null default 8,
  add column if not exists default_end_hour int not null default 22,
  add column if not exists all_day_reservation_allowed boolean not null default false,
  add column if not exists reservation_approval_mode varchar(32) not null default 'AUTOMATIC',
  add column if not exists reservation_rules text;

alter table common_area
  add column if not exists allowed_start_hour int,
  add column if not exists allowed_end_hour int,
  add column if not exists reservation_description text,
  add column if not exists reservation_approval_mode varchar(32),
  add column if not exists allow_override_from_condominium_default boolean not null default false;

update common_area
set reservation_description = rules
where reservation_description is null
  and rules is not null;

update common_area
set reservation_approval_mode = case when requires_approval then 'REQUIRE_APPROVAL' else 'AUTOMATIC' end
where reservation_approval_mode is null;

alter table invoice
  add column if not exists charge_type varchar(32) not null default 'CONDOMINIO',
  add column if not exists title varchar(200),
  add column if not exists description text,
  add column if not exists launch_key varchar(160);

update invoice
set title = case
  when title is not null then title
  when reference_month is not null then 'Cobrança ' || reference_month
  else 'Cobrança #' || id
end
where title is null;

update invoice
set launch_key = coalesce(launch_key, 'LEGACY:' || id::text)
where launch_key is null;

drop index if exists uq_invoice_unit_month;

create unique index if not exists uq_invoice_unit_launch_key
  on invoice(unit_id, launch_key);

create index if not exists idx_invoice_charge_type on invoice(charge_type);
