alter table assembly
    add column if not exists validated_at timestamptz,
    add column if not exists validated_by bigint;

alter table assembly_agenda_item
    add column if not exists resolution_status varchar(32) not null default 'NOT_APPLICABLE',
    add column if not exists winning_option_id bigint,
    add column if not exists resolved_at timestamptz,
    add column if not exists resolved_by bigint,
    add column if not exists applied_user_id bigint;

update assembly_agenda_item
set resolution_status = case
    when item_type = 'OFFICE_ELECTION' then 'PENDING'
    else 'NOT_APPLICABLE'
end
where resolution_status is null
   or resolution_status = '';

alter table assembly_agenda_item
    add constraint fk_assembly_agenda_item_winning_option
    foreign key (winning_option_id) references assembly_agenda_option(id);

alter table assembly_agenda_item
    add constraint fk_assembly_agenda_item_applied_user
    foreign key (applied_user_id) references users(id);

alter table assembly_agenda_option
    add column if not exists candidate_user_id bigint,
    add column if not exists candidate_unit_label varchar(80);

alter table assembly_agenda_option
    add constraint fk_assembly_agenda_option_candidate_user
    foreign key (candidate_user_id) references users(id);
