# 008 — Get wallet balances

- **Status:** Approved
- **Author:** IntuitWalletService
- **Last updated:** 2026-05-06
- **Implements:** TBD
- **Related ADRs:** [003-wallet-domain](../adrs/003-wallet-domain.md), [004-transactions-ledger](../adrs/004-transactions-ledger.md)

## Problem

After spec 007, transactions land in `transactions` and `ledger_entries`. The latest `ledger_entries` row per `(wallet_id, stablecoin)` carries the wallet's current `running_available` and `running_pending` (ADR 004 §4). There's no API path to read these. Today the only way to see a wallet's balance is to inspect the database directly via pgweb.

This spec adds two read endpoints that surface those balances. Both are pure reads — no transaction state changes — so per ADR 003 §1 they take the direct `controller → core → repository → DB` path. **No Temporal workflow.**

## Goals / Non-goals

- **Goals**
  - `GET /api/v1/wallets/{walletId}/balances` — list every stablecoin balance for a wallet.
  - `GET /api/v1/wallets/{walletId}/balances/{stablecoin}` — fetch a single stablecoin's balance.
  - O(1) read per stablecoin via `idx_ledger_latest` (declared in spec 005 / V2). The list endpoint is bounded by the number of distinct stablecoins on the wallet, which the POC will keep small.
  - Wallet existence enforced: 404 if `walletId` doesn't exist.
  - Single-stablecoin lookup against a wallet that has never held that stablecoin returns **404** — the response distinguishes "stablecoin not enabled for this wallet" (no ledger entry has ever existed) from "balance is 0" (latest entry's `running_available = 0`).
- **Non-goals**
  - Transaction history endpoints (separate spec).
  - Aggregations across wallets (separate spec; would need authn anyway).
  - Pending vs available accounting beyond echoing whatever the latest ledger entry recorded. Manipulating `running_pending` is the next ledger-writing spec, not this one.
  - Caching, ETags, conditional GETs. Reads go to live DB on every call.
  - Real-time push / SSE / WebSocket balance updates.
  - JWT / auth. Caller is trusted; consistent with specs 006 and 007.

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/v1/wallets/{walletId}/balances` | List all stablecoin balances for the wallet. |
| GET | `/api/v1/wallets/{walletId}/balances/{stablecoin}` | Fetch a single stablecoin's balance. |

### `GET /api/v1/wallets/{walletId}/balances`

**Response (`WalletBalancesResponse`):**

```json
{
  "walletId": "11111111-2222-3333-4444-555555555555",
  "balances": [
    {
      "stablecoin": "USDC",
      "runningAvailable": "100.00",
      "runningPending": "0.00",
      "lastEntrySequence": 5,
      "lastEntryAt": "2026-05-06T12:00:00Z"
    },
    {
      "stablecoin": "USDT",
      "runningAvailable": "50.00",
      "runningPending": "0.00",
      "lastEntrySequence": 2,
      "lastEntryAt": "2026-05-06T12:05:00Z"
    }
  ]
}
```

Sorted by `stablecoin` ascending. Empty `balances: []` if the wallet has no entries yet (newly provisioned, never funded).

| Status | When |
| --- | --- |
| 200 | Wallet found. `balances` may be empty. |
| 400 | `walletId` not a valid UUID. |
| 404 | Wallet doesn't exist. |

### `GET /api/v1/wallets/{walletId}/balances/{stablecoin}`

**Response (`WalletBalanceResponse`):**

```json
{
  "walletId": "11111111-2222-3333-4444-555555555555",
  "stablecoin": "USDC",
  "runningAvailable": "100.00",
  "runningPending": "0.00",
  "lastEntrySequence": 5,
  "lastEntryAt": "2026-05-06T12:00:00Z"
}
```

If the wallet has no `ledger_entries` row for the given stablecoin, the response is **404** with body:

```json
{ "error": "STABLECOIN_NOT_ENABLED", "message": "Stablecoin USDC has not been used by wallet <id>" }
```

A wallet that funded then fully drained a stablecoin still has a latest ledger entry (with `running_available = 0`), so it returns 200 with zeros. The 404 case is reserved for "no entry has ever existed" — i.e., the stablecoin was never enabled for this wallet via a fund or transfer.

| Status | When |
| --- | --- |
| 200 | Wallet found AND has at least one ledger entry for the stablecoin. Returns the latest entry's running balance (which may be 0). |
| 400 | `walletId` not a valid UUID, or `stablecoin` blank. |
| 404 | Wallet doesn't exist (`WALLET_NOT_FOUND`), OR wallet exists but has no ledger entry for the given stablecoin (`STABLECOIN_NOT_ENABLED`). Body's `error` field distinguishes. |

## Data model

No schema change. Both endpoints read existing tables.

- **List endpoint** runs a JPQL query that returns the latest `ledger_entries` row per `(wallet_id, stablecoin)` for the given wallet. JPQL (portable across H2 + Postgres):
  ```java
  @Query("""
      SELECT le FROM LedgerEntry le
      WHERE le.walletId = :walletId
        AND le.entrySequence = (
          SELECT MAX(le2.entrySequence) FROM LedgerEntry le2
          WHERE le2.walletId = le.walletId AND le2.stablecoin = le.stablecoin)
      ORDER BY le.stablecoin
      """)
  List<LedgerEntry> findLatestEntriesForWallet(@Param("walletId") UUID walletId);
  ```
  This rides the existing `idx_ledger_latest` (`wallet_id, stablecoin, entry_sequence DESC`) for the inner aggregation; the planner uses an index-only scan per stablecoin.

- **Single-stablecoin endpoint** uses the existing repository method `findTopByWalletIdAndStablecoinOrderByEntrySequenceDesc(walletId, stablecoin)` from spec 007 — single row, single index lookup.

## Workflow

N/A. Pure reads, direct controller → core → repository per ADR 003 §1.

## Files touched

- **Modified:**
  - `dal/repository/LedgerEntryRepository.java` — add `findLatestEntriesForWallet(UUID)`.
  - `businesslogic/core/LedgerCoreService.java` — add `getBalance(walletId, stablecoin)` and `getBalances(walletId)`. Both `@Transactional(readOnly = true)`.
  - `service/controller/WalletController.java` — add two `@GetMapping` methods. Existence check via the existing `walletCoreService.getById(walletId)` (which already throws `WalletNotFoundException` → 404 via `ApiExceptionHandler`).
- **New:**
  - `service/dto/WalletBalanceResponse.java` — single balance.
  - `service/dto/WalletBalancesResponse.java` — list wrapper.
  - `businesslogic/core/WalletBalanceView.java` — core-layer record shared by both response builders.
  - `businesslogic/core/StablecoinBalanceNotFoundException.java` — thrown by `LedgerCoreService.getBalance` when no entry exists for the (wallet, stablecoin) pair. Mapped to 404 with `error: STABLECOIN_NOT_ENABLED`.
- **Modified (additional):**
  - `service/controller/ApiExceptionHandler.java` — handler entry for `StablecoinBalanceNotFoundException` → 404.
- **Not touched:** No Flyway migration. No workflow / activity. `MethodArgumentTypeMismatchException` from Spring handles malformed UUID → 400 by default.

## Acceptance criteria

### List endpoint
- [ ] Existing wallet with three different stablecoin balances → 200, `balances` array of length 3, sorted by stablecoin ascending.
- [ ] Existing wallet with no entries → 200, `balances: []`.
- [ ] Unknown wallet → 404.
- [ ] Malformed `walletId` UUID → 400.
- [ ] After a fund of `100 USDC` then a send of `10 USDC`, the response shows `runningAvailable = "90"` for USDC.

### Single-stablecoin endpoint
- [ ] Existing wallet, has entries for the stablecoin → 200 with the latest balance.
- [ ] Existing wallet, has entries for the stablecoin but latest `running_available = 0` (funded then fully drained) → 200 with zeros.
- [ ] Existing wallet, NO entries for the stablecoin → 404 with `error: STABLECOIN_NOT_ENABLED`.
- [ ] Unknown wallet → 404 with the existing `WalletNotFoundException` message (different from STABLECOIN_NOT_ENABLED).
- [ ] Malformed `walletId` → 400.

### Cross-cutting
- [ ] No Temporal workflow execution is created on either endpoint (Temporal UI shows no new runs after a series of GET calls).
- [ ] `./gradlew build` is clean.

## Test plan

- **`LedgerCoreServiceTest`** — extend the existing test class:
  - `getBalance_existingEntry_returnsLatestRunningAvailable`.
  - `getBalance_noEntry_throwsStablecoinBalanceNotFound`.
  - `getBalance_drainedToZero_returns200WithZeros`.
  - `getBalance_afterMultipleSends_reflectsLatestRunningAvailable`.
  - `getBalances_walletWithMultipleStablecoins_returnsOnePerStablecoinSorted`.
  - `getBalances_walletWithNoEntries_returnsEmptyList`.
  - `getBalances_returnsOnlyLatestEntryPerStablecoin` (insert several entries per stablecoin manually; assert only one row per stablecoin in the response and that it's the highest sequence).

- **`WalletControllerTest`** — extend with:
  - `getBalances_existing_returns200WithList`.
  - `getBalances_existingNoEntries_returns200WithEmptyList`.
  - `getBalances_unknownWallet_returns404` (mock `walletCoreService.getById` to throw).
  - `getBalances_malformedUuid_returns400`.
  - `getBalance_existingWithEntries_returns200WithLatest`.
  - `getBalance_existingNoStablecoinEntry_returns404WithStablecoinNotEnabled`.
  - `getBalance_unknownWallet_returns404WithWalletNotFoundError`.
  - `getBalance_malformedUuid_returns400`.

- **Manual smoke** via `docker compose up -d --build`:
  ```bash
  # Reuse U1/W1 from spec 007 smoke
  curl -s ":8081/api/v1/wallets/$W1/balances" | jq
  curl -s ":8081/api/v1/wallets/$W1/balances/USDC" | jq
  curl -i ":8081/api/v1/wallets/$W1/balances/EURC"        # never funded — expect 404 STABLECOIN_NOT_ENABLED
  curl -i ":8081/api/v1/wallets/$(uuidgen)/balances"     # 404
  ```
  Temporal UI shows no new workflow executions for any of the above.

## Open questions

None at approval. Decisions logged here for traceability:

- **URL shape:** wallet-scoped — `/api/v1/wallets/{walletId}/balances[/{stablecoin}]`.
- **Single-stablecoin 404 vs zeros:** 404 with `error: STABLECOIN_NOT_ENABLED` when no ledger entry has ever existed. 200 with zeros when entries exist but the latest `running_available = 0` (funded then drained). The list endpoint mirrors this: only stablecoins with at least one ledger entry appear; never-used stablecoins are absent.
- **Response shape:** wrapper `{walletId, balances:[…]}` for the list; single-stablecoin echoes `walletId`.
- **Cursor metadata:** `lastEntrySequence` and `lastEntryAt` are included on both shapes.
- **Stablecoin allow-list:** none. Consistent with spec 007.
- **Sort order:** ascending by `stablecoin`.
- **Zero-balance entries on list:** shown if the wallet has ever held the stablecoin (i.e. there's a ledger entry, even if its `running_available = 0`). Never-held stablecoins are absent.
- **Status gate on reads:** none — `FROZEN` and `CLOSED` wallets are readable. Status checks remain on write paths only.
