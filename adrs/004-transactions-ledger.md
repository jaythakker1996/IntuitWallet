# 004 — Transactions, ledger entries, and the first Temporal workflow

- **Status:** Accepted
- **Date:** 2026-05-06
- **Author:** IntuitWalletService

## Context

[ADR 003](003-wallet-domain.md) establishes the wallet as the balance container but explicitly defers the entities that write to it: `transactions`, `ledger_entries`, the running-balance write protocol, partitioning, idempotency-key storage, and the seeding migration for `SYSTEM` wallets. ADR 004 lands all of those.

It is also the first ADR that forces real workflow code into the repo. ADR 001 made Temporal the default for state changes; ADR 002 carved User CRUD out as a POC stub; ADR 003 declared wallet mutations workflow-backed but didn't yet require any workflow to exist in code. With this ADR, **every transaction is a state change to `transactions` + `ledger_entries` written in a single ACID commit**, and per ADR 001 that path is non-negotiably orchestrated. The first `@WorkflowImpl` and `@ActivityImpl` in the codebase land alongside the first transaction-creating feature spec — wired into the existing `wallet-service-worker` task queue from `application.yml`.

Design forces (carried over from the broader stablecoin design):

- **Append-only ledger with a running balance carried on each entry.** No mutable balance column. The latest entry per `(wallet_id, stablecoin)` IS the current balance. Replay-verifiable: a daily audit re-derives running balances from `amount` history and compares.
- **Double-entry per transaction.** Every transaction produces ≥ 2 ledger entries — one debit and one credit — that sum to zero. Internal transfers debit one user wallet and credit another. External transactions debit/credit the user wallet on one side and a `SYSTEM` wallet (`external_deposits`, `external_withdrawals`, `fee_revenue`, `treasury`) on the other; this is what makes the ledger balance to zero even when funds cross the Intuit boundary.
- **Optimistic concurrency via `entry_sequence`.** Each `(wallet_id, stablecoin)` has a strictly monotonic sequence with a unique constraint. Concurrent writers race the constraint; the loser retries within the workflow. No row-level locks on `wallets`.

Schema scope is intentionally narrow for the POC: the two tables actually needed for the first transaction-creating spec to land. Compliance, batch, QR, external-address, and outbox tables are explicitly out of scope — they're separate data groups in the broader design and bring no value until their flows exist. Range partitioning is also deferred for the POC (see §6 / §8); the production rollout will reintroduce it via a copy-rebuild migration before any non-POC volume hits the tables.

## Decision

### 1. Every transaction is workflow-orchestrated, and the first workflow lives in this codebase

Per ADR 001, state changes affecting `transactions` or `ledger_entries` always go through a Temporal workflow. ADR 004 is where that rule stops being theoretical:

- The first transaction-creating spec adds a `TransactionWorkflow` (`@WorkflowInterface` + `@WorkflowImpl(workers = "wallet-service-worker")`) under `businesslogic.workflow` and a `LedgerActivity` (`@ActivityInterface` + `@ActivityImpl(workers = "wallet-service-worker")`) under `businesslogic.activity`.
- The activity is the only place that calls `core` services (per ADR 001's hard rule). The core service is the only place that talks to repositories.
- The workflow's responsibility is orchestration: validate inputs (deterministically), call the ledger-write activity, retry on `entry_sequence` collisions inside the activity, transition `transactions.status` PENDING → COMPLETED / FAILED, and complete. Idempotency-key dedup happens before the workflow starts (controller-side lookup against `transactions(idempotency_key)`); collision returns the original response.
- The `temporal-spring-boot-starter` already auto-discovers workflows and activities on the classpath, and the `wallet-service` task queue is already configured in `application.yml`. No additional infra changes — this is purely a code addition gated on this ADR.

The exact workflow signature, activity contracts, and retry policies are owned by the first transaction-creating spec, not this ADR. ADR 004 only fixes the schema and the call-flow rule.

### 2. No foreign keys, anywhere

The wallet service deploys cross-region active-active in the broader design. FK constraints break under that model: replicas receive child rows before parents during replication catch-up, conflict resolution doesn't preserve FK ordering, and even single-region failover into a follower can produce transient FK violations during promotion. To keep the schema portable to that deployment from day one, **no DB-level foreign keys are declared between any tables in this service** — `transactions`, `ledger_entries`, `wallets`, and the cross-DB references to `users` / `compliance_check_id` / `batch_id` are all **logical references**.

Integrity is enforced by:

- **Commit-boundary integrity for a single transaction's writes.** The ledger-write activity inserts `transactions` and `ledger_entries` in one Spring `@Transactional`. If any insert fails, the whole DB transaction rolls back — there's no window in which a child row exists without its parent in the same commit.
- **Daily reconciliation** (out of POC scope; tracked as a follow-up) that replays `ledger_entries` history per `(wallet_id, stablecoin)` and reports any entry whose `tx_id` is missing from `transactions`, or whose `wallet_id` is missing from `wallets`.
- **Code review on writers.** The activity is the only path that writes ledger rows; reviewing one file is enough to ensure no orphan-producing path exists.

### 3. `transactions` table

```sql
CREATE TABLE transactions (
    tx_id                UUID         NOT NULL,
    type                 VARCHAR(16)  NOT NULL
                         CHECK (type IN ('SEND', 'RECEIVE', 'QR_PAY',
                                         'FUND', 'WITHDRAW', 'BATCH_DEBIT',
                                         'SETTLEMENT', 'REVERSAL')),
    from_type            VARCHAR(16)  NOT NULL
                         CHECK (from_type IN ('WALLET', 'EXTERNAL')),
    from_party           TEXT         NOT NULL,
    to_type              VARCHAR(16)  NOT NULL
                         CHECK (to_type IN ('WALLET', 'EXTERNAL')),
    to_party             TEXT         NOT NULL,
    stablecoin           VARCHAR(16)  NOT NULL,
    amount               NUMERIC(28,8) NOT NULL CHECK (amount > 0),
    fee                  NUMERIC(28,8) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    status               VARCHAR(16)  NOT NULL
                         CHECK (status IN ('PENDING', 'COMPLETED',
                                           'FAILED', 'REVERSED')),
    blockchain_tx_hash   TEXT,
    compliance_check_id  UUID,
    batch_id             UUID,
    idempotency_key      TEXT         NOT NULL,
    request_hash         TEXT         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tx_id)
);

CREATE UNIQUE INDEX idx_tx_idempotency
    ON transactions (from_party, idempotency_key);
CREATE INDEX idx_tx_from_created
    ON transactions (from_party, created_at DESC);
CREATE INDEX idx_tx_to_created
    ON transactions (to_party, created_at DESC);
CREATE INDEX idx_tx_pending
    ON transactions (status) WHERE status = 'PENDING';
```

Field rules:

- `tx_id` — **UUIDv7**, generated server-side in the workflow (via a `Workflow.sideEffect` returning a UUIDv7), and the sole primary key. UUIDv7 gives time-ordering in the index, which makes cursor-paginated history reads index-only. Never caller-provided. (`users.intuit_account_id` and `wallets.wallet_id` continue to use UUIDv4 — they don't sit on a hot time-ordered read path.) Choosing UUIDv7 now means the production-rollout switch to range partitioning by `created_at` won't fight the read path: the time-prefixed `tx_id` already groups same-month rows in the index.
- `from_party` / `to_party` — **polymorphic**: `wallet_id` (UUID-as-text) when the corresponding `*_type` is `WALLET`, `chain:address` when `EXTERNAL`. TEXT, not UUID, because external addresses aren't UUIDs. Logical reference, no FK (per §2).
- `idempotency_key` + `request_hash` — caller-provided dedup key + SHA-256 of the canonical request body. Unique on `(from_party, idempotency_key)`. On duplicate submission, the controller resolves via this index and returns the original response if `request_hash` matches; otherwise 422 "key reused with different payload." TTL (24h) is enforced in application code, not DB.
- `compliance_check_id`, `batch_id` — logical references to entities in **other databases** in the broader design (compliance, merchant-config). Nullable, no FK. Out of scope for this ADR's data model.
- `blockchain_tx_hash` — populated only for on-chain transactions; null for off-chain.
- `status` — only PENDING → COMPLETED / FAILED / REVERSED transitions are valid. Enforced by the workflow, not by the DB.

### 4. `ledger_entries` table — source of truth for balances

```sql
CREATE TABLE ledger_entries (
    entry_id           UUID          NOT NULL,
    tx_id              UUID          NOT NULL,
    wallet_id          UUID          NOT NULL,
    stablecoin         VARCHAR(16)   NOT NULL,
    entry_type         VARCHAR(16)   NOT NULL
                       CHECK (entry_type IN ('DEBIT', 'CREDIT',
                                             'SETTLEMENT', 'REVERSAL')),
    amount             NUMERIC(28,8) NOT NULL CHECK (amount > 0),
    running_available  NUMERIC(28,8) NOT NULL CHECK (running_available >= 0),
    running_pending    NUMERIC(28,8) NOT NULL CHECK (running_pending >= 0),
    entry_sequence     BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (entry_id)
);

CREATE UNIQUE INDEX idx_ledger_seq
    ON ledger_entries (wallet_id, stablecoin, entry_sequence);
CREATE INDEX idx_ledger_latest
    ON ledger_entries (wallet_id, stablecoin, entry_sequence DESC);
CREATE INDEX idx_ledger_tx
    ON ledger_entries (tx_id);
```

Append-only. **No `UPDATE`, no `DELETE`** — enforced by code review for the POC; a Postgres trigger raising on `UPDATE` / `DELETE` lands when the first non-POC feature ships.

Field rules:

- `entry_id` — UUIDv7, generated in the activity. Same time-ordering rationale as `tx_id`.
- `amount` — always positive. Sign / direction comes from `entry_type` (`DEBIT` / `REVERSAL` decrease, `CREDIT` / `SETTLEMENT` increase).
- `running_available` — wallet's available balance after this entry was applied. Reading the latest entry per `(wallet_id, stablecoin)` returns the current balance in a single index-only seek on `idx_ledger_latest`.
- `running_pending` — outbound balance reserved for in-flight transactions. Reduced when a `PENDING` debit settles (status COMPLETED) or is released (status FAILED).
- `entry_sequence` — `BIGINT`, monotonic per `(wallet_id, stablecoin)`, **unique-constrained**. The activity computes the next sequence as `MAX(entry_sequence) + 1` for the wallet/stablecoin and inserts. Concurrent writers race the unique index; the loser retries by re-reading the new max. This is the optimistic-concurrency mechanism, deliberately chosen over `SELECT … FOR UPDATE` on `wallets`.
- **Min two entries per transaction**: at least one debit and one credit, summing to zero. Enforced by the activity, not the DB.

### 5. The atomic write protocol

Inside the ledger-write activity, single Spring `@Transactional`:

1. `INSERT INTO transactions (..., status='PENDING', ...)`.
2. For each affected wallet:
   - `SELECT MAX(entry_sequence) FROM ledger_entries WHERE wallet_id=? AND stablecoin=?` (lookup current state).
   - Compute new `running_available` / `running_pending`.
   - `INSERT INTO ledger_entries (..., entry_sequence = max+1, ...)`. On `DataIntegrityViolationException` from `idx_ledger_seq`, the whole DB transaction rolls back; the **activity** retries (Temporal does the wait + replay). The workflow doesn't see the collision.
3. Commit. The Spring transaction is the ACID boundary; either everything lands or nothing does.

### 6. Partitioning deferred for the POC

Both `transactions` and `ledger_entries` ship as plain (non-partitioned) tables for the POC. Range partitioning by `created_at` month is the right shape for production — it makes archival a `DETACH PARTITION` op, lets the planner prune old partitions on date-bounded queries, and aligns with the broader design's archival strategy — but it brings operational complexity (partition-management job, archival rules, the balance-row carry-forward invariant below) that the POC neither needs nor benefits from.

The trade-off accepted here: when partitioning is reintroduced before production rollout, it requires a copy-rebuild migration (create new partitioned table, copy rows, swap names, drop old). That migration is straightforward against POC volumes and is tracked as a follow-up.

**Balance row invariant for archival** (recorded here so a future archival spec doesn't have to re-derive it): before detaching any old partition, the archival job must copy the latest `ledger_entries` row per `(wallet_id, stablecoin)` forward to the current partition. That row IS the running balance — it must never leave the live tables.

### 7. Reversal model

A reversal does not `UPDATE` the original transaction's ledger entries. It writes:

1. A new `transactions` row with `type='REVERSAL'`, distinct `tx_id`, distinct `idempotency_key`.
2. New `ledger_entries` with `entry_type='REVERSAL'` mirroring the original's debit/credit, sign-flipped via the entry-type semantics.
3. An `UPDATE transactions SET status='REVERSED' WHERE tx_id=<original>` — the only `UPDATE` allowed against `transactions`. (Status transitions are otherwise PENDING → COMPLETED / FAILED via the workflow.)

The point: the audit trail stays intact, balance reconciliation still works by replay, and reversals don't make the ledger non-append-only.

### 8. System wallet seeding

The schema-bootstrap migration that creates these tables also seeds the four `SYSTEM` wallets declared in ADR 003 §3:

```sql
INSERT INTO wallets (wallet_id, intuit_account_id, type, status)
VALUES
    ('00000000-0000-0000-0000-00000000ed01',
     '00000000-0000-0000-0000-0000000000ed', 'SYSTEM', 'ACTIVE'),  -- external_deposits
    ('00000000-0000-0000-0000-00000000ed02',
     '00000000-0000-0000-0000-0000000000ee', 'SYSTEM', 'ACTIVE'),  -- external_withdrawals
    ('00000000-0000-0000-0000-00000000ed03',
     '00000000-0000-0000-0000-0000000000ef', 'SYSTEM', 'ACTIVE'),  -- fee_revenue
    ('00000000-0000-0000-0000-00000000ed04',
     '00000000-0000-0000-0000-0000000000f0', 'SYSTEM', 'ACTIVE');  -- treasury
```

Synthetic `intuit_account_id` values satisfy ADR 003's `UNIQUE NOT NULL` without colliding with any real user UUID. The mapping from logical name (`external_deposits` etc.) to `wallet_id` is held in a small `SystemWallets` constants class in `businesslogic.core` — the ledger activity looks up system wallets by name, never by hardcoded UUID outside that one file.

### 9. Out of scope

This ADR explicitly does **not** specify:

- The compliance, batch_payments, qr_codes, external_addresses tables (separate data groups; separate ADRs when their flows exist).
- The `outbox` table and any cross-region or BU-event publishing path. The outbox pattern is the right fix for the dual-write problem when events become a requirement; until the first cross-region or BU-facing feature spec exists, an outbox table with no writers and no readers is dead weight. The schema and atomic-write contract on the two tables this ADR defines don't change when outbox is added later — `INSERT INTO outbox` joins the existing `@Transactional` block as an additional statement.
- Cross-region settlement flows.
- The daily reconciliation job that replays ledger history to verify running balances.
- Read-tier routing (Aurora read replicas, warm/cold-tier fallback).
- The append-only trigger on `ledger_entries` (deferred to the first non-POC feature; for the POC, append-only is enforced by code review).
- Range partitioning of `transactions` and `ledger_entries` (deferred per §6; reintroduced via copy-rebuild before production rollout).

## Consequences

- **Positive**
  - The ledger has a single source of truth (`ledger_entries.running_available` on the latest entry) with a replay-verifiable correctness property — no chance of balance drifting from history.
  - Per-wallet balance reads stay O(1): one index seek on `idx_ledger_latest`.
  - Optimistic concurrency via `entry_sequence` avoids row-level locks on `wallets` — concurrent writers across different `(wallet_id, stablecoin)` pairs don't contend.
  - First real Temporal workflow + activity land in this codebase, exercising the call-flow rule that ADR 001 has been asserting since day one.
  - "No FKs anywhere" makes the schema portable to cross-region active-active without a migration that exists solely to drop constraints.
- **Negative**
  - Append-only is enforced by code review (and a future trigger). A bug that issues an `UPDATE ledger_entries` would silently break replay verification until the next reconciliation run. Mitigation: the trigger lands as soon as the first non-POC feature ships, and reconciliation runs daily.
  - `entry_sequence` collisions force activity-side retries. Under high contention on a single `(wallet_id, stablecoin)`, throughput is bounded by the retry loop. Acceptable for the POC; if a wallet ever becomes a hot key in production, partitioning by sub-account is the escape hatch.
  - Polymorphic `from_party` / `to_party` (TEXT) means foreign keys to `wallets` aren't possible. Same trade-off ADR 003 made for the user link, same justification (§2).
  - System wallets carry synthetic `intuit_account_id` UUIDs that need to stay reserved forever. The constant range (`…0ed`–`…0f0`) is documented in `SystemWallets`; expanding the set in the future means picking the next reserved UUID, not generating one.
  - Partitioning is deferred. Reintroducing it before production rollout requires a copy-rebuild migration (create new partitioned tables, copy rows, swap names, drop old). Cheap at POC volumes; would be expensive once production volumes accumulate, so this has to ship before the production-rollout cutover.
- **Follow-ups**
  - First transaction-creating spec (likely `specs/006-send-payment.md`): adds the JPA entities + repositories, the core ledger service, the activity, the workflow, and the controller endpoint.
  - Append-only trigger on `ledger_entries` once a non-POC feature is on the roadmap.
  - Daily reconciliation job replaying `(wallet_id, stablecoin)` history and verifying `running_available` matches.
  - `outbox` table + Kafka publisher — lands with the first cross-region or BU-facing event spec.
  - Reintroduce range partitioning on `transactions` and `ledger_entries` before production rollout (copy-rebuild migration), and add a partition-management job (create next month, archive oldest beyond N months) once partitioning is back.
  - Compliance, batch, QR, external-address tables — each in its own ADR when the flow exists.

## Alternatives considered

- **Mutable balance on `wallets`, ledger as audit log.** Rejected for the same reasons ADR 003 rejected putting `balance` on the wallet row: two writes per transaction, drift risk, no replay correctness. ADR 004 makes the rejection concrete by putting the running balance on the ledger entry instead.
- **No running balance — recompute by replay on every read.** Rejected: balance reads dominate the workload; replay needs either snapshots or a CQRS read model, both of which the running-balance pattern obviates.
- **Pessimistic locking on `wallets` (`SELECT … FOR UPDATE`) instead of `entry_sequence` optimistic concurrency.** Rejected: serializes all writes against a single wallet, including writes to different stablecoins on that wallet, and creates lock contention between debits and reads. Optimistic concurrency keyed on `(wallet_id, stablecoin)` lets independent stablecoins on the same wallet proceed in parallel.
- **Foreign keys between any of the tables in this service.** Rejected on cross-region active-active grounds (see §2). FK constraints would have to be dropped before the first multi-region deployment anyway; declaring them now would commit us to a future migration whose only purpose is removing them. The integrity guarantees FKs offer (no orphan child rows) are recovered via the single-`@Transactional` write boundary in the ledger activity, plus daily reconciliation.
- **Partition `transactions` and `ledger_entries` from day one.** Considered and **rejected for the POC** (see §6). Declarative partitioning at table creation is operationally cheap on Postgres, but it brings a partition-management job, an archival rule, and the balance-row carry-forward invariant — none of which the POC needs to validate. Reintroducing partitioning later requires a copy-rebuild, which is acceptable at POC volumes; this has to land before any production-rollout volume accumulates, so it's tracked as a follow-up rather than a forever-deferred item.
- **Wrap each transaction in its own Saga across multiple workflows.** Rejected: a single transaction's writes are local to one DB and one ACID commit. The workflow only needs to orchestrate compliance check (out of scope here) → ledger insert → status flip. Saga-shaped flows show up later for batch payments and cross-region settlement, not for the unit transaction.
- **Use the same UUIDv4 strategy as `users` and `wallets` for `tx_id` / `entry_id`.** Rejected: `transactions` is the hot read path (history listing, cursor pagination), and UUIDv7's time-ordered prefix turns range scans into index-only operations. The cost (a UUIDv7 generator dependency) is small; the benefit on tx history reads is large.
- **Add an `outbox` table now, even without a publisher.** Rejected: a table with no writers and no readers is dead weight. The atomic-write contract on `transactions` + `ledger_entries` doesn't change when `outbox` is added — the future `INSERT INTO outbox` joins the existing `@Transactional` block as an additional statement. The dual-write problem outbox solves only bites once events exist to be lost.
