-- 1) Adiciona/normaliza coluna block
alter table unit add column if not exists block varchar(50);
update unit set block = '' where block is null;
alter table unit alter column block set not null;
alter table unit alter column block set default '';

-- 2) Garante que exista a coluna "number" (renomeia legados comuns)
do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'unit' and column_name = 'number'
  ) then
    if exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'unit' and column_name = 'unit_number'
    ) then
      execute 'alter table unit rename column unit_number to number';
    elsif exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'unit' and column_name = 'num'
    ) then
      execute 'alter table unit rename column num to number';
    elsif exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'unit' and column_name = 'numero'
    ) then
      execute 'alter table unit rename column numero to number';
    end if;
  end if;
end
$$;

-- 3) Cria o índice único somente se as colunas existirem
do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema='public' and table_name='unit' and column_name='number'
  ) and exists (
    select 1 from information_schema.columns
    where table_schema='public' and table_name='unit' and column_name='block'
  ) then
    execute 'create unique index if not exists uk_unit_tenant_condo_number_block
             on unit (tenant_id, condominium_id, number, block)';
  else
    raise notice 'Pulando criação do índice: coluna number ou block ausente em unit';
  end if;
end
$$;