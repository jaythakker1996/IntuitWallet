# 009 — Get wallet transactions

- **Status:** Approved
- **Author:** IntuitWalletService
- **Last updated:** 2026-05-06
- **Implements:** TBD
- **Related ADRs:** [003-wallet-domain](../adrs/003-wallet-domain.md), [004-transactions-ledger](../adrs/004-transactions-ledger.md)

## Problem

After spec 007, transactions land in the `transactions` table with `from_party` / `to_party` carrying the wallet UUIDs. After spec 008, callers can read balances. They still can't read **transaction history** — there's no endpoint that returns the rows from `transactions` that involve a given wallet.

This spec adds two read endpoints. Both are pure DB reads — no transaction state changes — so per ADR 003 §1 they take the direct `controller → core → repository → DB` path. **No Temporal.**

## Goals / Non-goals

- **Goals**
  - `GET /api/v1/wallets/{walletId}/transactions` — list transactions where the wallet appears on either side (`from_party` or `to_party`), most recent first.
  - `GET /api/v1/wallets/{walletId}/transactions/{txId}` — fetch a single transaction, scoped to the wallet (404 if the tx exists but doesn't involve this wallet).
  - Each returned transaction is annotated with a wallet-relative `direction` (`OUTBOUND` if wallet is `from_party`, `INBOUND` if wallet is `to_party`) AND with the wallet's side of the ledger entry: `entryType` (`DEBIT` or `CREDIT`), `runningAvailableAfter`, `runningPendingAfter`, and `entrySequence`. The counterparty's ledger entry is intentionally not exposed.
  - Wallet existence enforced: 404 with `error: WALLET_NOT_FOUND` if `walletId` doesn't exist.
  - List endpoint returns the latest **100** transactions. No pagination params for the POC; pagination is a future spec.
- **Non-goals**
  - Pagination (cursor or page-based). Hard cap at 100 most-recent transactions; `?limit=` and `?before=` are deferred.
  - Filtering by `type`, `status`, `stablecoin`, or date range. Future spec.
  - Cross-wallet transaction queries (e.g. all transactions in the system). No admin endpoint here.
  - Aggregations (totals, counts, summaries). Separate spec if needed.
  - Reading `ledger_entries` directly. The transaction history is at the `transactions` granularity; per-entry views (debit + credit pair) belong to a separate spec.
  - JWT / auth. Caller is trusted, consistent with specs 006–008.

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/v1/wallets/{walletId}/transactions` | List the latest 100 transactions involving the wallet, newest first. |
| GET | `/api/v1/wallets/{walletId}/transactions/{txId}` | Fetch a single transaction scoped to the wallet. |

### `GET /api/v1/wallets/{walletId}/transactions`

**Response (`WalletTransactionsResponse`):**

```json
{
  "walletId": "11111111-2222-3333-4444-555555555555",
  "transactions": [
    {
      "txId":                 "01952af0-1234-7890-abcd-ef0123456789",
      "type":                 "SEND",
      "direction":            "OUTBOUND",
      "entryType":            "DEBIT",
      "fromWalletId":         "11111111-2222-3333-4444-555555555555",
      "toWalletId":           "66666666-7777-8888-9999-aaaaaaaaaaaa",
      "amount":               "10.50",
      "stablecoin":           "USDC",
      "fee":                  "0",
      "status":               "COMPLETED",
      "runningAvailableAfter":"89.50",
      "runningPendingAfter":  "0",
      "entrySequence":        2,
      "createdAt":            "2026-05-06T12:05:00Z"
    },
    {
      "txId":                 "01952ad0-1111-7890-abcd-ef0123456789",
      "type":                 "FUND",
      "direction":            "INBOUND",
      "entryType":            "CREDIT",
      "fromWalletId":         "00000000-0000-0000-0000-00000000ed01",
      "toWalletId":           "11111111-2222-3333-4444-555555555555",
      "amount":               "100.00",
      "stablecoin":           "USDC",
      "fee":                  "0",
      "status":               "COMPLETED",
      "runningAvailableAfter":"100.00",
      "runningPendingAfter":  "0",
      "entrySequence":        1,
      "createdAt":            "2026-05-06T12:00:00Z"
    }
  ]
}
```

Sorted by `createdAt DESC`. Empty `transactions: []` if the wallet has no history yet.

| Status | When |
| --- | --- |
| 200 | Wallet found. `transactions` may be empty. |
| 400 | `walletId` not a valid UUID. |
| 404 | Wallet doesn't exist (`error: WALLET_NOT_FOUND`). |

### `GET /api/v1/wallets/{walletId}/transactions/{txId}`

**Response (`WalletTransactionResponse`):** same shape as a single entry in the list above (with `direction`).

| Status | When |
| --- | --- |
| 200 | Wallet exists AND the transaction exists AND it involves this wallet. |
| 400 | `walletId` or `txId` not a valid UUID. |
| 404 | Wallet doesn't exist (`error: WALLET_NOT_FOUND`); OR transaction doesn't exist (or exists but doesn't involve this wallet) (`error: TRANSACTION_NOT_FOUND`). Body's `error` field distinguishes. |

The "exists but doesn't involve this wallet" case collapses into `TRANSACTION_NOT_FOUND` deliberately — from this wallet's perspective the transaction doesn't exist. Surfacing it as a different error code would leak information about other wallets' transaction IDs.

## Data model

No schema change. Both endpoints read existing tables (`transactions`, `wallets`).

- **List query** — JPQL with `OR` against `from_party`/`to_party`, ordered by `createdAt DESC`, with a `Pageable` cap at 100:
  ```java
  @Query("""
      SELECT t FROM Transaction t
      WHERE t.fromParty = :walletId OR t.toParty = :walletId
      ORDER BY t.createdAt DESC
      """)
  List<Transaction> findForWallet(@Param("walletId") String walletId, Pageable pageable);
  ```
  Rides the existing `idx_tx_from_created` and `idx_tx_to_created` indexes (both `(party, created_at DESC)`) — Postgres planner does an index merge or two index scans. Acceptable for POC.

- **Single-tx query** — JPQL combining `tx_id` lookup with the wallet-scoping check:
  ```java
  @Query("""
      SELECT t FROM Transaction t
      WHERE t.txId = :txId
        AND (t.fromParty = :walletId OR t.toParty = :walletId)
      """)
  Optional<Transaction> findByIdAndWallet(@Param("txId") UUID txId,
                                          @Param("walletId") String walletId);
  ```
  Single PK lookup + post-filter. The PK index on `tx_id` does the heavy lifting.

`from_party` / `to_party` are TEXT columns (per ADR 004); the queries pass `walletId.toString()` to match.

## Workflow

N/A. Pure reads, direct controller → core → repository per ADR 003 §1.

## Files touched

- **Modified:**
  - `dal/repository/TransactionRepository.java` — add `findForWallet(walletId, Pageable)` and `findByIdAndWallet(txId, walletId)`.
  - `businesslogic/core/LedgerCoreService.java` — add `getTransactionsForWallet(UUID walletId)` (returns up to 100, in DESC order) and `getTransactionForWallet(UUID walletId, UUID txId)`. Both `@Transactional(readOnly = true)`.
  - `service/controller/WalletController.java` — two new `@GetMapping` methods. Existence check on the wallet via the existing `walletCoreService.getById(walletId)` first.
  - `service/controller/ApiExceptionHandler.java` — handler entry for `TransactionNotFoundException` → 404 with `error: TRANSACTION_NOT_FOUND`.
- **New:**
  - `service/dto/WalletTransactionResponse.java` — single tx + `direction`.
  - `service/dto/WalletTransactionsResponse.java` — `{walletId, transactions: [...]}` wrapper.
  - `businesslogic/core/WalletTransactionView.java` — core-layer record carrying the tx + computed `direction`.
  - `businesslogic/core/TransactionNotFoundException.java`.

- **Not touched:** No Flyway migration. No workflow / activity. The existing `TransactionResponse` (spec 007) is **not** reused — it doesn't carry `direction`, and overloading it with an optional field that's only meaningful in a wallet-scoped context is uglier than a separate DTO. The existing `WalletNotFoundException` already maps to 404; only the new `TransactionNotFoundException` adds a handler entry.

## Acceptance criteria

### List endpoint
- [ ] Wallet with N (≤100) transactions in mixed directions → 200, `transactions` array of length N, sorted by `createdAt DESC`. Each entry has the correct `direction` (`OUTBOUND` if wallet matches `fromParty`, `INBOUND` if matches `toParty`).
- [ ] Wallet with > 100 transactions → 200 with the latest 100; the older ones are silently dropped.
- [ ] Wallet with no transactions → 200, `transactions: []`.
- [ ] Unknown wallet → 404 with `error: WALLET_NOT_FOUND`.
- [ ] Malformed `walletId` UUID → 400.
- [ ] After fund(100 USDC) on wallet A, then send(10 USDC) from A to B: A's list shows two transactions — the SEND first (newest) with `direction=OUTBOUND`, then the FUND with `direction=INBOUND`. B's list shows one transaction — the SEND with `direction=INBOUND`.

### Single-tx endpoint
- [ ] Existing transaction that involves the wallet → 200 with the same shape as a list entry. `direction` reflects the wallet's role in this tx.
- [ ] Existing transaction that does NOT involve the wallet → 404 with `error: TRANSACTION_NOT_FOUND`.
- [ ] Unknown `txId` for a known wallet → 404 with `error: TRANSACTION_NOT_FOUND` (same as the "not involved" case).
- [ ] Unknown wallet → 404 with `error: WALLET_NOT_FOUND` (wallet existence is checked first).
- [ ] Malformed UUID on either path variable → 400.

### Cross-cutting
- [ ] No Temporal workflow execution is created on either endpoint.
- [ ] `./gradlew build` is clean.

## Test plan

- **`LedgerCoreServiceTest`** — extend with:
  - `getTransactionsForWallet_returnsBothInboundAndOutbound`.
  - `getTransactionsForWallet_sortedByCreatedAtDesc`.
  - `getTransactionsForWallet_capsAt100` (insert 105 raw `Transaction` rows via the repository; assert exactly 100 returned, all newest).
  - `getTransactionsForWallet_emptyHistory_returnsEmptyList`.
  - `getTransactionForWallet_existingAndInvolvesWallet_returnsView`.
  - `getTransactionForWallet_existingButOtherWallets_throwsTransactionNotFound`.
  - `getTransactionForWallet_unknownTxId_throwsTransactionNotFound`.

- **`WalletControllerTest`** — extend with:
  - `getTransactions_existing_returns200WithList` (mixed directions).
  - `getTransactions_emptyHistory_returns200WithEmptyList`.
  - `getTransactions_unknownWallet_returns404WithWalletNotFound`.
  - `getTransactions_malformedUuid_returns400`.
  - `getTransaction_existingInvolvesWallet_returns200`.
  - `getTransaction_existingDoesNotInvolveWallet_returns404WithTransactionNotFound`.
  - `getTransaction_unknownTxId_returns404WithTransactionNotFound`.
  - `getTransaction_unknownWallet_returns404WithWalletNotFound`.
  - `getTransaction_malformedTxId_returns400`.

- **Manual smoke** via `docker compose up -d --build` (assumes spec/007 fund + send already exercised):
  ```bash
  curl -s ":8081/api/v1/wallets/$W1/transactions" | jq
  curl -s ":8081/api/v1/wallets/$W2/transactions" | jq
  curl -s ":8081/api/v1/wallets/$W1/transactions/$TX_ID" | jq
  curl -i ":8081/api/v1/wallets/$W1/transactions/$(uuidgen)"     # 404 TRANSACTION_NOT_FOUND
  curl -i ":8081/api/v1/wallets/$(uuidgen)/transactions"         # 404 WALLET_NOT_FOUND
  ```

## Open questions

None at approval. Decisions logged for traceability:

- **URL shape:** wallet-scoped for both endpoints — `/api/v1/wallets/{walletId}/transactions[/{txId}]`.
- **Pagination:** hard cap at 100 most-recent, no `?limit` param. Cursor pagination is a future spec.
- **Wallet-relative annotations:** every entry includes `direction` (`OUTBOUND`/`INBOUND`) AND wallet-side ledger fields (`entryType`, `runningAvailableAfter`, `runningPendingAfter`, `entrySequence`). The counterparty's ledger entry is not exposed.
- **Statuses shown:** all (`PENDING` / `COMPLETED` / `FAILED` / `REVERSED`). No filtering.
- **Sort order:** `createdAt DESC`.
- **Single-tx 404:** "tx exists but doesn't involve this wallet" collapses into the same `TRANSACTION_NOT_FOUND` as "tx doesn't exist at all". Privacy property: you can't probe other wallets' tx IDs.
- **DTOs:** new `WalletTransactionResponse` (single) + `WalletTransactionsResponse` (list wrapper). The existing `TransactionResponse` (spec 007) is left alone — `direction` and wallet-side ledger fields are wallet-scoped concerns.
- **Internal fields hidden:** `idempotencyKey` and `requestHash` are not in the response.
