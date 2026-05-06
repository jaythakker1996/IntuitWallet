# 001 — Tech stack and three-package architecture

- **Status:** Accepted
- **Date:** 2026-05-06
- **Author:** IntuitWalletService bootstrap

## Context

A new service has to be stood up before the business problem statement is written. We need a stack and an architectural shape that can absorb the future spec without rework, and we want spec-driven development to be the norm from day one.

## Decision

### Stack

- **Language / runtime:** Java 21 (LTS).
- **Framework:** Spring Boot 3.3.x.
- **Build tool:** Gradle (Groovy DSL) with the wrapper checked in.
- **Datastore:** PostgreSQL 16, schema managed by Flyway.
- **Workflow engine:** Temporal Java SDK via `temporal-spring-boot-starter`.
- **Tests:** JUnit 5 + Temporal `TestWorkflowEnvironment`. H2 is used in tests as a Postgres-compatible in-memory DB; CI/dev verification of the full stack happens via `docker compose up`.
- **Packaging / runtime infra:** self-contained `docker-compose.yml` with the app, its Postgres, Temporal, Temporal's Postgres, and the Temporal UI. Independent from any sibling service in this repo.

### Architecture: three top-level packages

The codebase is split into exactly three top-level packages, each owning one stage of the request lifecycle:

| Package | Responsibility |
| --- | --- |
| `service` | API surface — controllers, DTOs, input/output validation. |
| `businesslogic` | Temporal workflows + activities + core business rules. |
| `dal` | Data access — `@Entity` classes and Spring Data repositories. |

Allowed dependencies:

- `service` may depend on `businesslogic` (workflow interfaces + DTOs only). It must not import `dal`.
- `businesslogic` may depend on `dal`.
- `dal` depends on neither.

Hard rules inside the layers:

- Controllers either start/signal/query Temporal workflows (for orchestrated flows) or call `core` services directly (for non-orchestrated operations). Controllers must not call repositories directly — the `service`→`dal` boundary stays intact. The non-orchestrated path is currently a POC carve-out for User CRUD (see ADR 002); revisit before the first production-bound feature.
- Workflows do not call Spring beans or the database directly. They orchestrate activities.
- Activities are the only place that injects `core` services.
- Any state change that affects domain aggregates (e.g. transactions, ledger entries) goes through a Temporal workflow.

Conventions:

- API versioning. HTTP paths are prefixed with `/api/v{N}/`, defaulting to `v1`. Major version bump on breaking changes only; additive changes stay on the current major. Apply uniformly across new endpoints and specs.

## Consequences

- **Positive**
  - Workflow-first state changes give us retries, timeouts, history, and visibility for free.
  - Strict layering keeps controllers thin and makes business logic easy to test without HTTP.
  - The package boundary is mechanical enough to enforce in code review (and later via ArchUnit if drift becomes a problem).
  - Spec + ADR scaffolding makes the authoring path obvious for future Claude sessions and human contributors alike.

- **Negative**
  - Every state-changing endpoint pays the cost of going through Temporal — slightly more boilerplate per feature than a CRUD-only design.
  - Three packages mean small features still touch three places. We accept that in exchange for a single, predictable shape across the whole codebase.
  - The `temporal-spring-boot-starter`'s in-process test server didn't co-operate cleanly with auto-discovered workers in our scaffold attempt, so end-to-end HTTP-through-DB verification lives in the docker-compose smoke path rather than in JUnit. Revisit if the starter improves.

- **Follow-ups**
  - Once a few features have shipped, evaluate adding ArchUnit tests to enforce the package-dependency rules automatically.
  - Once auth/authorization is needed, add an ADR rather than smuggling decisions into a feature spec.
  - The Ping scaffolding flow has served its purpose and is removed in favor of the first real domain feature (User, ADR 002). `/actuator/health` is the canonical liveness probe.

## Alternatives considered

- **Plain Spring MVC + JPA, no Temporal.** Rejected: the problem space involves transactions and a ledger, where workflow orchestration, retries, and visibility are worth their cost up front rather than retrofitted.
- **Hexagonal / ports-and-adapters with `domain`, `application`, `infrastructure`.** Rejected as overkill for a service of this size and at odds with the explicit three-package shape requested. We can revisit if `core` business logic outgrows a single package.
- **Quarkus or Micronaut instead of Spring Boot.** Rejected: Spring Boot has the largest Temporal-integration ecosystem and the team's familiarity is highest there.
- **Maven instead of Gradle.** Rejected: requested as Gradle (Groovy DSL).

## Amendments

### 2026-05-06 — User domain (ADR 002) prep

- **Hard rules: controller path.** The original "Controllers only start, signal, or query Temporal workflows. They do not call `core` services or repositories directly." was rewritten to allow controllers to call `core` services directly for non-orchestrated operations. The `service`→`dal` boundary remains; controllers still cannot touch repositories. The non-orchestrated path is a **POC carve-out** for User CRUD (ADR 002) — User itself is POC-only (production identity comes from Intuit SSO). Revisit before the first production-bound feature.
- **Conventions: API versioning.** Added a versioning rule (`/api/v{N}/`, default `v1`) so the first real endpoint and every subsequent spec inherit it without re-deciding.
- **Follow-ups: Ping removal.** The Ping scaffolding flow served its purpose during bootstrap and is removed alongside this amendment. `/actuator/health` is the canonical liveness probe.
