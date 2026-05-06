# IntuitWalletService

Spring Boot REST service backed by Temporal workflows and PostgreSQL. The business problem statement is being written; the first feature is `User` (POC stub — see ADR 002 and specs 002–004).

## Prerequisites

- JDK 21
- Docker + Docker Compose

## Quick start

```bash
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
| App Postgres | `localhost:5433` (db `wallet`, user `wallet`, password from `.env`) |

Smoke-test the User-create call flow (controller → core service → repository → Postgres; non-orchestrated POC carve-out per ADR 001):

```bash
curl -X POST http://localhost:8081/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","role":"CONSUMER","homeRegion":"us-east-1"}'
```

Expected response: `201 Created` with `{"intuitAccountId":"...","email":"a@b.com","role":"CONSUMER","homeRegion":"us-east-1","createdAt":"...","updatedAt":"..."}`. Repeating the same email returns `200 OK` with the same `intuitAccountId` (idempotent). `select * from users;` in the app Postgres shows the row.

## Build and test

Run from the repo root. JDK 21 is required; the Gradle wrapper handles everything else.

| Task | Command | What it does |
| --- | --- | --- |
| Compile | `./gradlew compileJava` | Compiles `src/main/java` only. Fastest feedback loop. |
| Run unit tests | `./gradlew test` | Runs the full JUnit 5 suite against H2 + Temporal's `TestWorkflowEnvironment`. No Docker required. |
| Build (compile + test + jar) | `./gradlew build` | Full build: compile, run tests, produce `build/libs/*.jar`. This is what CI runs. |
| Run locally | `./gradlew bootRun` | Boots the app against an already-running Docker stack (Postgres on `:5433`, Temporal on `:7233`). |
| Clean | `./gradlew clean` | Wipes `build/` (use if a stale class file is causing confusion). |

Tests are intentionally split by what they exercise:

- **`@DataJpaTest`** for `core` services — H2 in-memory, real JPA, no Spring web stack (`UserCoreServiceTest`, `WalletCoreServiceTest`).
- **`TestWorkflowEnvironment`** for workflows — in-process Temporal test server with mocked activities (`CreateWalletWorkflowTest`).
- **`@WebMvcTest`** for controllers — MockMvc with mocked `WorkflowClient` / core services (`UserControllerTest`, `WalletControllerTest`).

Full HTTP-through-DB integration is verified by `docker compose up -d --build` and curl, not by an in-JVM Spring Boot integration test (the `@WorkflowImpl` auto-discovery doesn't co-operate cleanly with the in-process test server in this starter version).

`bootRun` expects Postgres on `localhost:5433` and Temporal on `localhost:7233`; the simplest way to get both is `docker compose up -d app-postgres temporal temporal-postgres temporal-ui`.

## Where to look

- `CLAUDE.md` — how to develop in this codebase (call flow, package boundaries, conventions).
- `specs/` — feature specs. Every behavior change starts here.
- `adrs/` — architectural decisions that aren't tied to a single feature.
- `src/main/java/com/intuit/walletservice/` — three packages: `service` (API), `businesslogic` (workflows + activities + core), `dal` (entities + repositories).
