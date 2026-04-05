alter table if exists audit_log
  drop constraint if exists audit_log_condominium_id_fkey;

alter table if exists audit_log
  add constraint audit_log_condominium_id_fkey
  foreign key (condominium_id)
  references condominium(id)
  on delete set null;

alter table if exists audit_log
  drop constraint if exists audit_log_actor_user_id_fkey;

alter table if exists audit_log
  add constraint audit_log_actor_user_id_fkey
  foreign key (actor_user_id)
  references users(id)
  on delete set null;
