# IntuitWalletService

Spring Boot REST service backed by Temporal workflows and PostgreSQL, plus a small React frontend. The business problem statement is TBD — see `specs/`. The first real backend domain is `User` (POC stub) — see ADR 002 and `specs/002-create-user.md`. The frontend POC architecture is in ADR 005.

## Repository layout

```
.
├── adrs/         cross-cutting architectural decisions (FE + BE)
├── specs/        feature specs (FE + BE)
├── backend/      Spring Boot service — Gradle root
├── frontend/     React + Vite app — npm root
├── docker/       docker-compose.yml + Dockerfiles for the full local stack
├── CLAUDE.md
└── README.md
```

Per ADR 005, `backend/` and `frontend/` are peers; `docker/` holds infrastructure; `adrs/` and `specs/` stay at root because they cross-cut both sides.

## Stack

- Java 21
- Spring Boot 3.3.x
- Gradle (Groovy DSL)
- PostgreSQL 16
- Temporal (Java SDK + `temporal-spring-boot-starter`)
- Flyway for schema migrations
- JUnit 5 + Temporal `TestWorkflowEnvironment` for tests

## Spec-driven workflow (the rule that matters)

Every behavior change starts with a spec under `specs/NNN-kebab-name.md`. Use `specs/000-template.md` as the template. Cross-cutting architectural decisions that aren't tied to a single feature go in `adrs/NNN-kebab-name.md` (template at `adrs/000-template.md`). Reference the spec ID in commit messages.

Lifecycle: `Draft` → `Approved` → `Implemented`. A spec doesn't need to be exhaustive — it needs to be specific enough that the resulting code and tests are unambiguous.

## Call flow (mandatory)

```
client → service (controller + DTO validation)
       → businesslogic (workflow → activity → core)
       → dal (repository → Postgres)
```

Hard rules:
- `service` must not import `dal`. Controllers either start/signal/query workflows (orchestrated flows) or call `core` services directly (non-orchestrated operations); controllers must never call repositories directly. The non-orchestrated path is a POC carve-out for User CRUD — see ADR 001 amendments + ADR 002.
- `businesslogic` must not import `service`.
- Workflows must not call Spring beans or the DB directly. Only activities call `core` services.
- State changes that affect `transactions` or the `ledger` always go through a Temporal workflow.
- API paths are versioned: `/api/v{N}/...`, default `v1`. Major bump on breaking changes only.

## Backend package layout

Under `backend/src/main/java/`:

```
com.intuit.walletservice
├── Application
├── service
│   ├── controller   (@RestController — start/signal/query workflows)
│   ├── dto          (request/response records, Bean Validation annotations)
│   └── validation   (custom @Constraint validators)
├── businesslogic
│   ├── workflow     (@WorkflowInterface + @WorkflowImpl)
│   ├── activity     (@ActivityInterface + @ActivityImpl)
│   └── core         (@Service beans — the actual business rules)
└── dal
    ├── entity       (@Entity JPA classes)
    └── repository   (Spring Data JpaRepository interfaces)
```

## Frontend layout

Under `frontend/src/` (when scaffolded — see ADR 005):

```
src/
├── main.tsx          ReactDOM entry
├── App.tsx           root layout: nav + <Routes>
├── api/              one file per backend resource (users, wallets, payments)
├── types/api.ts      hand-written TS interfaces matching Java DTOs
├── pages/            one file per route
├── components/       hand-rolled primitives (Field, Button, ErrorBox, ...)
└── index.css         single global stylesheet
```

## Implementing a new feature

Follow the call flow bottom-up so each layer has a green build before the next is added:

1. Write the spec at `specs/NNN-kebab-name.md`.
2. If an architectural choice falls out of it, add `adrs/NNN-kebab-name.md`.
3. **dal**: add a Flyway migration `backend/src/main/resources/db/migration/V<n>__<name>.sql`, the `@Entity`, and the `JpaRepository`.
4. **businesslogic.core**: add an `@Service` with the rules. Keep it free of Temporal annotations.
5. **businesslogic.activity** (orchestrated only): define the `@ActivityInterface` and an `@ActivityImpl(workers = "wallet-service-worker")` that delegates to the core service.
6. **businesslogic.workflow** (orchestrated only): define the `@WorkflowInterface` and `@WorkflowImpl(workers = "wallet-service-worker")` that orchestrates activities.
7. **service**: add request/response DTOs and a `@RestController`. For orchestrated features, submit via the injected `WorkflowClient`. For non-orchestrated features (POC carve-out, e.g. User CRUD), inject the `core` `@Service` and call it directly.
8. Tests: use `TestWorkflowEnvironment` when a workflow is involved; otherwise use `@DataJpaTest` for the core service (see `UserCoreServiceTest`) and `@WebMvcTest` for the controller (see `UserControllerTest`).

Steps 5–6 apply only to features that need orchestration (multi-step, retryable, saga-bearing, or any state change affecting transactions / ledger). Non-orchestrated features skip them.

## Commands

All Gradle commands run from `backend/`. All docker-compose commands run from `docker/`.

| Task | Command |
| --- | --- |
| Compile (backend) | `cd backend && ./gradlew compileJava` |
| Run unit tests (backend) | `cd backend && ./gradlew test` |
| Build (compile + test + jar) | `cd backend && ./gradlew build` |
| Run backend locally | `cd backend && ./gradlew bootRun` |
| Build + start full stack | `cd docker && docker compose up -d --build` |
| Tail app logs | `cd docker && docker compose logs -f app` |
| Stop full stack | `cd docker && docker compose down` |
| Reset DB volume | `cd docker && docker compose down -v` |
| Run frontend dev server natively (faster HMR) | `cd frontend && npm run dev` |
| Re-build just the frontend image | `cd docker && docker compose up -d --build frontend` |

Once `cd docker && docker compose up -d` is running:
- Frontend: `http://localhost:5173`
- App: `http://localhost:8081` (e.g. `POST /api/v1/users {"email":"a@b.com","role":"CONSUMER","homeRegion":"us-east-1"}`)
- Actuator health: `http://localhost:8081/actuator/health`
- Swagger UI: `http://localhost:8081/swagger-ui/index.html`
- Temporal UI: `http://localhost:8233`
- DB UI (pgweb): `http://localhost:8082`

## Conventions

- Constructor injection only — no `@Autowired` field injection.
- No Lombok.
- Package-private by default; widen visibility only when another package needs it.
- One Flyway migration per spec.
- Tests that need a workflow runtime use Temporal's `TestWorkflowEnvironment`.
- The full HTTP-through-DB integration is verified by running `docker compose up` and exercising the app, not by an in-JVM Spring Boot integration test (the `@WorkflowImpl` auto-discovery doesn't co-operate cleanly with the in-process test server in this starter version).

## Definition of done

- Spec exists and references the commit(s).
- `./gradlew build` is clean.
- New code is covered by tests.
- Manually smoke-tested via `docker compose up -d`.
