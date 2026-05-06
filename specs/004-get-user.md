# 004 — Get user

- **Status:** Implemented
- **Author:** IntuitWalletService
- **Last updated:** 2026-05-06
- **Implements:** TBD (link added when PR opens)
- **Related ADRs:** [002-user-domain](../adrs/002-user-domain.md)

## Problem

Once users can be created and updated, downstream POC features need to fetch them by id.

## Goals / Non-goals

- **Goals**
  - `GET /api/v1/users/{intuitAccountId}` returns the same projection as create / update.
  - 404 when the id doesn't exist.
- **Non-goals**
  - Lookup by email (separate spec if needed).
  - Listing / paging (separate spec).
  - Auth/authorization.

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | /api/v1/users/{intuitAccountId} | Fetch a user by id. |

### Request

No body. Path parameter: `intuitAccountId` (UUID).

### Response

Same shape as spec 002's response.

### Error cases

| Status | When |
| --- | --- |
| 400 | `intuitAccountId` is not a valid UUID. |
| 404 | No user with the given `intuitAccountId`. |

## Data model

N/A — same `users` table as ADR 002.

## Workflow

N/A. Controller calls `UserCoreService.getUser(UUID)` directly. The core method does `findById(...).orElseThrow(UserNotFoundException::new)`.

`@Transactional(readOnly = true)`.

## Acceptance criteria

- [ ] GET against an existing id returns 200 and the projection.
- [ ] GET against a syntactically valid but unknown UUID returns 404.
- [ ] GET against a malformed path parameter returns 400.

## Test plan

- `UserCoreServiceTest` additions: happy get, unknown id throws, malformed UUID handled at controller layer.
- `UserControllerTest` additions: 200 / 404 / 400 mapping.
- Manual smoke once implemented.

## Open questions

- Caching: skipped for the POC. Revisit if call volumes warrant.
