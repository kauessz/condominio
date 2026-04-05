alter table if exists parking_draw_registration
  add column if not exists tenant_id varchar(64);

alter table if exists parking_draw_registration
  add column if not exists condominium_id bigint;

update parking_draw_registration reg
set tenant_id = draw.tenant_id,
    condominium_id = draw.condominium_id
from parking_draw draw
where reg.draw_id = draw.id
  and (reg.tenant_id is null or reg.condominium_id is null);

alter table if exists parking_draw_registration
  alter column tenant_id set not null;

alter table if exists parking_draw_registration
  alter column condominium_id set not null;

alter table if exists parking_draw_registration
  add constraint fk_parking_reg_condominium
  foreign key (condominium_id) references condominium(id);

create index if not exists idx_parking_reg_tenant on parking_draw_registration(tenant_id);
create index if not exists idx_parking_reg_condo on parking_draw_registration(condominium_id);
