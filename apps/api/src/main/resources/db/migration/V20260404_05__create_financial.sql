-- Módulo 5: Financeiro Básico
create table if not exists financial_config (
  id              bigserial primary key,
  tenant_id       varchar(64) not null,
  condominium_id  bigint not null unique references condominium(id),
  monthly_fee     numeric(12,2) not null default 0,
  due_day         int not null default 10,
  late_fee_pct    numeric(5,2) not null default 2.00,
  interest_pct    numeric(5,2) not null default 1.00,
  pix_key         varchar(200),
  pix_key_type    varchar(32),
  updated_at      timestamptz default now()
);
create index if not exists idx_financial_config_tenant on financial_config(tenant_id);

-- A tabela invoice já existe desde V1__init.sql.
-- Aqui fazemos a evolução compatível do schema legado para o novo módulo financeiro.
alter table invoice add column if not exists condominium_id bigint;
alter table invoice add column if not exists reference_month varchar(7);
alter table invoice add column if not exists paid_at timestamptz;
alter table invoice add column if not exists paid_amount numeric(12,2);
alter table invoice add column if not exists payment_method varchar(32);
alter table invoice add column if not exists payment_notes text;
alter table invoice add column if not exists registered_by bigint;
alter table invoice add column if not exists created_at timestamptz default now();

update invoice i
set condominium_id = u.condominium_id
from unit u
where u.id = i.unit_id
  and i.condominium_id is null;

update invoice
set reference_month = to_char(due_date, 'YYYY-MM')
where reference_month is null;

update invoice
set created_at = now()
where created_at is null;

alter table invoice
  alter column condominium_id set not null;

alter table invoice
  alter column reference_month set not null;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'invoice_condominium_id_fkey'
  ) then
    alter table invoice
      add constraint invoice_condominium_id_fkey
      foreign key (condominium_id) references condominium(id);
  end if;

  if not exists (
    select 1
    from pg_constraint
    where conname = 'invoice_registered_by_fkey'
  ) then
    alter table invoice
      add constraint invoice_registered_by_fkey
      foreign key (registered_by) references users(id);
  end if;
end $$;

create unique index if not exists uq_invoice_unit_month on invoice(unit_id, reference_month);
create index if not exists idx_invoice_tenant on invoice(tenant_id);
create index if not exists idx_invoice_condo  on invoice(condominium_id);
create index if not exists idx_invoice_unit   on invoice(unit_id);
create index if not exists idx_invoice_status on invoice(status);
