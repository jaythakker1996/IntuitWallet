# 002 — Create user

- **Status:** Approved
- **Author:** IntuitWalletService
- **Last updated:** 2026-05-06
- **Implements:** TBD
- **Related ADRs:** [002-user-domain](../adrs/002-user-domain.md)

## Problem

This is the first real domain entity in the service. Without a way to materialise a user, every subsequent feature (accounts, transactions, ledger entries) has nothing to reference. This spec is the wedge: a single endpoint that creates one row in `users`, end-to-end, exercising the full call flow.

The User domain is POC-only — production user identity is owned by Intuit SSO. ADR 002 has the full caveat. This endpoint exists so the rest of the POC has a `users` row to hang off.

## Goals / Non-goals

- **Goals**
  - `POST /api/v1/users` creates a row in `users` with the fields from ADR 002.
  - Idempotent on duplicate email (case-insensitive): same email returns the existing row.
  - DTO-level validation rejects malformed input with HTTP 400.
- **Non-goals**
  - Authentication / authorization (separate ADR before user APIs go public).
  - KYC fields (deferred per ADR 002).
  - Update or get APIs (specs 003, 004).
  - Soft delete, role mutation, optimistic locking.

## API

| Method | Path | Description |
| --- | --- | --- |
| POST | /api/v1/users | Create a user row. |

### Request

```json
{
  "email": "alice@example.com",
  "role": "CONSUMER",
  "homeRegion": "us-east-1"
}
```

Validation:

- `email` — `@NotBlank @Email @Size(max = 255)`.
- `role` — `@NotNull`, must be one of the `Role` enum values (`CONSUMER`, `MERCHANT`, `CONTRACTOR`).
- `homeRegion` — `@NotBlank @Size(max = 32)`.

### Response

```json
{
  "intuitAccountId": "f3c1...uuid",
  "email": "alice@example.com",
  "role": "CONSUMER",
  "homeRegion": "us-east-1",
  "createdAt": "2026-05-06T12:34:56Z",
  "updatedAt": "2026-05-06T12:34:56Z"
}
```

HTTP status:

- `201 Created` — new row created.
- `200 OK` — duplicate-email idempotency match; body is the existing row's projection.

### Error cases

| Status | When |
| --- | --- |
| 400 | Missing/blank fields; invalid `email` format; unknown `role`; oversize fields. |
| 415 | Non-JSON request. |
| 500 | Unexpected DB error not covered by idempotency reconciliation. |

## Data model

N/A for this spec — uses the `users` table defined in ADR 002, created by the V1 Flyway migration. No new columns, no new indexes.

## Workflow

N/A. Per ADR 002 (and the ADR 001 amendment that allows it), user CRUD is non-orchestrated. The controller calls `UserCoreService.createUser(...)` directly. No Temporal workflow, no activity, no task queue.

## Idempotency

Implemented in `UserCoreService.createUser`:

1. `userRepository.findByEmailIgnoreCase(email)` — if present, return the existing entity (idempotent; controller maps to 200).
2. Otherwise `userRepository.save(new User(UUID.randomUUID(), email, role, homeRegion))` and return.
3. On `DataIntegrityViolationException` (concurrent submit collided on the unique index), re-resolve via `findByEmailIgnoreCase` and return the resolved row. Concurrent identical submits collapse to one row.

The whole method is `@Transactional`.

## Acceptance criteria

- [ ] `POST /api/v1/users` with a valid body returns `201` and the projection above; `select * from users` shows the row.
- [ ] Posting the same email twice (any case) returns `200` with the same `intuitAccountId` and a single row in the table.
- [ ] Posting with `email = ""`, missing `role`, `role = "ADMIN"`, or missing `homeRegion` returns `400`.
- [ ] `created_at` and `updated_at` are equal on first insert.
- [ ] No `CreateUserWorkflow` execution appears in the Temporal UI (sanity check that the bypass is real).

## Test plan

- `UserCoreServiceTest` (`@DataJpaTest` against H2):
  - Happy path: insert; assert UUID assigned, fields persisted, timestamps present.
  - Duplicate email exact case: second call returns same row, repository count stays at 1.
  - Duplicate email different case (`A@B.COM` then `a@b.com`): same as above.
- `UserControllerTest` (`@WebMvcTest` with mocked core, optional but recommended):
  - 201 on valid body, response shape matches `UserResponse`.
  - 400 on each validation failure.
- Manual smoke against `docker compose up -d`:

  ```bash
  curl -X POST http://localhost:8081/api/v1/users \
    -H 'Content-Type: application/json' \
    -d '{"email":"a@b.com","role":"CONSUMER","homeRegion":"us-east-1"}'
  ```

  Expected: 201 with the projection. Repeat → 200 same `intuitAccountId`. Repeat with `A@B.COM` → 200 same `intuitAccountId`.

## Open questions

- Should the duplicate-email idempotency surface as 200 (current decision) or 201 with a header? Current shape is simplest and matches "you asked for this user, here it is."
