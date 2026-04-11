# 🏙️ CondoHub — Sistema de Gestão de Condomínios

[![Backend](https://img.shields.io/badge/Backend-Java%2021%20%7C%20Spring%20Boot-6DB33F)](https://spring.io/projects/spring-boot)
[![Frontend](https://img.shields.io/badge/Frontend-React%20%7C%20TypeScript-3178C6)](https://react.dev/)
[![Database](https://img.shields.io/badge/Database-PostgreSQL-336791)](https://www.postgresql.org/)
[![Build](https://img.shields.io/badge/Build-Vite-646CFF)](https://vitejs.dev/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

## 📌 Sobre o projeto

**CondoHub** é uma plataforma de gestão condominial focada em operação, segurança e escalabilidade.  
O sistema centraliza processos administrativos, financeiros e operacionais em um único painel, atendendo diferentes perfis de acesso como **super admin, admin, síndico, morador, portaria, zelador e financeiro**.

A aplicação foi construída com:

- **Backend:** Java 21 + Spring Boot
- **Frontend:** React + TypeScript + Vite
- **Banco de dados:** PostgreSQL
- **Arquitetura:** multi-tenant com RBAC e trilha de auditoria
- **Financeiro:** módulo de cobranças com suporte a integração **Asaas** e reconciliação

---

## 🚀 Principais funcionalidades

### Administração condominial
- Gestão de **condomínios**
- Gestão de **unidades**
- Gestão de **moradores**
- Gestão de **visitantes**
- Gestão de **usuários e perfis**
- Controle de permissões por papel

### Operação
- **Reservas** de áreas comuns
- **Ordens de serviço**
- **Vagas**
- **Assembleias**
- **Solicitações internas**
- **Auditoria** de ações sensíveis

### Financeiro
- Cadastro e gestão de **cobranças**
- Cobranças por **tipo**, **competência** e **vencimento**
- Dashboard com:
  - total cobrado
  - recebido
  - em aberto
  - vencido
  - inadimplência
- Gráficos de:
  - **inadimplência por bloco**
  - **evolução mensal**
- Busca paginada com filtros server-side
- Portal do morador com:
  - **Minhas faturas**
  - acesso a **boleto**
  - **Pix copia e cola**
  - QR Code quando disponível

### Integração de pagamento
- Integração com **Asaas**
- Criação e consulta de cobranças externas
- Webhook por condomínio
- Reconciliação automática de status
- Preparado para expansão futura para múltiplos gateways

---

## 🧱 Estrutura atual do projeto

```text
condominio/
├── apps/
│   └── api/                  # Backend Spring Boot (Java 21)
│       ├── pom.xml
│       └── src/main/
│           ├── java/
│           └── resources/
│               ├── application.yml
│               └── db/migration/
│
├── src/                      # Frontend React + TypeScript
│   ├── components/
│   ├── pages/
│   ├── hooks/
│   ├── services/
│   └── __tests__/
│
├── package.json
├── pnpm-workspace.yaml
├── README.md
└── README-INSTALL.md
```

---

## 🛠️ Stack principal

### Backend
- Java 21
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- Spring Security
- JWT
- Flyway
- PostgreSQL

### Frontend
- React
- TypeScript
- Vite
- Tailwind CSS
- shadcn/ui
- Recharts
- React Router

### Infra e qualidade
- Docker
- Maven
- pnpm / npm
- Vitest
- JUnit

---

## 🔐 Segurança e arquitetura

O projeto utiliza:

- **Multi-tenancy**
- **RBAC** por perfil
- **JWT** para autenticação
- **Auditoria** de ações relevantes
- Isolamento por condomínio
- Regras de ownership para acesso de moradores aos próprios recursos

### Perfis suportados
- SUPERADMIN
- ADMIN
- SINDICO
- MORADOR
- PORTARIA
- ZELADOR
- FINANCEIRO

> As permissões variam por módulo e por contexto do condomínio.

---

## ⚙️ Configuração de ambiente

### Backend (`apps/api/src/main/resources/application.yml`)
O arquivo principal deve ficar **versionado**, mas sem segredos hardcoded.

Exemplo seguro:

```yaml
condohub:
  financial:
    due-soon-days: ${CONDOHUB_FINANCIAL_DUE_SOON_DAYS:3}
    asaas:
      enabled: ${ASAAS_ENABLED:false}
      api-key: ${ASAAS_API_KEY:}
      base-url: ${ASAAS_BASE_URL:https://api-sandbox.asaas.com}
      webhook-token: ${ASAAS_WEBHOOK_TOKEN:}
```

### Variáveis de ambiente recomendadas

```env
ASAAS_ENABLED=false
ASAAS_API_KEY=
ASAAS_BASE_URL=https://api-sandbox.asaas.com
ASAAS_WEBHOOK_TOKEN=

SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/condo_saas
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SPRING_PROFILES_ACTIVE=dev
```

> **Importante:** nunca commitar chaves, tokens ou segredos no repositório.  
> Use variáveis de ambiente, arquivos locais ignorados pelo Git ou secrets da plataforma de deploy.

---

## ▶️ Como rodar o projeto

### Backend
Na pasta `apps/api`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Ou no Windows:

```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

### Frontend
Na raiz do projeto:

```bash
pnpm install
pnpm dev
```

ou

```bash
npm install
npm run dev
```

### Build do frontend

```bash
pnpm build
```

---

## 🗃️ Banco de dados e migrações

As migrações ficam em:

```text
apps/api/src/main/resources/db/migration
```

O projeto usa **Flyway** para versionamento do schema.

Exemplos de mudanças já tratadas:
- estrutura financeira
- trilha de auditoria
- integração Asaas
- campo `cpf` em `resident`

---

## 💳 Módulo financeiro

O módulo financeiro é um dos principais focos atuais do projeto.

### Recursos já implementados
- listagem paginada de cobranças
- filtros server-side
- dashboard financeiro
- detalhamento de invoices
- portal do morador
- suporte a pagamento manual
- suporte a integração com Asaas
- reconciliação por job
- webhook de atualização de status

### Busca paginada
A busca de invoices foi estruturada com:
- paginação server-side
- filtros opcionais
- ordenação controlada com segurança
- cuidado específico com `native queries` e parâmetros nullable no PostgreSQL

### Regra adotada para queries nativas
Em `nativeQuery = true` com parâmetros opcionais:
- `String` e `enum` nullable usam `CAST(:param AS text)`
- enums são convertidos para `.name()` no service
- o repository recebe `String`, não enum direto

Essa decisão evita erros como:
- `could not determine data type of parameter`
- `lower(bytea) does not exist`

---

## 👤 Portal do morador

O sistema possui experiência específica para moradores, incluindo:

- dashboard com contexto da unidade
- moradores da unidade
- minhas visitas
- minhas reservas
- minhas faturas

No módulo financeiro, o morador consegue visualizar apenas suas cobranças permitidas, com ações como:
- ver boleto
- copiar Pix
- abrir QR Code

---

## 🧪 Testes

### Backend
Na pasta `apps/api`:

```bash
mvn test
```

### Frontend
Na raiz:

```bash
pnpm test
```

ou conforme configuração do projeto:

```bash
vitest run
```

---

## 📈 Status atual do projeto

### Já implementado
- autenticação e perfis
- multi-tenant
- gestão de unidades e moradores
- visitantes
- reservas
- assembleias
- auditoria
- módulo financeiro com dashboard
- portal do morador
- integração inicial com Asaas

### Em evolução
- hardening das listagens e filtros
- consolidação completa do fluxo financeiro externo
- melhorias de UX
- padronização de módulos com paginação server-side
- ampliação de testes de regressão

---

## 🧭 Roadmap sugerido

- estabilização e padronização de listagens
- consolidação completa do fluxo Asaas
- melhoria de dashboard e drill-down financeiro
- otimização de bundle no frontend
- expansão de testes integrados
- suporte futuro a múltiplos gateways de pagamento

---

## 🤝 Contribuição

1. Crie uma branch:
```bash
git checkout -b feature/minha-feature
```

2. Faça suas alterações

3. Rode testes e build

4. Commit:
```bash
git commit -m "feat: minha feature"
```

5. Envie para o repositório:
```bash
git push origin feature/minha-feature
```

---

## 🧑‍💻 Autor

**Kauê Lima Santos**  
GitHub: [kauessz](https://github.com/kauessz)

---

## 📄 Licença

Este projeto está licenciado sob a licença MIT. Veja `LICENSE` para mais detalhes.
