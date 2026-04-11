create table if not exists financial_notification (
  id               bigserial primary key,
  tenant_id        varchar(64) not null,
  condominium_id   bigint not null references condominium(id),
  invoice_id       bigint references invoice(id) on delete set null,
  unit_id          bigint references unit(id) on delete set null,
  recipient_email  varchar(200),
  recipient_name   varchar(200),
  type             varchar(64) not null,
  channel          varchar(32) not null,
  status           varchar(32) not null,
  message          text not null,
  metadata         jsonb,
  sent_at          timestamptz,
  created_at       timestamptz not null default now()
);

create index if not exists idx_financial_notification_invoice_created
  on financial_notification(invoice_id, created_at desc);

create index if not exists idx_financial_notification_tenant_type
  on financial_notification(tenant_id, type);
