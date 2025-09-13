# Condo Hardening Pack — Como integrar

## 1) Copie os arquivos
- **API (Java/Spring)** → coloque o conteúdo de `apps/api/` dentro de `apps/api/` do seu projeto.
  - Se já existirem classes com o mesmo nome, compare e **mescle**.
- **Infra** → copie `infra/` (ou só `.env.example` e `docker-compose.yml` se já tiver pasta).
- **Flyway** → garanta que `src/main/resources/db/migration/V1__init.sql` está presente.

## 2) Ajuste o `pom.xml`
- Adicione as dependências de **Security**, **Validation**, **Springdoc**, **Flyway** e **JJWT**.
  - Veja `apps/api/pom-additions.txt` neste pacote.

## 3) Configuração (`application.yml`)
- Use o arquivo deste pack (ou mescle com o seu):
  - `spring.jpa.hibernate.ddl-auto=validate` (para Flyway comandar o esquema).
  - Config de **Flyway** e `app.jwt.*`.

## 4) Suba o banco (dev)
```bash
cd infra
cp .env.example .env
docker compose up -d
```
- Postgres em `localhost:5432`
- pgAdmin em `http://localhost:5050`

## 5) Rode a API
```bash
cd apps/api
./mvnw spring-boot:run
```
- Flyway executará `V1__init.sql` automaticamente.
- Swagger em: `http://localhost:8080/swagger-ui/index.html`

## 6) Teste rápido (Postman ou curl)
- **Login (stub):**
```bash
curl -X POST http://localhost:8080/auth/login   -H "Content-Type: application/json"   -d '{"email":"admin@demo.com","password":"123"}'
```
Copie o `token` da resposta.

- **Criar condomínio:**
```bash
curl -X POST http://localhost:8080/api/condominiums   -H "Authorization: Bearer SEU_TOKEN"   -H "X-Tenant-ID: demo"   -H "Content-Type: application/json"   -d '{"name":"Condomínio Alpha","cnpj":"00.000.000/0000-00"}'
```

- **Listar por tenant:**
```bash
curl -H "Authorization: Bearer SEU_TOKEN" -H "X-Tenant-ID: demo"   http://localhost:8080/api/condominiums
```

- **Cobrança (stub):**
```bash
curl -X POST http://localhost:8080/api/payments/invoices/1/charge   -H "Authorization: Bearer SEU_TOKEN" -H "X-Tenant-ID: demo"
```

## 7) O que você deve substituir depois
- `AuthController.login` → trocar stub por **validação real** (users na tabela, `BCryptPasswordEncoder`).
- `PaymentController` → implementar integração com **Asaas/Gerencianet** e persistir `invoice/payment`.
- `BaseEntity`/`TenantEntityListener` → manter; eles setam `tenant_id` automaticamente.
- `Repository` → sempre filtrar por `tenant_id` (use métodos tipo `findAllByTenant(...)`).
- **Perfis** → aplique `@PreAuthorize` conforme regra de negócios (RESIDENT/MANAGER/ADMIN).
- **JWT_SECRET** → setar valor seguro em produção (e rotacionar).

## 8) Front-end (Next.js)
- Em cada request, enviar `X-Tenant-ID` e `Authorization: Bearer ...`
- Definir `NEXT_PUBLIC_API_URL` (ex.: `http://localhost:8080`).
