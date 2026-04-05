# AGENTS.md

## Escopo deste arquivo
Este arquivo cobre a pasta `apps/`.

Os caminhos abaixo são relativos a `apps/`:
- `api/` = backend Spring Boot principal
- `web/` = frontend React/Vite principal
- `api-ts/` = backend legado

## Regra de prioridade técnica
- Novas funcionalidades devem ser implementadas preferencialmente em `api/` e `web/`.
- `api-ts/` é legado e não deve receber novas features sem pedido explícito.
- Não misturar alterações nas três apps em uma mesma entrega sem necessidade.

## Backend principal (`api/`)
Stack principal:
- Java 21
- Spring Boot
- Spring Security
- JPA / Hibernate
- Flyway

Ao trabalhar no backend principal:
- revisar controllers, services, repositories e entities relacionados
- respeitar autenticação JWT e contexto de tenant
- evitar queries sem filtro de tenant quando a entidade for multi-tenant
- revisar impacto em permissões e roles
- revisar impacto em migrations antes de alterar schema

### Antes de alterar autenticação, tenant ou autorização
Sempre verificar:
- filtros de segurança
- geração e leitura do JWT
- `X-Tenant`
- `TenantContext`
- services que consultam por tenant
- possíveis reflexos no frontend

## Frontend principal (`web/`)
Stack principal:
- React
- Vite
- TypeScript
- Axios

Ao trabalhar no frontend:
- usar a camada centralizada de API existente
- preservar interceptors, auth e fluxo de sessão
- conferir a chave usada para armazenar tenant e token
- validar impacto em guards, hooks e chamadas autenticadas

## Backend legado (`api-ts/`)
- tratar como legado
- não criar novas regras de negócio aqui
- só alterar se houver necessidade explícita ou correção pontual orientada

## Forma de trabalho
Antes de implementar:
1. localizar os arquivos relevantes
2. explicar rapidamente o fluxo atual
3. propor uma alteração pequena e segura
4. implementar
5. revisar o próprio diff

## Qualidade mínima
Cada entrega deve ter, quando aplicável:
- código consistente com o padrão existente
- validações essenciais
- testes mínimos
- resumo objetivo no final
- baixo acoplamento
- sem segredos hardcoded

## Ao finalizar
Sempre retornar:
- arquivos alterados
- motivo de cada alteração
- testes executados
- riscos restantes