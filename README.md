# IntuitWalletService

Spring Boot REST service backed by Temporal workflows and PostgreSQL, plus a small React frontend (POC, scaffold pending). The business problem statement is being written; the first backend feature was `User` (POC stub — see ADR 002 and specs 002–004). The frontend POC is described in ADR 005.

## Repository layout

```
.
├── adrs/         cross-cutting architectural decisions (FE + BE)
├── specs/        feature specs (FE + BE)
├── backend/      Spring Boot service — Gradle root
├── frontend/     React + Vite app — npm root (scaffold lands with the first frontend spec)
├── docker/       docker-compose.yml + Dockerfiles for the full local stack
├── CLAUDE.md     dev guide
└── README.md     this file
```

`backend/` and `frontend/` are peers; `docker/` holds infrastructure; `adrs/` and `specs/` stay at root because they cross-cut both sides. See ADR 005 for the rationale.

## Prerequisites

- JDK 21
- Docker + Docker Compose
- Node.js 20+ and npm (only when working on the frontend)

## Quick start (full stack via Docker)

```bash
cd docker
docker compose up -d --build
```

Once everything is healthy:

| What | Where |
| --- | --- |
| App | http://localhost:8081 |
| Health | http://localhost:8081/actuator/health |
| Swagger UI | http://localhost:8081/swagger-ui/index.html |
| Temporal UI | http://localhost:8233 |
| DB UI (pgweb) | http://localhost:8082 |
| App Postgres | `localhost:5433` (db `wallet`, user `wallet`, password from `docker/.env`) |

Smoke-test the User-create call flow (controller → core service → repository → Postgres; non-orchestrated POC carve-out per ADR 001):

```bash
curl -X POST http://localhost:8081/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","role":"CONSUMER","homeRegion":"us-east-1"}'
```

Expected response: `201 Created` with `{"intuitAccountId":"...","email":"a@b.com","role":"CONSUMER","homeRegion":"us-east-1","createdAt":"...","updatedAt":"..."}`. Repeating the same email returns `200 OK` with the same `intuitAccountId` (idempotent).

### Common Docker commands

All run from `docker/`.

| Task | Command |
| --- | --- |
| Build + start stack | `docker compose up -d --build` |
| Tail app logs | `docker compose logs -f app` |
| Stop | `docker compose down` |
| Stop + wipe DB volume | `docker compose down -v` |
| Restart just the app | `docker compose restart app` |

The app's Dockerfile lives at `backend/Dockerfile` and `docker-compose.yml` references it via `build: ../backend`. The `.env` file (copied from `docker/.env.example`) sets `APP_DB_PASSWORD`.

## Backend: build and test

All Gradle commands run from `backend/`. JDK 21 required; the Gradle wrapper handles the rest.

| Task | Command | What it does |
| --- | --- | --- |
| Compile | `cd backend && ./gradlew compileJava` | Compiles `src/main/java` only. Fastest feedback loop. |
| Run unit tests | `cd backend && ./gradlew test` | Runs the JUnit 5 suite against H2 + Temporal's `TestWorkflowEnvironment`. No Docker required. |
| Build (compile + test + jar) | `cd backend && ./gradlew build` | Full build: compile, run tests, produce `backend/build/libs/*.jar`. |
| Run locally | `cd backend && ./gradlew bootRun` | Boots against an already-running Docker stack (`app-postgres` on `:5433`, `temporal` on `:7233`). |
| Clean | `cd backend && ./gradlew clean` | Wipes `backend/build/`. |

Tests are split by what they exercise:

- **`@DataJpaTest`** for `core` services — H2 in-memory, real JPA, no Spring web stack (`UserCoreServiceTest`, `WalletCoreServiceTest`, `LedgerCoreServiceTest`).
- **`TestWorkflowEnvironment`** for workflows — in-process Temporal test server with mocked activities (`CreateWalletWorkflowTest`, `SendPaymentWorkflowTest`, `FundWalletWorkflowTest`).
- **`@WebMvcTest`** for controllers — MockMvc with mocked `WorkflowClient` / core services (`UserControllerTest`, `WalletControllerTest`, `PaymentControllerTest`).

Full HTTP-through-DB integration is verified by `cd docker && docker compose up -d --build` plus curl, not by an in-JVM Spring Boot integration test (the `@WorkflowImpl` auto-discovery doesn't co-operate cleanly with the in-process test server at this starter version).

`bootRun` expects Postgres on `localhost:5433` and Temporal on `localhost:7233`; the simplest way to get the dependencies up without the app is `cd docker && docker compose up -d app-postgres temporal temporal-postgres temporal-ui pgweb`.

## Frontend (POC, scaffold pending)

The frontend will live under `frontend/` once the first frontend spec lands (ADR 005 → `010-frontend-scaffold-and-login.md`). When that PR is merged:

```bash
cd frontend
npm install
npm run dev
```

…serves on `http://localhost:5173`. It talks directly to the backend on `:8081`; a small CORS config on the backend allows the dev origin (POC-scoped, replaced when auth lands).

Until that spec lands, this directory is empty.

## Where to look

- `CLAUDE.md` — how to develop in this codebase (call flow, package boundaries, conventions).
- `adrs/` — architectural decisions that aren't tied to a single feature.
- `specs/` — feature specs. Every behavior change starts here.
- `backend/src/main/java/com/intuit/walletservice/` — three packages: `service` (API), `businesslogic` (workflows + activities + core), `dal` (entities + repositories).
- `frontend/src/` — React app (post-scaffold).
- `docker/` — `docker-compose.yml`, `.env.example`, future Dockerfiles for sidecars.
