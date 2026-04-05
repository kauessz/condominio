create extension if not exists btree_gist;

alter table audit_log
  add column if not exists actor_name varchar(255),
  add column if not exists actor_email varchar(200);

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'reservation_time_range_valid'
  ) then
    alter table reservation
      add constraint reservation_time_range_valid
      check (end_datetime > start_datetime);
  end if;
end $$;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'reservation_no_overlap'
  ) then
    alter table reservation
      add constraint reservation_no_overlap
      exclude using gist (
        common_area_id with =,
        tstzrange(start_datetime, end_datetime, '[)') with &&
      )
      where (status not in ('CANCELLED', 'REJECTED', 'COMPLETED'));
  end if;
end $$;
