# 007 — Send payment + fund wallet (on-ramp)

- **Status:** Approved
- **Author:** IntuitWalletService
- **Last updated:** 2026-05-06
- **Implements:** TBD
- **Related ADRs:** [001-tech-stack](../adrs/001-tech-stack.md), [003-wallet-domain](../adrs/003-wallet-domain.md), [004-transactions-ledger](../adrs/004-transactions-ledger.md)

## Problem

The `transactions` and `ledger_entries` tables exist (V2) and contain only the four seeded `SYSTEM` wallets on `wallets`. There is no code path that writes a transaction or any ledger entries. Wallet creation (spec 006) doesn't move money; it just provisions the row.

This spec lands the **first money-moving flows**, both on the same machinery:

1. **Fund (on-ramp)** — `SYSTEM external_deposits → USER wallet`. Lets us seed a user's balance so the rest of the system has something to test against. Conceptually represents "external money entered Intuit and was attributed to this user".
2. **Send (internal off-chain transfer)** — `USER wallet → USER wallet`. The unit transfer the broader product is built around: same DB, same region, single stablecoin.

Both share the `LedgerCoreService.executeTransfer(...)` atomic write — `transactions` row + sender debit + receiver credit, in one Spring `@Transactional`. The two flows differ only in (a) whether the "from" side is a SYSTEM or USER wallet, (b) the corresponding skip of the balance check on SYSTEM senders, and (c) the `transactions.type` enum value.

This is intentionally the **simplest** transaction shape: no fees, no compliance, no on-chain leg, no batch, no PENDING → COMPLETED two-phase status. The schema and workflow shape established here have to extend cleanly to those richer cases without restructuring.

## Goals / Non-goals

- **Goals**
  - `POST /api/v1/wallets/{walletId}/fund` credits a user's wallet from the `external_deposits` SYSTEM wallet. Type `FUND` in `transactions`.
  - `POST /api/v1/payments` debits one USER wallet and credits another USER wallet. Type `SEND` in `transactions`.
  - Atomic write per transaction: 1 row in `transactions` + 2 rows in `ledger_entries` (one DEBIT, one CREDIT), in one Spring `@Transactional`. Either all three land or none do.
  - Optimistic concurrency on `entry_sequence` — `MAX(entry_sequence)+1` per `(wallet_id, stablecoin)`, unique-constrained, activity retries on collision.
  - Idempotency: `(from_party, idempotency_key)` is unique on `transactions`. Same key + same canonical body → return original tx, HTTP 200. Same key + different body → 422.
  - Insufficient-balance and frozen-wallet checks short-circuit before any ledger write. Balance check is **skipped** when the sender is a SYSTEM wallet (system wallets can run negative; see the migration in §Data model).
  - Workflow-orchestrated per ADR 004 §1. Two workflows (`SendPaymentWorkflow`, `FundWalletWorkflow`) sharing one activity (`ExecuteTransferActivity`) and one core service (`LedgerCoreService`).
  - Deterministic workflow IDs for three-layer idempotency (HTTP pre-check, Temporal `WorkflowExecutionAlreadyStarted`, DB unique constraint).
- **Non-goals**
  - Off-ramp / withdraw (`USER → SYSTEM external_withdrawals`). Same machinery, different direction; deferred so this spec stays focused.
  - On-chain transactions (`blockchain_tx_hash` populated). External `WALLET ↔ EXTERNAL` flows where the external party is a chain address.
  - Fees and fee revenue routing. `fee` is hard-coded to `0`. No `fee_revenue` system wallet credit.
  - Compliance check integration. `compliance_check_id` stays `null`.
  - Batch payments. `batch_id` stays `null`.
  - Cross-region settlement / `SETTLEMENT` entry types.
  - Reversals (`type='REVERSAL'`, `entry_type='REVERSAL'`).
  - Get-payment / list-payments endpoints. Separate spec.
  - Multi-stablecoin transfers / swaps.
  - PENDING → COMPLETED two-phase status. Both flows here are single-step; the row goes straight to `COMPLETED` inside the same `@Transactional`. PENDING is reserved for genuinely in-flight money (on-chain awaiting confirmation, cross-region awaiting ack).
  - JWT / auth. Caller is trusted; party identifiers come from the request payload. Same carve-out as spec 006.

## API

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/wallets/{walletId}/fund` | On-ramp: credit a USER wallet from `external_deposits` SYSTEM wallet. |
| POST | `/api/v1/payments` | Send: debit one USER wallet and credit another. |

### POST `/api/v1/wallets/{walletId}/fund`

**Request:**
```json
{
  "amount":        "100.00",
  "stablecoin":    "USDC",
  "idempotencyKey": "fund-2026-05-06-001"
}
```

Validation:
- `walletId` (path): valid UUID, must reference an existing `USER`-type wallet with `status='ACTIVE'`.
- `amount`: required, decimal string, `> 0`, max 8 decimal places.
- `stablecoin`: required, non-blank, uppercase.
- `idempotencyKey`: required, non-blank, ≤ 64 chars. Uniqueness scope: `(external_deposits wallet_id, idempotencyKey)`.

**Response (TransactionResponse, see below):** 201 on first call, 200 on idempotent retry.

| Status | When |
| --- | --- |
| 201 | Funded on this call. |
| 200 | Idempotent retry (same key + matching `request_hash`). Returns the original transaction. |
| 400 | Validation failed. |
| 404 | Wallet doesn't exist. |
| 409 | Wallet is `FROZEN` or `CLOSED`. |
| 422 | `idempotencyKey` reused with a different body. |

### POST `/api/v1/payments`

**Request:**
```json
{
  "fromWalletId":   "11111111-2222-3333-4444-555555555555",
  "toWalletId":     "66666666-7777-8888-9999-aaaaaaaaaaaa",
  "amount":         "10.50",
  "stablecoin":     "USDC",
  "idempotencyKey": "5b4f3ee5-8a8a-4b6e-9c8c-9d8c8a8a8a8a"
}
```

Validation:
- `fromWalletId`, `toWalletId`: required UUIDs; must differ; both must reference existing `USER`-type wallets with `status='ACTIVE'`.
- `amount`, `stablecoin`, `idempotencyKey`: same rules as fund. Idempotency uniqueness scope: `(fromWalletId, idempotencyKey)`.

**Response (TransactionResponse):** 201 on first call, 200 on idempotent retry.

| Status | When |
| --- | --- |
| 201 | Sent on this call. |
| 200 | Idempotent retry. |
| 400 | Validation failed (incl. `from == to`, non-positive amount). |
| 404 | Either wallet doesn't exist. |
| 409 | Either wallet is `FROZEN` or `CLOSED`. |
| 422 | Insufficient balance, OR `idempotencyKey` reused with different body. Body's `error` field distinguishes (`INSUFFICIENT_BALANCE` vs `IDEMPOTENCY_CONFLICT`). |

### Shared response shape — `TransactionResponse`

```json
{
  "txId":         "01952af0-1234-7890-abcd-ef0123456789",
  "type":         "FUND",
  "fromWalletId": "00000000-0000-0000-0000-00000000ed01",
  "toWalletId":   "11111111-2222-3333-4444-555555555555",
  "amount":       "100.00",
  "stablecoin":   "USDC",
  "fee":          "0.00",
  "status":       "COMPLETED",
  "createdAt":    "2026-05-06T12:00:00Z"
}
```

For SEND, `type` is `SEND` and both party UUIDs are USER wallets.

## Data model

### Schema change: V3 — allow SYSTEM wallets to run negative `running_available`

ADR 004 §4 declares `CHECK (running_available >= 0)` on `ledger_entries`. The on-ramp pattern (DEBIT on `external_deposits`, CREDIT on user wallet) requires the SYSTEM source to accumulate negative `running_available` over time — `external_deposits` starts at 0 and decreases by `amount` on every fund. The CHECK blocks that.

The constraint was redundant defense-in-depth — `LedgerCoreService` already enforces `running_available >= amount` for USER senders before debiting. Dropping it lets the SYSTEM/USER asymmetry live in code (where the sender-type test belongs) rather than in DDL.

`src/main/resources/db/migration/V3__allow_system_negative_balance.sql`:

```sql
-- spec/007: SYSTEM wallets may run negative (e.g. external_deposits accumulates
-- DEBITs over time). Application code (LedgerCoreService) enforces the
-- non-negative invariant for USER wallets before each debit.

ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_running_available_check;
```

(Constraint name is the Postgres default for inline `CHECK (running_available >= 0)`. Verify the actual name on the deployed DB before running V3 — if it differs, the migration's `DROP CONSTRAINT` line gets adjusted.)

ADR 004 §4 will get a small amendment in the same PR noting the rule moved from DB to code, with the rationale.

### Writes per transaction (both flows)

- `transactions`: one row.
  - **FUND:** `type='FUND'`, `from_type='WALLET'`, `from_party=<external_deposits wallet_id as text>`, `to_type='WALLET'`, `to_party=<targetWalletId as text>`, `status='COMPLETED'`, `fee=0`, `compliance_check_id=null`, `batch_id=null`, `blockchain_tx_hash=null`, `idempotency_key`, `request_hash`.
  - **SEND:** `type='SEND'`, both party fields are USER wallet IDs, otherwise identical.
- `ledger_entries`: exactly two rows (DEBIT on sender, CREDIT on receiver).
  - Sender DEBIT: `wallet_id=<sender>`, `entry_type='DEBIT'`, `amount`, `running_available=<prev_avail - amount>`, `running_pending=<prev_pending>`, `entry_sequence=<MAX+1>`.
  - Receiver CREDIT: `wallet_id=<receiver>`, `entry_type='CREDIT'`, `amount`, `running_available=<prev_avail + amount>`, `running_pending=<prev_pending>`, `entry_sequence=<MAX+1>`.
- `tx_id` and both `entry_id`s are UUIDv7, generated server-side inside the activity (Gradle dep: `com.fasterxml.uuid:java-uuid-generator:5.1.0`, ~30 KB jar; same generator reused by every future ledger-writing spec).

### Reads

- `wallets`: existence + status check on each party (and `type` check, to know whether to enforce the balance rule).
- `ledger_entries`: latest row per `(wallet_id, stablecoin)` for both parties (one query each via `findTopByWalletIdAndStablecoinOrderByEntrySequenceDesc(...)`). If null, treat balances as 0.
- `transactions`: idempotency-key lookup before write.

### `SystemWallets` constants class

`com.intuit.walletservice.businesslogic.core.SystemWallets`:

```java
public final class SystemWallets {
    public static final UUID EXTERNAL_DEPOSITS    = UUID.fromString("00000000-0000-0000-0000-00000000ed01");
    public static final UUID EXTERNAL_WITHDRAWALS = UUID.fromString("00000000-0000-0000-0000-00000000ed02");
    public static final UUID FEE_REVENUE          = UUID.fromString("00000000-0000-0000-0000-00000000ed03");
    public static final UUID TREASURY             = UUID.fromString("00000000-0000-0000-0000-00000000ed04");

    private SystemWallets() {}
}
```

This is the only file that hardcodes the seeded UUIDs (per ADR 004 §8).

## Workflow

Two workflow types, one shared activity, one shared core service.

### Shared core: `LedgerCoreService`

`com.intuit.walletservice.businesslogic.core.LedgerCoreService`. Methods:

- `executeTransfer(ExecuteTransferRequest req) -> ExecuteTransferResult` — single Spring `@Transactional`. The atomic write protocol:
  1. **Idempotency check.** `transactionRepository.findByFromPartyAndIdempotencyKey(fromParty, idempotencyKey)`:
     - Found AND `request_hash` matches → return existing tx (`created=false`). No further writes.
     - Found AND `request_hash` mismatches → throw `IdempotencyConflictException`.
     - Not found → continue.
  2. **Load both wallets** by id; verify each exists and `status='ACTIVE'`. (Existence + status checks happen in `executeTransfer` even though the workflow's validate activity already did them — defense in depth, plus the activity's view might be stale by the time the @Transactional opens.)
  3. **Balance check (sender side, conditional).** If sender's `wallets.type = 'USER'`:
     - Read sender's latest entry for `(wallet_id, stablecoin)`. If null, `running_available = 0`.
     - If `running_available < amount` → throw `InsufficientBalanceException`.
     - If `wallets.type = 'SYSTEM'`, skip the balance check (system wallets are allowed to run negative; see V3).
  4. **Read receiver's latest entry** for carry-forward of `running_pending`. Treat null as zero.
  5. **Compute next sequences and balances.**
  6. **Insert all three rows.** `tx_id`, both `entry_id`s are UUIDv7.
  7. **Race resolution.**
     - `DataIntegrityViolationException` on `idx_tx_idempotency` → re-run the idempotency lookup; return the resolved row with `created=false`.
     - `DataIntegrityViolationException` on `idx_ledger_seq` → throw out, let Temporal retry the activity (default policy: 5 attempts; each retry re-reads `MAX(entry_sequence)` and re-validates balance).
  8. Return `ExecuteTransferResult(txView, created=true)`.

### Workflow: `SendPaymentWorkflow`

- **Class:** `com.intuit.walletservice.businesslogic.workflow.SendPaymentWorkflow` (`@WorkflowInterface`) + `SendPaymentWorkflowImpl` (`@WorkflowImpl(workers = "wallet-service-worker")`).
- **Method:** `TransactionView send(SendPaymentInput input)`.
- **Workflow ID:** `send-payment-{fromWalletId}-{idempotencyKey}`. Reuse policy: `ALLOW_DUPLICATE_FAILED_ONLY`.
- **Activities (sequential):**
  1. `ValidateTransferActivity.validate(...)` — calls `WalletCoreService.getById(...)` for both sides, asserts both exist and both are ACTIVE, asserts `from != to`. Throws `WalletNotFoundException` (→ 404), `WalletNotActiveException` (→ 409), or `IllegalArgumentException` (→ 400). All three registered as **non-retryable**.
  2. `ExecuteTransferActivity.execute(...)` — wraps `LedgerCoreService.executeTransfer(...)` with `type='SEND'` and `from_type/to_type='WALLET'`. Returns `ExecuteTransferResult`.

### Workflow: `FundWalletWorkflow`

- **Class:** `FundWalletWorkflow` + `FundWalletWorkflowImpl`.
- **Method:** `TransactionView fund(FundWalletInput input)` where `input` carries `targetWalletId, amount, stablecoin, idempotencyKey`.
- **Workflow ID:** `fund-wallet-{targetWalletId}-{idempotencyKey}`.
- **Activities (sequential):**
  1. `ValidateFundActivity.validate(...)` — calls `WalletCoreService.getById(targetWalletId)`, asserts existence + ACTIVE + `type='USER'`. (FUND only credits user wallets; the on-ramp into another SYSTEM wallet would be a future operations spec.) Throws same exceptions as above with same status mappings.
  2. `ExecuteTransferActivity.execute(...)` — same activity class as Send, called with `type='FUND'`, `from = SystemWallets.EXTERNAL_DEPOSITS`, `to = targetWalletId`, `from_type/to_type='WALLET'`.

### Activity contract details (both workflows)

- **`ExecuteTransferActivity`** — single class, single method. Receives an `ExecuteTransferRequest` carrying `txType, fromWalletId, toWalletId, fromType, toType, amount, stablecoin, idempotencyKey, requestHash`.
- Start-to-close timeout: 10s.
- Default retry policy with up to 5 attempts. Non-retryable exceptions: `IdempotencyConflictException`, `InsufficientBalanceException`, `WalletNotFoundException`, `WalletNotActiveException`. Retryable: any `DataIntegrityViolationException` on `idx_ledger_seq` (sequence collision).

### Three-layer idempotency

1. **Controller pre-check.** Before starting the workflow, the controller calls a new `TransactionCoreService.findByIdempotency(fromParty, idempotencyKey)` (read-only). On match with `request_hash` equal → return 200 with the existing tx and don't start a workflow.
2. **Temporal workflow id.** Deterministic id collides on duplicate (fromParty, idempotencyKey). Reuse policy `ALLOW_DUPLICATE_FAILED_ONLY` rejects re-start of a successful run; controller catches `WorkflowExecutionAlreadyStarted` and falls back to the same lookup as #1.
3. **DB unique constraint.** `idx_tx_idempotency` is the source of truth. Race past #1 and #2 hits this and is resolved inside `LedgerCoreService.executeTransfer`.

### `request_hash` definition

SHA-256 of a canonical JSON `(fromWalletId, toWalletId, amount, stablecoin)`. The `idempotencyKey` itself is **not** part of the hash. For FUND, `fromWalletId` is `SystemWallets.EXTERNAL_DEPOSITS` and `toWalletId` is the path-parameter wallet. Hash is computed in the controller before the workflow starts and passed into the activity.

## Acceptance criteria

### Fund

- [ ] Funding an existing ACTIVE USER wallet returns 201 with `type='FUND'`, `status='COMPLETED'`, fresh `txId`. `transactions` has one row; `ledger_entries` has one DEBIT on `external_deposits` and one CREDIT on the target wallet, both with the same `tx_id`.
- [ ] After N funds for the same target/stablecoin, target wallet's latest `running_available` equals the sum of all fund amounts; `external_deposits`'s latest `running_available` equals the negative of the same sum.
- [ ] Repeating the same fund (same `idempotencyKey`, same body) returns 200 with the same `txId`.
- [ ] Same `idempotencyKey` with changed `amount` returns 422 (`IDEMPOTENCY_CONFLICT`).
- [ ] Funding a non-existent wallet returns 404; FROZEN/CLOSED returns 409.

### Send

- [ ] Sender already has sufficient balance (after a prior fund), receiver exists and is ACTIVE: POST returns 201, `transactions.type='SEND'`, two ledger entries (DEBIT sender, CREDIT receiver). Sender's `running_available` decreased by `amount`; receiver's increased by `amount`. The two amounts net to zero.
- [ ] Idempotent retry returns 200 with same `txId`.
- [ ] Idempotency-key reuse with different body returns 422.
- [ ] Sender or receiver missing → 404; either FROZEN/CLOSED → 409; insufficient balance → 422 (`INSUFFICIENT_BALANCE`); `from==to` / negative amount / malformed UUID / missing field → 400.

### Cross-cutting

- [ ] Successful POST creates one Temporal workflow execution with exactly two activity completions (`ValidateTransfer`/`ValidateFund`, `ExecuteTransfer`).
- [ ] Concurrent same-sender same-stablecoin POSTs (manually exercised) cause one or more `entry_sequence` collisions; the activity retries are visible in the Temporal UI; final state has strictly monotonic `entry_sequence` per `(wallet_id, stablecoin)` with no gaps.
- [ ] V3 migration applies cleanly; `external_deposits.running_available` is observable as negative after the first fund.
- [ ] `./gradlew build` is clean.

## Test plan

### `LedgerCoreServiceTest` (`@DataJpaTest`, no Temporal)

Tests the shared atomic write logic. Both directions (fund, send) tested through one entry point.

- `executeTransfer_send_happyPath_writesTxAndTwoEntries`.
- `executeTransfer_fund_happyPath_writesTxAndTwoEntries`.
- `executeTransfer_send_runningBalancesCarriedForward`.
- `executeTransfer_fund_systemWalletGoesNegative`.
- `executeTransfer_send_idempotencyKeySameHash_returnsExistingNoNewRows`.
- `executeTransfer_send_idempotencyKeyDifferentHash_throwsIdempotencyConflict`.
- `executeTransfer_send_insufficientBalance_throwsAndWritesNothing`.
- `executeTransfer_fund_systemSenderSkipsBalanceCheck` (allowed even when `external_deposits` already has a hugely negative balance).
- `executeTransfer_send_concurrentEntrySequenceCollision_resolvedByRetry` (pre-insert a competing entry directly via the repository).
- `executeTransfer_send_senderHasNoPriorEntries_treatsBalanceAsZero` (always insufficient).

### `SendPaymentWorkflowTest`, `FundWalletWorkflowTest` (`TestWorkflowEnvironment`)

Same shape for both. Mock activities; assert call order and that failures short-circuit before `ExecuteTransfer`.

- `send_validInput_runsValidateThenExecute_returnsResult`.
- `send_unknownWallet_skipsExecute` (mocked validate throws).
- `send_frozenWallet_skipsExecute`.
- `send_insufficientBalance_executeAttemptedOnce` (insufficient balance is detected inside execute, not validate; execute is called but no writes).
- `fund_validInput_runsValidateThenExecute_returnsResult`.
- `fund_unknownWallet_skipsExecute`.

### `PaymentControllerTest`, `FundControllerTest` (`@WebMvcTest`)

(Or one combined controller with both endpoints — see open question B.) Mock `WorkflowClient` and `TransactionCoreService`.

- All status-code paths from the API table above.
- Idempotency pre-check: pre-stub `TransactionCoreService.findByIdempotency` to return a hit; assert no `WorkflowClient` interaction and 200 response.
- `WorkflowExecutionAlreadyStarted` fallback: stub workflow stub to throw, stub the lookup to return a hit; assert 200.

### Manual smoke

```bash
# Two users + two wallets
U1=$(curl -s -X POST :8081/api/v1/users -H 'Content-Type: application/json' \
     -d '{"email":"alice@example.com","role":"CONSUMER","homeRegion":"us-east-1"}' | jq -r .intuitAccountId)
U2=$(curl -s -X POST :8081/api/v1/users -H 'Content-Type: application/json' \
     -d '{"email":"bob@example.com","role":"CONSUMER","homeRegion":"us-east-1"}' | jq -r .intuitAccountId)
W1=$(curl -s -X POST :8081/api/v1/wallets -H 'Content-Type: application/json' \
     -d "{\"intuitAccountId\":\"$U1\"}" | jq -r .walletId)
W2=$(curl -s -X POST :8081/api/v1/wallets -H 'Content-Type: application/json' \
     -d "{\"intuitAccountId\":\"$U2\"}" | jq -r .walletId)

# Fund Alice with 100 USDC
curl -i -X POST ":8081/api/v1/wallets/$W1/fund" \
     -H 'Content-Type: application/json' \
     -d "{\"amount\":\"100.00\",\"stablecoin\":\"USDC\",\"idempotencyKey\":\"$(uuidgen)\"}"
# Expect 201 + COMPLETED

# Send 10.50 from Alice to Bob
curl -i -X POST :8081/api/v1/payments \
     -H 'Content-Type: application/json' \
     -d "{\"fromWalletId\":\"$W1\",\"toWalletId\":\"$W2\",\"amount\":\"10.50\",\"stablecoin\":\"USDC\",\"idempotencyKey\":\"$(uuidgen)\"}"
# Expect 201 + COMPLETED

# Idempotent retry of same send (same key) — also expect 201 because idempotencyKey is fresh; reuse a captured key to exercise 200
```

## Open questions

None at approval. Decisions logged here for traceability:

- `status='COMPLETED'` direct, single-`@Transactional` write. Two-phase deferred until on-chain settlement.
- No stablecoin allow-list. Any non-blank uppercase string accepted.
- Fee hard-coded to `0`. No `fee_revenue` routing.
- `request_hash` is `SHA-256` of `(fromWalletId, toWalletId, amount, stablecoin)` — `idempotencyKey` itself excluded from the hash.
- UUIDv7 via `com.fasterxml.uuid:java-uuid-generator:5.1.0` (added as a Gradle dep).
- `entry_sequence` collision retry is Temporal-driven (default 5 attempts).
- V3 drops the `running_available >= 0` CHECK; non-negative is enforced in `LedgerCoreService` for USER wallets only.
- One controller (`PaymentController`) handles both `/api/v1/payments` and `/api/v1/wallets/{walletId}/fund`.
- Two workflows (`SendPaymentWorkflow`, `FundWalletWorkflow`) share one activity (`ExecuteTransferActivity`) and one core service (`LedgerCoreService`).
- `SystemWallets` constants class (no config-driven mapping for the POC).
- V3 uses Postgres's default constraint name `ledger_entries_running_available_check`. If `\d ledger_entries` shows a different name on the deployed DB, the migration's `DROP CONSTRAINT` line is a 1-line fix.
