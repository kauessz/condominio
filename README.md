# 🏙️ Condomínio — Sistema de Gestão de Condomínios

[![Spring Boot](https://img.shields.io/badge/Backend-SpringBoot-green.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/Frontend-React-blue.svg)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/Language-TypeScript-3178C6.svg)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL-336791.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Container-Docker-2496ED.svg)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

## 📌 Sobre o Projeto

**Condomínio** é um sistema completo para gestão de condomínios residenciais e comerciais.  
O objetivo é fornecer uma plataforma moderna, segura e escalável para administradores, síndicos e moradores, centralizando serviços em um único painel.

A aplicação possui **backend em Spring Boot** e **frontend com React + TypeScript (TS/TSX)**, utilizando **PostgreSQL** em containers **Docker**.

---

## 🚀 Principais Funcionalidades

- 👤 **Moradores** — cadastro, listagem e gerenciamento por unidade  
- 🏠 **Unidades** — organização por blocos e apartamentos, com status de ocupação  
- 👥 **Visitantes** — controle de visitantes, registro de entrada/saída  
- 📅 **Reservas** — agendamento de espaços (salão, churrasqueira, etc.)  
- 💳 **Boletos** — geração/consulta de boletos de taxas condominiais  
- 🛠️ **Reparos** — abertura e acompanhamento de ordens de serviço  
- 📢 **Reclamações** — canal interno para solicitações e reclamações  
- 🔐 **Autenticação & Multi‑Tenant** — suporte a múltiplos condomínios via cabeçalho `X-Tenant`  
- 📊 **Dashboard** — indicadores e relatórios

---

## 🧱 Arquitetura e Estrutura

```
condominio/
├── backend/                # API em Spring Boot (Java 17+)
│   ├── src/main/java/…     # Código-fonte do backend
│   ├── src/main/resources/ # application.yml, mapeamentos, etc.
│   └── Dockerfile          # Container do backend
│
├── frontend/               # Aplicação React (Vite + TS/TSX)
│   ├── src/                # Páginas, componentes e hooks
│   ├── public/             # Assets estáticos
│   └── Dockerfile          # Container do frontend
│
├── docker-compose.yml      # Orquestração (API + Front + PostgreSQL)
└── README.md               # Este arquivo
```

**Stack principal**  
- **Backend:** Spring Boot, Spring Web, Spring Data JPA, (opcional: Spring Security + JWT), Java 17  
- **Banco:** PostgreSQL 14+  
- **Frontend:** React 18, Vite, TypeScript (TS/TSX), Axios  
- **Infra:** Docker, Docker Compose

---

## ⚙️ Pré‑requisitos

- [Node.js 18+](https://nodejs.org/) (ou compatível)
- [Java 17+](https://adoptium.net/)
- [Docker + Docker Compose](https://www.docker.com/)
- [Git](https://git-scm.com/)

---

## 🔧 Configuração (um bloco único para todo o ambiente)

### 1) Clonar o repositório

```bash
git clone https://github.com/kauessz/condominio.git
cd condominio
```

### 2) Variáveis de ambiente

Crie um arquivo **`.env`** na **raiz** do projeto com os valores abaixo (ajuste conforme necessário):

```env
# ---------- Banco de Dados ----------
POSTGRES_USER=admin
POSTGRES_PASSWORD=admin
POSTGRES_DB=condominio_db
POSTGRES_PORT=5432

# ---------- Backend (Spring) ----------
SPRING_DATASOURCE_URL=jdbc:postgresql://db:${POSTGRES_PORT}/${POSTGRES_DB}
SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
SPRING_PROFILES_ACTIVE=dev

# Multi-tenant: valor padrão do cabeçalho X-Tenant (fallback opcional)
DEFAULT_TENANT=demo

# ---------- Frontend (Vite) ----------
VITE_API_URL=http://localhost:8080/api
```

> **Dica:** garanta que as portas **3000** (frontend), **8080** (backend) e **5432** (Postgres) estejam livres.

### 3) Subir tudo com Docker

```bash
docker-compose up --build
```

- 🌐 Frontend: `http://localhost:3000`  
- 🖥️ Backend: `http://localhost:8080`  
- 🗄️ Postgres: `localhost:5432` (usuário/senha de acordo com `.env`)

> **Parar**: `Ctrl+C` e depois `docker-compose down` para encerrar os containers.  
> **Recriar do zero**: `docker-compose down -v && docker-compose up --build` (remove volumes).

---

## 🧪 Execução para Desenvolvimento (sem Docker)

> Útil para iterar rápido em uma das pontas.

### Backend

```bash
cd backend
# Linux/Mac:
./mvnw spring-boot:run
# Windows:
mvnw.cmd spring-boot:run
```

A API sobe em `http://localhost:8080`. Configure `application.yml`/`.properties` ou use as variáveis do `.env`.

### Frontend

```bash
cd frontend
# Gerenciador de pacotes a sua escolha:
npm install   # ou: pnpm install | yarn
npm run dev   # ou: pnpm dev | yarn dev
```

A aplicação abre em `http://localhost:3000` e consome `VITE_API_URL`.

---

## 🔐 Autenticação e Multi‑Tenant

- **JWT** para autenticação/autorização (quando habilitado no projeto)  
- **Cabeçalho `X-Tenant`** identifica o condomínio do request (ex.: `X-Tenant: demo`)  
- É recomendável validar papéis (admin, síndico, morador) nas rotas protegidas

Exemplo de chamada no frontend (Axios):
```ts
import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? "http://localhost:8080/api",
});

// Fallback de tenant (opcional)
api.defaults.headers.common["X-Tenant"] = import.meta.env.VITE_DEFAULT_TENANT ?? "demo";

export default api;
```

---

## 📚 Rotas Principais da API (exemplos)

| Recurso          | Endpoint             | Métodos                       | Descrição                                     |
|------------------|----------------------|-------------------------------|-----------------------------------------------|
| Moradores        | `/api/moradores`     | GET, POST, PUT, DELETE        | CRUD de moradores                             |
| Unidades         | `/api/unidades`      | GET, POST, PUT, DELETE        | Cadastro/listagem de unidades                 |
| Visitantes       | `/api/visitantes`    | GET, POST                     | Registro e consulta de visitantes             |
| Reservas         | `/api/reservas`      | GET, POST                     | Agendamento de espaços comuns                 |
| Boletos          | `/api/boletos`       | GET, POST                     | Geração e consulta de boletos                 |
| Reparos          | `/api/reparos`       | GET, POST, PUT                | Solicitação e atualização de ordens de serviço|
| Reclamações      | `/api/reclamacoes`   | GET, POST                     | Registro e acompanhamento de reclamações      |
| Autenticação (*) | `/api/auth/login`    | POST                          | Autenticação por credenciais                  |

> (*) A rota de autenticação pode variar conforme sua implementação.

---

## 🗃️ Migrações de Banco (opcional)

Se utilizar **Flyway** ou **Liquibase**, mantenha os scripts de migração em `backend/src/main/resources/db/migration` (ou pasta equivalente) para versionar o schema do banco.

---

## 🧩 Convenções & Scripts úteis

- Commits semânticos (ex.: `feat:`, `fix:`, `chore:`, `docs:`)  
- Back-end (Maven): `mvn test`, `mvn package`, `mvn spring-boot:run`  
- Front-end (Vite): `npm run dev`, `npm run build`, `npm run preview`

---

## 📈 Roadmap

- ✅ CRUD de moradores, visitantes e unidades  
- ✅ Reservas com visualização por calendário  
- 🚧 Integração com gateway de pagamento (boletos)  
- 🚧 Notificações por e-mail/push  
- 🚧 App mobile (React Native)

---

## 🤝 Contribuindo

1. Faça um **fork** do repositório  
2. Crie uma branch: `git checkout -b feature/minha-feature`  
3. Commit: `git commit -m "feat: adiciona minha feature"`  
4. Push: `git push origin feature/minha-feature`  
5. Abra um **Pull Request**

---

## 🧑‍💻 Autor

**Kauê Santos**  
GitHub: https://github.com/kauessz  
E-mail: ssz.kaue@gmail.com
Demo pública: https://condo-landing.netlify.app

---

## 📄 Licença

Distribuído sob a **MIT License**. Veja `LICENSE` para mais informações.
