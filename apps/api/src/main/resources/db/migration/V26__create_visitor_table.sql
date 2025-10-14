create table if not exists visitor (
  id              bigserial primary key,
  tenant_id       varchar(64)  not null,
  condominium_id  bigint       not null,
  unit_id         bigint       null,

  name            varchar(160) not null,
  document        varchar(64)  null,    -- RG/CPF/Outro
  plate           varchar(16)  null,    -- placa do veículo
  note            text         null,

  check_in_at     timestamp    not null,
  check_out_at    timestamp    null,

  created_at      timestamp    default now()
);

create index if not exists idx_visitor_tenant_condo on visitor(tenant_id, condominium_id);
create index if not exists idx_visitor_unit on visitor(tenant_id, unit_id);
create index if not exists idx_visitor_dates on visitor(check_in_at, check_out_at);