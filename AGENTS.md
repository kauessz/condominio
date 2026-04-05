# AGENTS.md

## Contexto do repositório
Este repositório é um monorepo da plataforma de condomínio.

Estrutura principal:
- apps/api = backend principal em Spring Boot
- apps/web = frontend React/Vite
- apps/api-ts = backend legado
- infra = docker, banco e ambiente local

As convenções técnicas detalhadas das apps ficam em `apps/AGENTS.md`.

## Contexto do produto
O sistema é uma plataforma de condomínio com foco operacional e administrativo.

Prioridade atual:
1. Entrada e Saída
2. Reservas de Áreas Comuns
3. Boletos / Cobranças
4. Assembleias
5. Ordens de Serviço

Depois:
- Contratação de profissionais
- Notificações
- Dashboards
- Integrações e automações

## Regras obrigatórias
- Sempre analisar o código existente antes de alterar arquivos.
- Sempre propor um plano curto antes de implementar mudanças amplas.
- Preferir mudanças pequenas, fáceis de revisar e com baixo risco.
- Não misturar refatorações amplas com entrega funcional no mesmo passo.
- Não usar `apps/api-ts` para novas funcionalidades, salvo se eu pedir explicitamente.
- Considerar `apps/api` como backend principal ativo.
- Considerar `apps/web` como frontend principal ativo.

## Restrições críticas do projeto
- Não quebrar autenticação JWT existente.
- Não quebrar multi-tenant.
- Não quebrar permissões por perfil.
- Não alterar fluxo de login/refresh sem revisar backend e frontend juntos.
- Sempre avaliar impacto de mudanças em:
  - header `X-Tenant`
  - claims do JWT
  - TenantContext
  - interceptors do frontend
  - armazenamento do tenant no browser

## Riscos conhecidos que devem ser revisados antes de mudanças sensíveis
- Validação incompleta entre `X-Tenant` e tenant do JWT
- Possível isolamento incompleto por tenant em queries
- Permissões ainda muito centradas em verbo HTTP
- Auditoria insuficiente
- Desalinhamento de chave de tenant no frontend
- Existência de backend legado no repositório

## Forma de trabalhar
Antes de codar:
1. identificar os arquivos mais relevantes
2. resumir o que existe hoje
3. propor um plano curto
4. só então implementar

Ao implementar:
- alterar apenas o necessário
- evitar mexer em módulos não relacionados
- preservar comportamento já funcional
- sinalizar qualquer dúvida de regra de negócio

Ao finalizar:
- listar arquivos alterados
- resumir o que foi feito
- informar testes executados
- apontar pendências, riscos e próximos passos

## Definição de pronto
Uma tarefa só está pronta quando tiver:
- fluxo principal funcionando de ponta a ponta
- validações essenciais
- permissões por perfil quando aplicável
- tratamento básico de erros
- testes mínimos
- documentação curta de uso ou regra

## Prioridade técnica antes de expandir o P0
Ao tocar autenticação, tenant, permissões ou auditoria:
- revisar backend e frontend juntos
- evitar mudanças amplas sem testes
- preferir correções incrementais