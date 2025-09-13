-- Garante a coluna "number" na tabela unit e tenta popular a partir de colunas legadas comuns.

do $$
declare
  src_col text;
begin
  -- 1) cria a coluna se não existir
  if not exists (
    select 1 from information_schema.columns
    where table_schema='public' and table_name='unit' and column_name='number'
  ) then
    execute 'alter table unit add column number varchar(50)';
  end if;

  -- 2) tenta descobrir uma coluna antiga para copiar valores (ajuste a lista se necessário)
  for src_col in
    select c from (values
      ('unit_number'),
      ('apartment'),
      ('apto'),
      ('ap'),
      ('num'),
      ('numero'),
      ('name')
    ) as t(c)
  loop
    if exists (
      select 1 from information_schema.columns
      where table_schema='public' and table_name='unit' and column_name = src_col
    ) then
      execute format(
        'update unit set number = coalesce(%I::varchar, '''') where number is null or number = ''''',
        src_col
      );
      exit;
    end if;
  end loop;

  -- 3) normaliza constraints
  update unit set number = '' where number is null;
  alter table unit alter column number set not null;
  alter table unit alter column number set default '';

  -- 4) índice único (tenant, condomínio, number, block)
  if not exists (
    select 1 from pg_indexes where schemaname='public' and indexname='uk_unit_tenant_condo_number_block'
  ) then
    execute 'create unique index uk_unit_tenant_condo_number_block
             on unit (tenant_id, condominium_id, number, block)';
  end if;
end
$$;
