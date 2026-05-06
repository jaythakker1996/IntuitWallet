# IntuitWalletService

Spring Boot REST service backed by Temporal workflows and PostgreSQL. The business problem statement is TBD — see `specs/`. Until the first feature spec lands, the only non-infrastructure code is a `Ping` flow that exercises every layer end-to-end.

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
- `service` must not import `dal`. Controllers only start/signal/query workflows.
- `businesslogic` must not import `service`.
- Workflows must not call Spring beans or the DB directly. Only activities call `core` services.
- State changes that affect `transactions` or the `ledger` always go through a Temporal workflow.

## Package layout

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

## Implementing a new feature

Follow the call flow bottom-up so each layer has a green build before the next is added:

1. Write the spec at `specs/NNN-kebab-name.md`.
2. If an architectural choice falls out of it, add `adrs/NNN-kebab-name.md`.
3. **dal**: add a Flyway migration `src/main/resources/db/migration/V<n>__<name>.sql`, the `@Entity`, and the `JpaRepository`.
4. **businesslogic.core**: add an `@Service` with the rules. Keep it free of Temporal annotations.
5. **businesslogic.activity**: define the `@ActivityInterface` and an `@ActivityImpl(workers = "wallet-service-worker")` that delegates to the core service.
6. **businesslogic.workflow**: define the `@WorkflowInterface` and `@WorkflowImpl(workers = "wallet-service-worker")` that orchestrates activities.
7. **service**: add request/response DTOs and a `@RestController` that submits the workflow via the injected `WorkflowClient`.
8. Tests: a `TestWorkflowEnvironment`-based unit test for the workflow (see `PingWorkflowTest`), plus repository/service unit tests as needed.

## Commands

| Task | Command |
| --- | --- |
| Compile | `./gradlew compileJava` |
| Run unit tests | `./gradlew test` |
| Build (compile + test + jar) | `./gradlew build` |
| Run locally | `./gradlew bootRun` |
| Build + start full stack | `docker compose up -d --build` |
| Tail app logs | `docker compose logs -f app` |
| Stop full stack | `docker compose down` |
| Reset DB volume | `docker compose down -v` |

Once `docker compose up -d` is running:
- App: `http://localhost:8081` (e.g. `POST /api/ping {"message":"hi"}`)
- Actuator health: `http://localhost:8081/actuator/health`
- Temporal UI: `http://localhost:8233`

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
