update invoice
set launch_key = 'LEGACY:' || id::text
where launch_key is null;

alter table invoice
  alter column launch_key set not null;

do $$
declare
  rec record;
begin
  for rec in
    select c.conname
    from pg_constraint c
    join pg_class t on t.oid = c.conrelid
    join pg_namespace n on n.oid = t.relnamespace
    where n.nspname = 'public'
      and t.relname = 'invoice'
      and c.contype = 'u'
      and (
        select array_agg(a.attname::text order by cols.ord)
        from unnest(c.conkey) with ordinality cols(attnum, ord)
        join pg_attribute a
          on a.attrelid = t.oid
         and a.attnum = cols.attnum
      ) = array['unit_id', 'reference_month']
  loop
    execute format('alter table public.invoice drop constraint %I', rec.conname);
  end loop;
end $$;

drop index if exists public.uq_invoice_unit_month;

do $$
declare
  rec record;
begin
  for rec in
    select idx.indexname
    from pg_indexes idx
    where idx.schemaname = 'public'
      and idx.tablename = 'invoice'
      and idx.indexname <> 'uq_invoice_unit_launch_key'
      and replace(replace(replace(idx.indexdef, '"', ''), ' ', ''), 'public.', '') like '%uniqueindex%oninvoiceusingbtree(unit_id,reference_month)%'
  loop
    execute format('drop index if exists public.%I', rec.indexname);
  end loop;
end $$;

create unique index if not exists uq_invoice_unit_launch_key
  on invoice(unit_id, launch_key);