# 003 — Wallet domain (POC stub)

- **Status:** Accepted
- **Date:** 2026-05-06
- **Author:** IntuitWalletService

## Context

Wallet is the second domain entity in the service. It's the container that ledger entries and transactions reference; without it, the next ADR (004 — transactions and ledger entries, forthcoming) has nowhere to point.

The reasoning that put the `users` schema in [ADR 002](002-user-domain.md) instead of duplicating it across specs 002 / 003 / 004 applies again here. Every future ledger / transaction / QR / batch spec touches the `wallets` row, so the schema is genuinely cross-cutting and belongs in an ADR rather than in the first feature spec that touches it.

This domain is **POC-scoped**, same caveat as the User domain: the schema choices captured here are POC choices and get revisited before any production-bound feature ships. In production, wallet provisioning is likely tied to identity onboarding behind Intuit SSO. The wallet table here exists so subsequent POC specs (transactions, ledger entries) have something to reference.

User and Wallet are separated by design. `users` is owned by Intuit's identity platform and lives in a different database; `wallets` lives in the Ledger Core DB. ADR 003 reflects that boundary today by treating the `users` ↔ `wallets` link as a **logical reference** rather than a database foreign key. The POC happens to co-locate both tables in one Postgres for convenience, but the schema must not depend on that.

The wallet design is informed by the broader Intuit Global Stablecoin Wallet design — the relevant forces are:

- Off-chain settlement is preferred for intra-Intuit transfers; the source of truth for balances is an internal append-only ledger, not a blockchain.
- A wallet holds **multiple stablecoin balances** (USDC, USDT, …). Balance is per `(wallet_id, stablecoin)`, not a single column.
- Ledger pattern: **append-only ledger with a running balance carried on each entry**. Each ledger entry records `running_available` and `running_pending` as of that entry; the latest entry per `(wallet_id, stablecoin)` IS the current balance. There is no mutable balance column on `wallets`. (Detailed schema and rationale: ADR 004.)
- **System wallets** (`type = SYSTEM`) exist so every external transaction still balances to zero — the user's wallet on one side, a system account (`external_deposits`, `external_withdrawals`, `fee_revenue`, `treasury`) on the other.
- Cardinality: one wallet per user (1:1 with `users.intuit_account_id`). One identity, one wallet, accessible across products.

ADR 003 records only the wallet-side decisions. ADR 004 will record the transactions / ledger-entries / outbox schema and the running-balance write protocol in detail.

## Decision

### 1. Wallet mutations go through Temporal workflows (default rule)

Wallet **is not** a User-style POC carve-out. The [ADR 001](001-tech-stack.md) default applies: every state-changing wallet operation is orchestrated by a Temporal workflow.

- Wallet provisioning isn't a single-row insert in the broader design — it fans out across the Ledger Core DB (the `wallets` row), seeded ledger state if needed, and (when ADR 004 lands) the outbox / event publication that downstream BU products listen for. Even at POC scope, this is the shape the workflow needs to land in; wrapping it in a workflow now means ADR 004 only adds activities, never restructures the call path.
- Status mutations (`FREEZE`, `CLOSE`) are compliance-driven and need retries, audit history, and visibility — exactly what Temporal gives us for free.
- It keeps the call-flow rule honest. The User carve-out is explicitly tagged as a POC stub for an entity that wouldn't live in this service in production; widening it to wallet — which **does** live in this service in production — would erode the rule on its first real test.

Reads remain a plain `controller → core → repository → DB` path — no workflow, consistent with ADR 001 (the workflow rule is for state changes).

The forthcoming wallet endpoints will likely be `POST /api/v1/wallets` (workflow-backed), `GET /api/v1/wallets/{walletId}` (read, direct), and `PATCH /api/v1/admin/wallets/{walletId}/status` (workflow-backed). The exact API surface is owned by the spec, not this ADR.

### 2. `wallets` table schema

Flyway migration `V2__wallets.sql` (created by the first wallet spec, not by this ADR) creates exactly this:

```sql
CREATE TABLE wallets (
    wallet_id         UUID PRIMARY KEY,
    intuit_account_id UUID         NOT NULL UNIQUE,
    type              VARCHAR(16)  NOT NULL
                      CHECK (type IN ('USER', 'SYSTEM')),
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

Field rules:

- `wallet_id` — UUID PK, generated app-side in the wallet-creation activity / `WalletCoreService`. Never auto-incremented, never reused. The POC uses random UUIDv4 for parity with `users`; the broader design calls for UUIDv7 for time-ordering on `transactions` / `ledger_entries`, but that detail is owned by ADR 004 because it matters there, not here.
- `intuit_account_id` — `UNIQUE NOT NULL`. Enforces 1:1 with the user. **No database foreign key** to `users(intuit_account_id)`: in the production architecture, `users` is owned by Intuit's identity platform and lives in a separate database (the wallet service reads it via API, not direct DB access). The link is a **logical reference**, the same shape transactions use for `compliance_check_id` and `batch_id` in the broader design. Existence of the user is verified in core / activity code (and by daily reconciliation in production), not by a constraint.
- `type` — `USER` or `SYSTEM`. System wallets are seeded once (see §3) and never created via the public API.
- `status` — `ACTIVE` on creation. `FROZEN` blocks outbound transactions; `CLOSED` is permanent and requires zero balance (enforced in the close workflow when it lands; the DB constraint is just the enum).
- `created_at` — DB default, never updated by application code.
- `updated_at` — DB default on insert; bumped explicitly by the wallet-mutation workflow on every update path. Same pattern as `users`.

**No `balance` column, no `currency` column.** Balance is per stablecoin and lives on the latest ledger entry. Any future field that varies per stablecoin (e.g. per-currency status, per-currency limits) belongs on a wallet-stablecoin junction table introduced when the use case arises, not on `wallets`.

### 3. System wallets are seeded, not user-created

The `SYSTEM` wallets (`external_deposits`, `external_withdrawals`, `fee_revenue`, `treasury`) are inserted by a Flyway migration when ADR 004 lands, with synthetic `intuit_account_id` values reserved for system accounts. ADR 003 only declares that `type = SYSTEM` exists; the seeding migration is owned by ADR 004 because it's only meaningful once the ledger exists.

### 4. What ADR 004 will cover

ADR 003 explicitly does **not** specify any of the following — they belong to ADR 004 (next):

- The `transactions` table.
- The `ledger_entries` table (including `running_available`, `running_pending`, `entry_sequence`).
- The `outbox` table for cross-region event delivery.
- The running-balance write protocol and its optimistic-concurrency mechanism.
- The partitioning strategy for `ledger_entries` and `transactions`.
- Idempotency-key + request-hash storage on transactions.
- The seeding migration for `SYSTEM` wallets.

## Consequences

- **Positive**
  - Wallet schema is fixed once for the POC; future wallet specs only describe API shape, not columns.
  - Multi-stablecoin support drops out for free — the wallet row doesn't encode currency, so adding stablecoins is purely a ledger concern.
  - 1:1 with the user via the `UNIQUE` constraint on `intuit_account_id` makes "does this user have a wallet?" a single index lookup.
  - System wallets are explicit in the type enum from day one — no awkward retrofit when ADR 004 lands.
  - Workflow-orchestrated mutations from day one mean ADR 004's transactions (which must be workflow-driven) plug into an existing pattern instead of forcing a call-flow refactor.
  - No DB-level FK to `users` keeps the schema honest about the production deployment shape (identity in a different DB) — moving `users` out later is a config change, not a migration.

- **Negative**
  - Reading a balance requires hitting `ledger_entries`, not `wallets` — the read path is ADR 004's responsibility, not a one-liner here. Acceptable: balance correctness > read shortcut, and the running-balance pattern still gets a single-row index lookup per stablecoin.
  - Wallet creation pays Temporal's overhead (workflow + activity) for what is, in the POC, a single-row insert. Accepted up front in exchange for a stable call-flow shape; the cost shrinks fast once the wallet-creation activity also has to write the outbox event.
  - Existence of the referenced user is enforced in code rather than by a DB constraint — a stale `intuit_account_id` in `wallets` is now possible if the verification step is skipped. Mitigated by performing the check in the wallet-creation activity and (in production) by daily reconciliation between identity and Ledger Core.
  - System wallets carry a synthetic `intuit_account_id` purely to satisfy `UNIQUE NOT NULL` — slightly ugly, but cheaper than a nullable column or a separate `system_accounts` table that would duplicate the ledger's wallet-id surface.

- **Follow-ups**
  - ADR 004: transactions, ledger entries, outbox, running-balance write protocol, partitioning, idempotency-key storage, system-wallet seeding migration.
  - First wallet feature spec adds `V2__wallets.sql`, the `Wallet` entity, repository, core service, DTOs, controller, and the wallet-creation workflow / activity.
  - Auth/authorization ADR before wallet APIs go public (same as for User).
  - Admin-only `FREEZE` / `CLOSE` flow as a separate spec once compliance reasons get involved.

## Alternatives considered

- **Persist `balance` (and `currency`) on the wallet row, with the ledger as a side audit log.** Rejected: every payment becomes two writes (balance + ledger), the two representations can drift, and the replay-verifiable correctness property the running-balance pattern gives us for free goes away.
- **No running balance — recompute from full ledger replay on every read.** Rejected: balance reads require replaying history (or a separate read model). Fine for payment-processor reconciliation workloads; wrong for a wallet product where users hit balance reads constantly.
- **One row per `(wallet_id, stablecoin)` on `wallets`.** Rejected: conflates wallet identity with per-currency holdings. The ledger already provides per-stablecoin balance via the latest entry; duplicating it on the wallet table reintroduces the drift risk we just rejected.
- **Many wallets per user (e.g. one per purpose).** Rejected: the design is unambiguous — one wallet, one identity, accessible from any Intuit product. Multi-wallet adds nothing the POC needs.
- **Skip `type = SYSTEM`; put system accounts in a separate table.** Rejected: ledger entries reference `wallet_id` uniformly; a separate table forces a polymorphic reference or a union view. The `type` enum keeps the ledger's join surface flat.
- **Take the User-style POC carve-out and skip workflows for wallet CRUD.** Rejected: User CRUD has no money movement and (in production) wouldn't live in this service at all — its carve-out was scoped to that. Wallet *does* live in this service in production, its creation is the entry point for the ledger's lifecycle, and ADR 004's transactions must be workflow-driven anyway. Landing wallet on the same path now avoids restructuring it later.
- **Add a database FK from `wallets.intuit_account_id` to `users(intuit_account_id)`.** Rejected: `users` is owned by the identity platform in production and lives in a separate DB. A FK only works while the POC happens to co-locate the two tables in one Postgres; the schema must not depend on that. Cross-group references are logical IDs verified in code, matching how transactions reference compliance and batch records.
