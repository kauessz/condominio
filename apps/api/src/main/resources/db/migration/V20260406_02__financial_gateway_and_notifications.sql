alter table financial_config
  add column if not exists default_billing_type varchar(32) not null default 'BOLETO',
  add column if not exists notification_email_enabled boolean not null default true,
  add column if not exists notification_whatsapp_enabled boolean not null default false,
  add column if not exists asaas_enabled boolean not null default false;

alter table invoice
  add column if not exists external_provider varchar(32),
  add column if not exists billing_type varchar(32),
  add column if not exists external_charge_id varchar(64),
  add column if not exists external_customer_id varchar(64),
  add column if not exists external_invoice_number varchar(64),
  add column if not exists external_reference varchar(128),
  add column if not exists external_status varchar(64),
  add column if not exists external_created_at timestamptz,
  add column if not exists external_updated_at timestamptz,
  add column if not exists external_last_error text,
  add column if not exists last_webhook_at timestamptz,
  add column if not exists last_notification_at timestamptz,
  add column if not exists last_notification_type varchar(64),
  add column if not exists pix_qr_code text,
  add column if not exists pix_copy_paste text,
  add column if not exists pix_expires_at timestamptz,
  add column if not exists boleto_url text,
  add column if not exists invoice_url text,
  add column if not exists payment_received_at timestamptz,
  add column if not exists failure_reason text,
  add column if not exists cancelled_at timestamptz,
  add column if not exists failed_at timestamptz,
  add column if not exists apportionment_group varchar(160),
  add column if not exists apportionment_mode varchar(32) not null default 'NONE',
  add column if not exists external_last_event_id varchar(128);

create unique index if not exists uq_invoice_external_charge
  on invoice(external_provider, external_charge_id)
  where external_provider is not null and external_charge_id is not null;

create index if not exists idx_invoice_external_reference
  on invoice(external_reference)
  where external_reference is not null;

create table if not exists invoice_webhook_event (
  id                  bigserial primary key,
  tenant_id           varchar(64),
  condominium_id      bigint,
  invoice_id          bigint references invoice(id) on delete set null,
  provider            varchar(32) not null,
  external_event_id   varchar(128) not null,
  external_charge_id  varchar(64),
  event_type          varchar(64) not null,
  processing_status   varchar(32) not null default 'RECEIVED',
  payload             text,
  error_message       text,
  received_at         timestamptz not null default now(),
  processed_at        timestamptz
);

create unique index if not exists uq_invoice_webhook_provider_event
  on invoice_webhook_event(provider, external_event_id);

create index if not exists idx_invoice_webhook_charge
  on invoice_webhook_event(provider, external_charge_id);

create table if not exists financial_webhook_event (
  id                  bigserial primary key,
  tenant_id           varchar(64),
  invoice_id          bigint references invoice(id) on delete set null,
  provider            varchar(32) not null,
  dedup_key           varchar(255) not null,
  event_type          varchar(64),
  external_charge_id  varchar(64),
  processing_status   varchar(32) not null default 'RECEIVED',
  error_message       text,
  payload             text not null,
  received_at         timestamptz not null default now(),
  processed_at        timestamptz
);

create unique index if not exists uq_financial_webhook_dedup
  on financial_webhook_event(dedup_key);

create table if not exists invoice_event (
  id               bigserial primary key,
  tenant_id        varchar(64) not null,
  condominium_id   bigint not null references condominium(id),
  invoice_id       bigint not null references invoice(id) on delete cascade,
  event_type       varchar(64) not null,
  title            varchar(160) not null,
  message          text,
  metadata         text,
  created_at       timestamptz not null default now()
);

create index if not exists idx_invoice_event_invoice_created
  on invoice_event(invoice_id, created_at desc);
