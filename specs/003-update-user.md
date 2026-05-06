# 003 — Update user

- **Status:** Draft
- **Author:** IntuitWalletService
- **Last updated:** 2026-05-06
- **Implements:** TBD
- **Related ADRs:** [002-user-domain](../adrs/002-user-domain.md)

## Problem

A small set of user fields needs to be mutable post-creation: `email` and `homeRegion`. `role` is intentionally not mutable here — role changes belong to a future admin flow. KYC fields don't exist yet (ADR 002 defers them).

## Goals / Non-goals

- **Goals**
  - `PATCH /api/v1/users/{intuitAccountId}` updates one or both of `email` and `homeRegion`.
  - On any update, `updated_at` is bumped to `now()` in the same transaction.
  - 404 when the id doesn't exist.
- **Non-goals**
  - Mutating `role`.
  - Optimistic locking (single-writer assumption for now; revisit when concurrent admin tooling lands).
  - Auth/authorization.

## API

| Method | Path | Description |
| --- | --- | --- |
| PATCH | /api/v1/users/{intuitAccountId} | Update mutable fields on a user. |

### Request

```json
{
  "email": "new@example.com",
  "homeRegion": "us-west-2"
}
```

At least one field is required. Both fields are optional independently; an empty body is `400`.

Validation (when present):

- `email` — `@Email @Size(max = 255)`, non-blank.
- `homeRegion` — `@NotBlank @Size(max = 32)`.

### Response

Same shape as spec 002's response (the full `UserResponse` projection).

### Error cases

| Status | When |
| --- | --- |
| 400 | Empty body, invalid `email`, oversize/blank `homeRegion`. |
| 404 | No user with the given `intuitAccountId`. |
| 409 | New `email` collides with another existing user's email (case-insensitive). |

## Data model

N/A — same `users` table as ADR 002.

## Workflow

N/A. Controller calls `UserCoreService.updateUser(UUID, UpdateUserCommand)` directly. The core service:

1. Loads the row by id; throws `UserNotFoundException` (→ 404) if missing.
2. If `email` is present, checks for case-insensitive collision; throws `EmailConflictException` (→ 409) if another row owns it.
3. Mutates the entity fields, sets `updatedAt = OffsetDateTime.now()`, saves.

`@Transactional` on the method.

## Acceptance criteria

- [ ] PATCH with valid body returns 200 and the updated projection; `updated_at` is strictly greater than the prior value.
- [ ] PATCH with empty body returns 400.
- [ ] PATCH against unknown id returns 404.
- [ ] PATCH attempting to change `email` to a value owned by another user returns 409.
- [ ] PATCH ignores `role` if sent (or returns 400; pick one and document) — current decision: 400 with "unrecognized field" via `FAIL_ON_UNKNOWN_PROPERTIES`.

## Test plan

- `UserCoreServiceTest` additions: happy patch, partial patch (email only / homeRegion only), unknown id, email collision.
- `UserControllerTest` additions: 400 on empty body, 404 mapping, 409 mapping.
- Manual smoke once implemented.

## Open questions

- Concurrency: last-writer-wins is fine for the POC. Decide on optimistic locking when admin tooling lands; cheapest add is a `version` column.
