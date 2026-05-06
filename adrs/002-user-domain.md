# 002 — User domain (POC stub)

- **Status:** Accepted
- **Date:** 2026-05-06
- **Author:** IntuitWalletService

## Context

This is the first real domain entity in the service. Three forthcoming feature specs (create / update / get user) all read or write the same `users` row, so the schema is genuinely cross-cutting and belongs in an ADR rather than being duplicated across specs that will inevitably drift.

This domain is a **POC stub**. In production, user identity is owned by Intuit SSO / the identity platform and this wallet service would not persist users at all — at most it would keep a minimal mirror of identity data, populated by a sync job rather than by a public POST endpoint. The User domain exists in this repo only so subsequent POC specs (accounts, transactions, ledger entries) have a stable `users` row to reference. Anything that follows from "User lives here" (the API surface, the relaxed call-flow path, the schema choices) is therefore POC-scoped and gets revisited before the first production-bound feature ships.

Schema scope is intentionally narrow for the POC: identity + role + home region + audit timestamps. KYC fields are called out as a near-future addition to this same table once the KYC flow is designed, but are not included now because there is no flow that would write to them.

## Decision

### 1. User CRUD bypasses workflows (POC carve-out)

Per the ADR 001 amendment that introduces the controller→core path for non-orchestrated flows, the three user APIs go:

```
controller → core → repository → DB
```

No Temporal workflow, no activity. The endpoints are:

- `POST /api/v1/users`
- `PATCH /api/v1/users/{intuitAccountId}`
- `GET /api/v1/users/{intuitAccountId}`

Workflows remain mandatory for ledger / transaction state changes and any other multi-step, retryable, or saga-bearing flow. User CRUD has none of those properties — orchestration overhead would be pure cost. The strict controller→workflow rule reasserts as the default once User leaves this service.

### 2. `users` table schema

The Flyway V1 migration creates exactly this:

```sql
CREATE TABLE users (
    intuit_account_id UUID PRIMARY KEY,
    email             VARCHAR(255) NOT NULL,
    role              VARCHAR(16)  NOT NULL
                      CHECK (role IN ('CONSUMER', 'MERCHANT', 'CONTRACTOR')),
    home_region       VARCHAR(32)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));
```

Field rules:

- `intuit_account_id` — UUID PK, generated app-side in `UserCoreService.createUser`. Never auto-incremented, never reused. The column name anticipates the eventual sync from Intuit SSO without needing a rename.
- `email` — case-insensitive uniqueness via the functional index on `LOWER(email)`. Stored as entered.
- `role` — one of `CONSUMER` / `MERCHANT` / `CONTRACTOR`. No default; caller specifies. Locked at creation time for now (an admin-only mutation flow comes later).
- `home_region` — required. CHECK is intentionally omitted so adding regions doesn't require a schema migration.
- `created_at` — DB default, never updated by application code.
- `updated_at` — DB default on insert; explicitly bumped to `now()` by `UserCoreService` on every update path. Useful audit signal even before a dedicated audit log lands.

### 3. KYC is future scope

When the KYC flow is designed, two columns will be added to this same `users` table via a follow-up migration: `kyc_tier` (`NONE` / `BASIC` / `ENHANCED`, default `NONE`) and `kyc_verified_at` (nullable TIMESTAMPTZ). Those columns will be written by a dedicated KYC flow, not by the user-CRUD APIs — KYC is also POC-scoped, and in production may live in the identity platform alongside identity data. They are out of scope here so the schema doesn't carry columns no code path touches.

## Consequences

- **Positive**
  - Schema is fixed once for the POC; the three user specs only describe API shape, not columns.
  - User CRUD is plain controller→core→repo — no workflow plumbing for an entity that doesn't need it.
  - API paths are versioned from day one (`/api/v1/...`), so additive changes don't churn URLs.

- **Negative**
  - The POC-scoped controller-rule carve-out means future authors need to consciously decide "does this feature need a workflow?" rather than getting one-shape-fits-all. Mitigated by tagging the carve-out as POC-only in ADR 001 and revisiting it before any production-bound feature ships.
  - `users` will need to be migrated (or deleted) when the SSO-backed identity integration lands.

- **Follow-ups**
  - Replace the User stub with Intuit-SSO-backed identity integration before production.
  - KYC migration + flow (POC-scoped initially).
  - Auth/authorization ADR before user APIs go public.
  - Admin-only role mutation.
  - Soft-delete (`status` column) when needed.

## Alternatives considered

- **Schema in spec 002 only.** Rejected — specs 003 and 004 would have to duplicate or back-reference it, and it would drift.
- **`BIGSERIAL` PK.** Rejected — UUID is the conventional opaque user-facing identifier and survives any later identity-platform extraction without translation.
- **Include KYC fields now (default `NONE`, nullable `verified_at`).** Rejected — the POC has no flow that writes to them, leaving columns no code path touches. Adding them when the KYC flow lands keeps the schema honest.
- **Wrap user CRUD in a thin pass-through workflow for ops-visibility uniformity.** Rejected — pure overhead with no orchestration value, and ADR 001's amendment already permits the bypass.
- **Make the controller-rule relaxation a permanent architectural principle rather than a POC carve-out.** Rejected — User itself is POC-only, so widening the rule on its back would be premature. Re-evaluate when the first non-POC feature would benefit from the path.
