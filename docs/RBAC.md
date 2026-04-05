# RBAC oficial do CondoHub

Fonte de verdade: backend Spring Boot em `apps/api`.

## Roles oficiais

- `SUPERUSER`: escopo global da plataforma; pode operar todos os condomínios do tenant.
- `ADMIN`: gestão administrativa do próprio condomínio.
- `SINDICO`: gestão do próprio condomínio.
- `FINANCEIRO`: operação financeira do próprio condomínio.
- `OPERADOR`: operação administrativa do próprio condomínio.
- `ZELADOR`: manutenção, reservas e ordens de serviço do próprio condomínio.
- `PORTARIA`: visitantes, check-in, check-out e rotinas de acesso do próprio condomínio.
- `MORADOR`: portal da própria unidade.

`VISITANTE` não é role autenticada do sistema. É entidade operacional do domínio.

## Onde a role é definida

- Enum oficial: `apps/api/src/main/java/com/example/condo/security/Role.java`
- Conversão legado -> oficial: `apps/api/src/main/java/com/example/condo/persistence/RoleCodeConverter.java`
- Regras web: `apps/api/src/main/java/com/example/condo/config/SecurityConfig.java`
- Labels da UI: `apps/web/src/lib/auth.ts`
- Seeds dev/test: `apps/api/src/main/java/com/example/condo/bootstrap/DevAdminSeed.java`

## Observações

- Toda role exibida na UI deve existir no enum do backend.
- `FINANCEIRO` e `OPERADOR` são roles oficiais e permanecem ativas.
- Regras finas de escopo continuam nos services; `SecurityConfig` faz o gate HTTP inicial.

## Matriz consolidada desta fase

- `SUPERUSER`
  - condomínio: `CREATE`, `UPDATE`, `DELETE`, `APPROVE`, `ACTIVATE`, `DEACTIVATE`
  - unidade: `VIEW`, `CREATE`, `UPDATE`, `DELETE`
  - morador: `VIEW`, `CREATE`, `UPDATE`, `DELETE`
  - visitante: `VIEW`, `CREATE`, `UPDATE`, `DELETE`, `APPROVE`, `REJECT`, `CHECK_IN`, `CHECK_OUT`
  - assembleias: gestão total
  - financeiro: gestão total
  - usuários: gestão total
  - vagas: gestão total
  - reservas: gestão total

- `ADMIN`
  - condomínio: `REQUEST_CREATE`, `REQUEST_UPDATE`, `REQUEST_DELETE`, `REQUEST_ACTIVATE`, `REQUEST_DEACTIVATE`
  - unidade: `VIEW`, `CREATE`, `UPDATE`, `DELETE` no próprio condomínio
  - morador: `VIEW`, `CREATE`, `UPDATE`, `DELETE` no próprio condomínio
  - visitante: `VIEW`, `CREATE`, `UPDATE`, `DELETE`, `APPROVE`, `REJECT`, `CHECK_IN`, `CHECK_OUT` no próprio condomínio
  - assembleias / financeiro / usuários / vagas / reservas: gestão do próprio condomínio

- `SINDICO`
  - condomínio: `REQUEST_CREATE`, `REQUEST_UPDATE`, `REQUEST_DELETE`, `REQUEST_ACTIVATE`, `REQUEST_DEACTIVATE`
  - unidade: `VIEW`, `CREATE`, `UPDATE`, `DELETE` no próprio condomínio
  - morador: `VIEW`, `CREATE`, `UPDATE`, `DELETE` no próprio condomínio
  - visitante: `VIEW`, `CREATE`, `UPDATE`, `CHECK_IN`, `CHECK_OUT`; `APPROVE` só se `allowSyndicApproveVisitor=true`
  - assembleias / financeiro / usuários / vagas / reservas: gestão do próprio condomínio

- `MORADOR`
  - condomínio / unidade: apenas `VIEW` do próprio contexto
  - morador: `VIEW`, `CREATE`, `UPDATE` apenas da própria unidade
  - visitante: `CREATE`, `APPROVE`, `REJECT`, `CANCEL` da própria unidade
  - assembleias: `VIEW`, `VOTE` quando elegível
  - financeiro: `VIEW` apenas das próprias cobranças
  - reservas / vagas: apenas próprio escopo

- `PORTARIA`
  - condomínio / unidade / morador: apenas `VIEW`
  - visitante: `VIEW`, `CREATE`, `UPDATE` operacional, `CHECK_IN`, `CHECK_OUT`, `CANCEL`
  - aprovação de visitante: negada por padrão, salvo configuração explícita do condomínio
  - sem assembleias, sem mutação estrutural

- `ZELADOR`
  - foco em manutenção e operação predial
  - sem mutação estrutural de condomínio, unidade ou morador
  - sem assembleias por padrão

- `FINANCEIRO`
  - foco em cobranças, lançamentos, baixas e configurações financeiras do próprio condomínio
  - sem mutação estrutural de condomínio, unidade ou morador

- `OPERADOR`
  - papel oficial de backoffice operacional
  - leitura ampliada e apoio em solicitações, visitantes, reservas e ordens de serviço
  - sem aprovação estrutural, sem assembleias por padrão e sem financeiro sensível por padrão

## Tipos oficiais de cobrança

Fonte de verdade: `apps/api/src/main/java/com/example/condo/entity/Invoice.java`

- `CONDOMINIO`: mensalidade ordinária
- `REFORMA`: rateio de obra ou reforma
- `EXTRA`: taxa extraordinária
- `FUNDO_RESERVA`: fundo de reserva
- `MULTA`: multa
- `OUTROS`: casos não padronizados
