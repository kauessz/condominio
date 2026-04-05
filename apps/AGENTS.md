# Repository Guidelines

## Project Structure & Module Organization
- `api/` — Spring Boot (Java 21). Source in `src/main/java`, resources in `src/main/resources`, tests in `src/test/java`.
- `api-ts/` — Express + TypeScript + Prisma. Feature folders: `controllers/`, `routes/`, `services/`, `middlewares/`, `utils/`; database schema in `prisma/`.
- `web/` — React + Vite + Tailwind. App code in `src/`, static assets in `public/`.

## Build, Test, and Development Commands
- Java API: `cd api`
  - Dev run: `mvn spring-boot:run`
  - Build: `mvn clean package`
  - Tests: `mvn test`
- TS API: `cd api-ts`
  - Install: `pnpm install` (or `npm install`)
  - Dev run: `pnpm dev`
  - Build/Start: `pnpm build && pnpm start`
  - Prisma: `pnpm prisma:migrate`, `pnpm prisma:studio`
- Web: `cd web`
  - Install: `pnpm install` (or `npm install`)
  - Dev run: `pnpm dev`
  - Build/Preview: `pnpm build && pnpm preview`

## Coding Style & Naming Conventions
- Java: 4-space indent; packages `lowercase.dotted`, classes `PascalCase`; suffix Spring classes (`*Controller`, `*Service`, `*Repository`).
- TypeScript/JS: 2-space indent; files `kebab-case.ts`; classes `PascalCase`, variables/functions `camelCase`.
- Linting: `web` uses ESLint (`web/eslint.config.js`). Prefer consistent Prettier-style formatting (no semicolons preference optional).

## Testing Guidelines
- Java API: JUnit via Spring Boot Starter Test. Place tests under `api/src/test/java` named `*Tests.java`. Run with `mvn test`.
- Web: Vitest configured. Place tests under `web/src/__tests__/` or alongside files as `*.test.ts(x)`. Run `pnpm test` (watch: `pnpm test:watch`, coverage: `pnpm test:coverage`).
- TS API: No runner configured yet. Prefer Jest or Vitest; name tests `*.test.ts`.
- Aim for meaningful unit tests around controllers/services and critical UI flows.

## Commit & Pull Request Guidelines
- Use Conventional Commits: `feat(api-ts): short summary`, `fix(web): …`, `chore(api): …`.
- PRs must include: clear description, scope (api/api-ts/web), linked issues, and for UI changes screenshots or GIFs. Add run/verify steps.
- Keep changes focused; separate formatting-only changes.

## Security & Configuration
- Never commit secrets. Use `.env` for `api-ts` and `web`; for `api` configure Spring properties via env vars or external `application-*.properties`.
- Prisma: run migrations locally before pushing schema changes.

## Agent-Specific Tips
- Keep edits scoped to the relevant app (`api`, `api-ts`, or `web`).
- Match existing naming and folder boundaries; avoid cross-app refactors in a single PR.
