alter table if exists audit_log
  add column if not exists module varchar(32),
  add column if not exists description text;

create index if not exists idx_audit_log_module on audit_log(module);
create index if not exists idx_audit_log_actor_user on audit_log(actor_user_id);
