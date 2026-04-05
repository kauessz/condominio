alter table condominium
    add column if not exists active boolean not null default true,
    add column if not exists allow_syndic_approve_visitor boolean not null default false,
    add column if not exists resident_approval_required boolean not null default true,
    add column if not exists admin_override_allowed boolean not null default true,
    add column if not exists portaria_can_auto_approve boolean not null default false;

create table if not exists governance_request (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    request_type varchar(64) not null,
    target_entity_type varchar(64) not null,
    target_entity_id bigint null,
    condominium_id bigint null,
    requested_by_user_id bigint not null,
    requested_by_role varchar(32) not null,
    status varchar(32) not null default 'PENDING',
    payload_before jsonb null,
    payload_after jsonb null,
    approved_by_user_id bigint null,
    approved_at timestamptz null,
    rejection_reason varchar(1000) null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_governance_request_tenant_status
    on governance_request (tenant_id, status, created_at desc);

create index if not exists idx_governance_request_condominium
    on governance_request (condominium_id, created_at desc);

alter table visitor
    alter column status type varchar(32);

update visitor
set status = 'PENDING_APPROVAL'
where status = 'PENDING';
