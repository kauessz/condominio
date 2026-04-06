alter table resident
    add column if not exists user_id bigint null;

create index if not exists idx_resident_user_id on resident(user_id);

alter table resident
    add constraint fk_resident_user
    foreign key (user_id) references users(id);

update resident r
set user_id = u.id
from users u
where r.user_id is null
  and r.tenant_id = u.tenant_id
  and lower(r.email) = lower(u.email)
  and r.condominium_id = u.condominium_id
  and (
      r.unit_id is null
      or u.unit_id is null
      or r.unit_id = u.unit_id
  )
  and u.role in ('MORADOR', 'SINDICO', 'ZELADOR');

alter table assembly_agenda_item
    add column if not exists item_type varchar(32) not null default 'GENERAL_VOTE',
    add column if not exists office_name varchar(120);

update assembly_agenda_item
set item_type = 'GENERAL_VOTE'
where item_type is null;

create table if not exists assembly_agenda_option (
    id bigserial primary key,
    agenda_item_id bigint not null references assembly_agenda_item(id) on delete cascade,
    candidate_name varchar(160) not null,
    sort_order integer not null default 0
);

create index if not exists idx_assembly_agenda_option_item on assembly_agenda_option(agenda_item_id);

alter table assembly_vote
    add column if not exists option_id bigint null references assembly_agenda_option(id) on delete cascade;

alter table assembly_vote
    alter column vote_value drop not null;
