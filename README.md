# IntuitWalletService

Spring Boot REST service backed by Temporal workflows and PostgreSQL. The business problem statement is being written; the scaffold here is ready for the first feature spec.

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
| Temporal UI | http://localhost:8233 |
| App Postgres | `localhost:5433` (db `wallet`, user `wallet`, password from `.env`) |

Smoke-test the full call flow (controller → workflow → activity → core service → repository → Postgres):

```bash
curl -X POST http://localhost:8081/api/ping \
  -H 'Content-Type: application/json' \
  -d '{"message": "hello"}'
```

Expected response: `{"result":"pong","workflowId":"ping-..."}`. The Temporal UI shows the workflow execution; `select * from ping_log;` in the app Postgres shows a row was written.

## Local development

```bash
./gradlew test       # unit tests (uses H2 + Temporal in-process server)
./gradlew bootRun    # run against an already-running docker compose stack
./gradlew build      # full build incl. tests + bootJar
```

`bootRun` expects Postgres on `localhost:5433` and Temporal on `localhost:7233`; the simplest way to get both is `docker compose up -d app-postgres temporal temporal-postgres temporal-ui`.

## Where to look

- `CLAUDE.md` — how to develop in this codebase (call flow, package boundaries, conventions).
- `specs/` — feature specs. Every behavior change starts here.
- `adrs/` — architectural decisions that aren't tied to a single feature.
- `src/main/java/com/intuit/walletservice/` — three packages: `service` (API), `businesslogic` (workflows + activities + core), `dal` (entities + repositories).
