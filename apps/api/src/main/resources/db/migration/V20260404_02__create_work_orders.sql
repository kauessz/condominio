-- Módulo 3: Ordens de Serviço
create table if not exists work_order_category (
  id          bigserial primary key,
  name        varchar(120) not null,
  icon        varchar(64),
  color       varchar(32) default '#6366f1',
  sort_order  int not null default 0
);

create table if not exists work_order_subcategory (
  id          bigserial primary key,
  category_id bigint not null references work_order_category(id),
  name        varchar(120) not null,
  sla_hours   int not null default 48,
  sort_order  int not null default 0
);

create table if not exists work_order (
  id              bigserial primary key,
  tenant_id       varchar(64) not null,
  condominium_id  bigint not null references condominium(id),
  unit_id         bigint references unit(id),
  category_id     bigint not null references work_order_category(id),
  subcategory_id  bigint references work_order_subcategory(id),
  title           varchar(200) not null,
  description     text,
  status          varchar(32) not null default 'OPEN',
  priority        varchar(32) not null default 'MEDIUM',
  sla_deadline    timestamptz,
  assigned_to     bigint references users(id),
  resolved_at     timestamptz,
  closed_at       timestamptz,
  created_by      bigint references users(id),
  created_at      timestamptz default now(),
  updated_at      timestamptz default now()
);
create index if not exists idx_work_order_tenant on work_order(tenant_id);
create index if not exists idx_work_order_condo  on work_order(condominium_id);
create index if not exists idx_work_order_status on work_order(status);

create table if not exists work_order_update (
  id            bigserial primary key,
  work_order_id bigint not null references work_order(id),
  author_id     bigint references users(id),
  author_name   varchar(200),
  content       text not null,
  new_status    varchar(32),
  created_at    timestamptz default now()
);
create index if not exists idx_wo_update_order on work_order_update(work_order_id);

-- Seed: categorias pré-programadas
insert into work_order_category(name, icon, color, sort_order) values
  ('Hidráulica',     'droplet',      '#3b82f6', 1),
  ('Elétrica',       'zap',          '#f59e0b', 2),
  ('Estrutura',      'home',         '#6b7280', 3),
  ('Limpeza',        'sparkles',     '#10b981', 4),
  ('Portão',         'door-open',    '#8b5cf6', 5),
  ('Elevador',       'arrow-up',     '#ec4899', 6),
  ('Área Comum',     'users',        '#06b6d4', 7),
  ('Gás',            'flame',        '#f97316', 8),
  ('Internet/CFTV',  'wifi',         '#0ea5e9', 9),
  ('Outro',          'wrench',       '#9ca3af', 10)
on conflict do nothing;

-- Seed: subcategorias com SLA
insert into work_order_subcategory(category_id, name, sla_hours, sort_order)
select c.id, s.name, s.sla_hours, s.sort_order
from work_order_category c
join lateral (values
  -- Hidráulica
  ('Hidráulica', 'Vazamento de água', 4, 1),
  ('Hidráulica', 'Entupimento', 8, 2),
  ('Hidráulica', 'Torneira com defeito', 48, 3),
  ('Hidráulica', 'Caixa d''água', 24, 4),
  -- Elétrica
  ('Elétrica', 'Tomada sem funcionamento', 24, 1),
  ('Elétrica', 'Iluminação', 8, 2),
  ('Elétrica', 'Disjuntor', 4, 3),
  ('Elétrica', 'Gerador', 4, 4),
  -- Estrutura
  ('Estrutura', 'Trinca ou fissura', 72, 1),
  ('Estrutura', 'Infiltração', 48, 2),
  ('Estrutura', 'Piso danificado', 72, 3),
  -- Limpeza
  ('Limpeza', 'Áreas comuns', 8, 1),
  ('Limpeza', 'Descarte irregular', 4, 2),
  ('Limpeza', 'Desinfeção', 24, 3),
  -- Portão
  ('Portão', 'Portão eletrônico', 4, 1),
  ('Portão', 'Interfone', 8, 2),
  ('Portão', 'Controle remoto', 24, 3),
  -- Elevador
  ('Elevador', 'Manutenção preventiva', 72, 1),
  ('Elevador', 'Parado/preso', 2, 2),
  ('Elevador', 'Barulho anormal', 24, 3),
  -- Área Comum
  ('Área Comum', 'Piscina', 24, 1),
  ('Área Comum', 'Academia/Salão', 48, 2),
  ('Área Comum', 'Playground', 72, 3),
  -- Gás
  ('Gás', 'Vazamento de gás', 2, 1),
  ('Gás', 'Medidor', 24, 2),
  -- Internet/CFTV
  ('Internet/CFTV', 'Câmera com defeito', 48, 1),
  ('Internet/CFTV', 'Rede sem sinal', 24, 2),
  ('Internet/CFTV', 'Acesso ao NVR', 48, 3),
  -- Outro
  ('Outro', 'Outro problema', 72, 1)
) as s(cat_name, name, sla_hours, sort_order) on c.name = s.cat_name
on conflict do nothing;
