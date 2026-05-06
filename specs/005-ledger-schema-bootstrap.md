# 005 — Ledger schema bootstrap

- **Status:** Approved
- **Author:** IntuitWalletService
- **Last updated:** 2026-05-06
- **Implements:** TBD (link added when PR opens)
- **Related ADRs:** [003-wallet-domain](../adrs/003-wallet-domain.md), [004-transactions-ledger](../adrs/004-transactions-ledger.md)

## Problem

ADR 003 describes the `wallets` table. ADR 004 describes `transactions` and `ledger_entries`, the partitioning strategy, and the system-wallet seeding. None of those tables exist in the database yet — the only Flyway migration so far is `V1__users.sql`.

Subsequent feature specs (send-payment, fund-wallet, wallet-create, …) need the schema in place before they can add their workflow + activity + controller. Splitting schema-bootstrap from feature work keeps the first feature spec focused on a single endpoint, and lets us validate the schema in isolation: Flyway applies cleanly against an empty DB, `ddl-auto=validate` later catches entity drift, and the SYSTEM wallets are queryable by name from day one.

This spec lands the entire ADR 003 + ADR 004 schema contract in one migration and stops there. No Java code, no endpoints — those land with the features that use them.

## Goals / Non-goals

- **Goals**
  - Single Flyway migration `V2__ledger_schema.sql` creating the `wallets`, `transactions`, and `ledger_entries` tables exactly as specified in ADR 003 §2 and ADR 004 §3 / §4.
  - Range-partition `transactions` and `ledger_entries` by `created_at` month at table creation; pre-create partitions for the current and next two months.
  - Seed the four `SYSTEM` wallets (`external_deposits`, `external_withdrawals`, `fee_revenue`, `treasury`) per ADR 004 §8.
  - Migration applies cleanly via `./gradlew bootRun` and `docker compose up -d --build` against an empty database.
- **Non-goals**
  - Any Java code: no JPA entities, no repositories, no core services, no workflow, no activity, no controller, no DTOs.
  - Any HTTP endpoint.
  - The `outbox` table — explicitly out of scope (ADR 004 §9).
  - The append-only Postgres trigger on `ledger_entries` — deferred per ADR 004 §9.
  - Partition-management automation (auto-create next month, archive old) — operational concern, not part of this spec.
  - Compliance / batch / QR / external-address tables.

## API

N/A — this spec adds no endpoints.

## Data model

Single Flyway migration: `src/main/resources/db/migration/V2__ledger_schema.sql`.

### `wallets` (per ADR 003 §2)

```sql
CREATE TABLE wallets (
    wallet_id         UUID PRIMARY KEY,
    intuit_account_id UUID NOT NULL UNIQUE,
    type              VARCHAR(16) NOT NULL
                      CHECK (type IN ('USER', 'SYSTEM')),
    status            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

No FK to `users(intuit_account_id)` — logical reference per ADR 003 §2.

### `transactions` (per ADR 004 §3)

```sql
CREATE TABLE transactions (
    tx_id                UUID         NOT NULL,
    type                 VARCHAR(16)  NOT NULL
                         CHECK (type IN ('SEND','RECEIVE','QR_PAY','FUND',
                                         'WITHDRAW','BATCH_DEBIT',
                                         'SETTLEMENT','REVERSAL')),
    from_type            VARCHAR(16)  NOT NULL CHECK (from_type IN ('WALLET','EXTERNAL')),
    from_party           TEXT         NOT NULL,
    to_type              VARCHAR(16)  NOT NULL CHECK (to_type   IN ('WALLET','EXTERNAL')),
    to_party             TEXT         NOT NULL,
    stablecoin           VARCHAR(16)  NOT NULL,
    amount               NUMERIC(28,8) NOT NULL CHECK (amount > 0),
    fee                  NUMERIC(28,8) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    status               VARCHAR(16)  NOT NULL
                         CHECK (status IN ('PENDING','COMPLETED','FAILED','REVERSED')),
    blockchain_tx_hash   TEXT,
    compliance_check_id  UUID,
    batch_id             UUID,
    idempotency_key      TEXT         NOT NULL,
    request_hash         TEXT         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tx_id, created_at)
) PARTITION BY RANGE (created_at);

CREATE UNIQUE INDEX idx_tx_idempotency ON transactions (from_party, idempotency_key);
CREATE INDEX        idx_tx_from_created ON transactions (from_party, created_at DESC);
CREATE INDEX        idx_tx_to_created   ON transactions (to_party,   created_at DESC);
CREATE INDEX        idx_tx_pending      ON transactions (status) WHERE status = 'PENDING';
```

### `ledger_entries` (per ADR 004 §4)

```sql
CREATE TABLE ledger_entries (
    entry_id           UUID          NOT NULL,
    tx_id              UUID          NOT NULL,
    wallet_id          UUID          NOT NULL,
    stablecoin         VARCHAR(16)   NOT NULL,
    entry_type         VARCHAR(16)   NOT NULL
                       CHECK (entry_type IN ('DEBIT','CREDIT','SETTLEMENT','REVERSAL')),
    amount             NUMERIC(28,8) NOT NULL CHECK (amount > 0),
    running_available  NUMERIC(28,8) NOT NULL CHECK (running_available >= 0),
    running_pending    NUMERIC(28,8) NOT NULL CHECK (running_pending   >= 0),
    entry_sequence     BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (entry_id, created_at)
) PARTITION BY RANGE (created_at);

CREATE UNIQUE INDEX idx_ledger_seq    ON ledger_entries (wallet_id, stablecoin, entry_sequence);
CREATE INDEX        idx_ledger_latest ON ledger_entries (wallet_id, stablecoin, entry_sequence DESC);
CREATE INDEX        idx_ledger_tx     ON ledger_entries (tx_id);
```

### Monthly partitions (current + next 2 months)

For each of `transactions` and `ledger_entries`, create three monthly child partitions covering `[first-of-current-month, first-of-month+3)` so writes during this development window always have a partition to land in. Naming convention: `<table>_yYYYYmMM` (e.g. `transactions_y2026m05`).

Authored statically in `V2__ledger_schema.sql` for May / June / July 2026 (current dev window). A periodic partition-management job is out of scope; production rollout will replace this static block with an automated rolling window.

### SYSTEM wallet seed (per ADR 004 §8)

```sql
INSERT INTO wallets (wallet_id, intuit_account_id, type, status) VALUES
    ('00000000-0000-0000-0000-00000000ed01',
     '00000000-0000-0000-0000-0000000000ed', 'SYSTEM', 'ACTIVE'),
    ('00000000-0000-0000-0000-00000000ed02',
     '00000000-0000-0000-0000-0000000000ee', 'SYSTEM', 'ACTIVE'),
    ('00000000-0000-0000-0000-00000000ed03',
     '00000000-0000-0000-0000-0000000000ef', 'SYSTEM', 'ACTIVE'),
    ('00000000-0000-0000-0000-00000000ed04',
     '00000000-0000-0000-0000-0000000000f0', 'SYSTEM', 'ACTIVE');
```

The `(wallet_id → logical_name)` mapping (`…ed01 → external_deposits` etc.) lives in a future `SystemWallets` constants class in `businesslogic.core`, added with the first feature spec that needs to look up a system wallet. This spec only commits the seed rows.

### Foreign keys

None. Per ADR 004 §2 ("No foreign keys, anywhere"), every inter-table reference is a logical reference; integrity is enforced by the activity's single-`@Transactional` write boundary plus daily reconciliation, neither of which is in this spec's scope.

## Workflow

N/A — this spec adds no workflow. Workflow code lands with the first transaction-creating feature.

## Acceptance criteria

- [ ] `./gradlew build` is clean.
- [ ] `docker compose down -v && docker compose up -d --build` produces an app that reaches `actuator/health` UP.
- [ ] Against the resulting DB, all four expected tables exist: `users`, `wallets`, `transactions`, `ledger_entries`.
- [ ] `transactions` and `ledger_entries` are partitioned (`SELECT * FROM pg_partitioned_table` returns rows for both); their three monthly child partitions exist (`SELECT inhrelid::regclass FROM pg_inherits WHERE inhparent IN ('transactions'::regclass, 'ledger_entries'::regclass)` returns six rows).
- [ ] `SELECT count(*) FROM wallets WHERE type='SYSTEM'` returns `4`.
- [ ] `SELECT wallet_id FROM wallets WHERE type='SYSTEM' ORDER BY wallet_id` returns the four reserved UUIDs (`…ed01`, `…ed02`, `…ed03`, `…ed04`).
- [ ] `flyway_schema_history` shows V2 applied with `success = true`.

## Test plan

- No new unit tests — there's no Java code in this spec. Flyway's own validation on Spring Boot startup is the test.
- Manual smoke: run `docker compose down -v && docker compose up -d --build`, then verify the acceptance criteria via pgweb at `http://localhost:8082` or `psql`.
- A `@DataJpaTest`-style smoke test could be added later (with the first feature spec's entities) to assert the schema validates against JPA. Not in scope here.

## Open questions

- None at the time of approval. (UUIDv7 vs UUIDv4 for `tx_id` / `entry_id` is settled per ADR 004 — the column type is just `UUID`; the choice of generator is a code-side concern that doesn't affect this migration.)
