# 011 — Create + display QR for a wallet

- **Status:** Approved
- **Author:** IntuitWalletService
- **Last updated:** 2026-05-06
- **Implements:** TBD (filled with PR/commit)
- **Related ADRs:** [003-wallet-domain](../adrs/003-wallet-domain.md), [004-transactions-ledger](../adrs/004-transactions-ledger.md), [006-qr-domain](../adrs/006-qr-domain.md)

## Problem

ADR 006 introduces the `qr_codes` domain entity. The DB has no `qr_codes` table and no API path that creates one. The frontend's `TransactPage` has a disabled "Scan QR" placeholder, and there's nowhere to display a wallet's own QR. Users can't share a QR with anyone yet.

This spec lands the **first QR-related code path**: a single `POST /api/v1/wallets/{walletId}/qr` endpoint that creates a QR for a wallet (idempotent: one QR per wallet) and returns the row including the encoded `payload` string. Frontend rendering of the QR image and the scanner side are separate specs.

## Goals / Non-goals

- **Goals**
  - Flyway migration `V4__qr_codes.sql` with the table from ADR 006 §2.
  - `Qr` JPA entity and `QrRepository` with `findByWalletId(UUID)`.
  - `QrCoreService.createIfMissing(walletId)` + `getByWalletId(walletId)`. Idempotent insert with race resolution against the `UNIQUE(wallet_id)` constraint, mirroring `WalletCoreService.createIfMissing`.
  - `POST /api/v1/wallets/{walletId}/qr` endpoint — idempotent. 201 on first call, 200 on retry.
  - `GET /api/v1/wallets/{walletId}/qr` endpoint — 200 if exists, 404 (`QR_NOT_FOUND`) if no QR for this wallet.
  - Wallet existence + ACTIVE status enforced. 404 if wallet doesn't exist (`WALLET_NOT_FOUND`); 409 if wallet is `FROZEN` or `CLOSED` (`WALLET_NOT_ACTIVE`).
  - Payload generated server-side as `wallet:{walletId}` (per ADR 006 §3).
  - **Frontend integration on `WalletPage`**: lazy-fetch the wallet's QR via `GET`. If 200, render the QR image inline (using `react-qr-code`). If 404 with `QR_NOT_FOUND`, show a "Generate QR" button that calls `POST` and re-renders with the new QR.
- **Non-goals**
  - **No scanning, no `QR_PAY` flow.** That writes `transactions` + `ledger_entries` and goes through Temporal; out of scope.
  - **No dynamic / amount-encoded QRs.** All POC QRs are `STATIC`.
  - **No expiry, no revocation.** `expires_at` stays `NULL` for every row; `status` stays `ACTIVE`. Both columns exist (per ADR 006) but no API path manipulates them.
  - **No multi-QR-per-wallet.** `UNIQUE(wallet_id)` enforces one.
  - **No backend QR image rendering.** Backend returns the payload string; frontend renders the image via `react-qr-code` (SVG output).
  - **No download / share / copy actions on the frontend.** Just render the QR. Future polish if needed.
  - **No dedicated QR page route.** The QR section is inline on `/wallet`, not at `/wallet/qr`.

## API

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/wallets/{walletId}/qr` | Create the QR for a wallet. Idempotent on `walletId`. |
| GET  | `/api/v1/wallets/{walletId}/qr` | Fetch the QR for a wallet. |

### POST request

No body. The `walletId` path parameter is the only input.

### GET request

No body, no query string.

### Response — `QrResponse` (shared by both endpoints)

```json
{
  "qrCodeId":  "01952af0-1234-7890-abcd-ef0123456789",
  "walletId":  "11111111-2222-3333-4444-555555555555",
  "payload":   "wallet:11111111-2222-3333-4444-555555555555",
  "type":      "STATIC",
  "status":    "ACTIVE",
  "expiresAt": null,
  "createdAt": "2026-05-06T12:00:00Z",
  "updatedAt": "2026-05-06T12:00:00Z"
}
```

### POST status codes

| Status | When |
| --- | --- |
| 201 | QR was created on this call. |
| 200 | A QR for this wallet already existed; existing row is returned. |
| 400 | `walletId` not a valid UUID. |
| 404 | No wallet with that `walletId` (`error: WALLET_NOT_FOUND`). |
| 409 | Wallet exists but `status` is not `ACTIVE` (`error: WALLET_NOT_ACTIVE`). |

### GET status codes

| Status | When |
| --- | --- |
| 200 | QR found; existing row is returned. |
| 400 | `walletId` not a valid UUID. |
| 404 | Wallet doesn't exist (`error: WALLET_NOT_FOUND`), OR wallet exists but has no QR (`error: QR_NOT_FOUND`). The `error` field distinguishes. |

## Data model

New Flyway migration `backend/src/main/resources/db/migration/V4__qr_codes.sql`. Schema is exactly the one in ADR 006 §2:

```sql
CREATE TABLE qr_codes (
    qr_code_id   UUID         PRIMARY KEY,
    wallet_id    UUID         NOT NULL UNIQUE,
    payload      TEXT         NOT NULL,
    type         VARCHAR(16)  NOT NULL DEFAULT 'STATIC'
                 CHECK (type IN ('STATIC', 'DYNAMIC')),
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                 CHECK (status IN ('ACTIVE', 'REVOKED')),
    expires_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

No FK to `wallets` per ADR 004 §2. Logical reference; existence is checked in `QrCoreService` before the insert.

## Workflow

N/A — POC carve-out per ADR 006 §1. Direct controller → core → repository.

## Files touched

### New (backend)

- `backend/src/main/resources/db/migration/V4__qr_codes.sql` — the migration above.
- `backend/src/main/java/com/intuit/walletservice/dal/entity/Qr.java` — JPA entity.
- `backend/src/main/java/com/intuit/walletservice/dal/repository/QrRepository.java` — `JpaRepository<Qr, UUID>` with `findByWalletId(UUID)`.
- `backend/src/main/java/com/intuit/walletservice/businesslogic/core/QrCoreService.java` — `createIfMissing(UUID walletId)` returning `CreateQrResult(QrView qr, boolean created)`, plus `getByWalletId(UUID walletId)` returning `QrView` (throws `QrNotFoundException` if absent).
- `backend/src/main/java/com/intuit/walletservice/businesslogic/core/QrView.java` — response shape record.
- `backend/src/main/java/com/intuit/walletservice/businesslogic/core/QrNotFoundException.java` — thrown by `getByWalletId` when no QR exists for the wallet. Mapped to 404 with `error: QR_NOT_FOUND` by `ApiExceptionHandler`.
- `backend/src/main/java/com/intuit/walletservice/service/dto/QrResponse.java` — `from(QrView)` factory.
- `backend/src/test/java/com/intuit/walletservice/businesslogic/core/QrCoreServiceTest.java` — `@DataJpaTest` with happy / idempotent / wallet-not-found / wallet-not-active / race-resolution / get-existing / get-missing.

### Modified (backend)

- `backend/src/main/java/com/intuit/walletservice/service/controller/WalletController.java` — `@PostMapping("/{walletId}/qr")` and `@GetMapping("/{walletId}/qr")`. Both check wallet existence via `walletCoreService.getById(walletId)` first (throws `WalletNotFoundException` → 404 `WALLET_NOT_FOUND`). POST passes through to `createIfMissing`. GET passes through to `getByWalletId` (throws `QrNotFoundException` → 404 `QR_NOT_FOUND`).
- `backend/src/main/java/com/intuit/walletservice/service/controller/ApiExceptionHandler.java` — handler entry for `QrNotFoundException` → 404 with `error: QR_NOT_FOUND`.
- `backend/src/test/java/com/intuit/walletservice/service/controller/WalletControllerTest.java` — additions for both endpoints across all status codes.

### New (frontend)

- `frontend/src/api/qr.ts` — `getQr(walletId)` and `createQr(walletId)`.
- `frontend/src/components/WalletQrSection.tsx` — renders one of three states: loading, "Generate QR" button, or the QR image (`react-qr-code` rendering the payload string as SVG).
- `frontend/src/types/api.ts` — adds `QrResponse` interface.

### Modified (frontend)

- `frontend/package.json` — adds `react-qr-code` (~3 KB minified, zero deps).
- `frontend/src/pages/WalletPage.tsx` — embeds `<WalletQrSection walletId={...} />` below the balances list.
- `frontend/src/index.css` — `.qr-section` styles (centered SVG, label, container).

## Implementation outline

### Payload generation

```java
private static String buildPayload(UUID walletId) {
    return "wallet:" + walletId;
}
```

### `QrCoreService.createIfMissing` and `getByWalletId`

`createIfMissing` — same race-resolution shape as `UserCoreService.createUser` and `WalletCoreService.createIfMissing`. `getByWalletId` is a single-row read that throws `QrNotFoundException` on miss.

### Controller

```java
@PostMapping("/{walletId}/qr")
public ResponseEntity<QrResponse> createQr(@PathVariable UUID walletId) {
    CreateQrResult result = qrCoreService.createIfMissing(walletId);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(QrResponse.from(result.qr()));
}

@GetMapping("/{walletId}/qr")
public QrResponse getQr(@PathVariable UUID walletId) {
    walletCoreService.getById(walletId);  // 404 WALLET_NOT_FOUND if missing
    return QrResponse.from(qrCoreService.getByWalletId(walletId));  // 404 QR_NOT_FOUND if missing
}
```

### Frontend `WalletQrSection`

```tsx
// On mount: GET /qr.
//   200 -> render <QRCode value={payload} />
//   404 QR_NOT_FOUND -> show "Generate QR" button
//   404 WALLET_NOT_FOUND -> show error
//   other -> show error
// On button click: POST /qr -> 200 or 201 -> set state to the new QrResponse.
```

## Acceptance criteria

### POST
- [ ] `POST /api/v1/wallets/{walletId}/qr` against an existing ACTIVE wallet returns 201 with a `QrResponse` whose `payload` equals `wallet:<walletId>`, `type=STATIC`, `status=ACTIVE`, `expiresAt=null`.
- [ ] A second POST with the same `walletId` returns 200 and the same `qrCodeId` as the first call. No new row in `qr_codes`.
- [ ] POST against an unknown `walletId` returns 404 `WALLET_NOT_FOUND`.
- [ ] POST against a `FROZEN` or `CLOSED` wallet returns 409 `WALLET_NOT_ACTIVE`.
- [ ] POST with a malformed UUID path variable returns 400.
- [ ] `UNIQUE(wallet_id)` catches a concurrent double-insert; second caller observes the winning row (200, `created=false`).

### GET
- [ ] `GET` against a wallet that has a QR returns 200 with the same `QrResponse` shape as POST.
- [ ] `GET` against an existing wallet with no QR yet returns 404 `QR_NOT_FOUND`.
- [ ] `GET` against an unknown wallet returns 404 `WALLET_NOT_FOUND`.
- [ ] Malformed UUID returns 400.

### Frontend
- [ ] Visiting `/wallet` after creating a wallet shows a "Generate QR" button beneath the balances list.
- [ ] Clicking "Generate QR" calls POST and renders the QR image inline (SVG via `react-qr-code`) without a page reload. The payload below the image reads `wallet:<walletId>`.
- [ ] On subsequent visits the QR section loads the existing QR via GET and renders it directly (no button).
- [ ] The QR survives logout / login (it's wallet-scoped, not session-scoped).

### Cross-cutting
- [ ] No Temporal workflow execution is triggered (Temporal UI shows no new runs).
- [ ] `cd backend && ./gradlew build` is clean.
- [ ] `cd frontend && npm install && npm run build` is clean.

## Test plan

- **`QrCoreServiceTest`** (`@DataJpaTest`):
  - `createIfMissing_newWallet_persistsRowAndReturnsCreatedTrue`.
  - `createIfMissing_existingQr_returnsExistingAndCreatedFalse`.
  - `createIfMissing_payloadIsExpectedFormat` (asserts `wallet:<walletId>`).
  - `createIfMissing_unknownWallet_throwsWalletNotFound`.
  - `createIfMissing_frozenWallet_throwsWalletNotActive`.
  - `createIfMissing_concurrentInsertResolvedToWinner`.
  - `getByWalletId_existing_returnsView`.
  - `getByWalletId_noQr_throwsQrNotFound`.

- **`WalletControllerTest`** — extend with:
  - `createQr_validWallet_returns201WithExpectedPayload`.
  - `createQr_idempotentRetry_returns200WithSameQrCodeId`.
  - `createQr_unknownWallet_returns404WithWalletNotFound`.
  - `createQr_frozenWallet_returns409`.
  - `createQr_malformedWalletId_returns400`.
  - `getQr_existing_returns200`.
  - `getQr_walletButNoQr_returns404WithQrNotFound`.
  - `getQr_unknownWallet_returns404WithWalletNotFound`.
  - `getQr_malformedWalletId_returns400`.

- **Manual smoke** via `cd docker && docker compose up -d --build`:
  ```bash
  W1=$(curl -s -X POST :8081/api/v1/wallets -H 'Content-Type: application/json' \
       -d "{\"intuitAccountId\":\"$U1\"}" | jq -r .walletId)

  curl -i ":8081/api/v1/wallets/$W1/qr"               # 404 QR_NOT_FOUND
  curl -i -X POST ":8081/api/v1/wallets/$W1/qr"       # 201
  curl -i ":8081/api/v1/wallets/$W1/qr"               # 200 (same row)
  curl -i -X POST ":8081/api/v1/wallets/$W1/qr"       # 200 (idempotent)
  curl -i -X POST ":8081/api/v1/wallets/$(uuidgen)/qr" # 404 WALLET_NOT_FOUND
  ```

- **Browser smoke**: open `http://localhost:5173`, sign in, visit `/wallet`. "Generate QR" button visible. Click → QR image appears. Reload the page → QR still there (loaded via GET).

## Open questions

None at approval. Decisions logged for traceability:

- **URL shape:** wallet-scoped — `POST /api/v1/wallets/{walletId}/qr` and `GET /api/v1/wallets/{walletId}/qr`. Matches the `/wallets/{walletId}/fund`, `/wallets/{walletId}/balances` pattern.
- **GET endpoint included.** 200 if QR exists, 404 `QR_NOT_FOUND` (or `WALLET_NOT_FOUND`) otherwise.
- **Payload format:** `wallet:{walletId}` (ADR 006 §3) — minimal scheme prefix, no path/query.
- **Backend returns payload string only.** Image rendering is client-side via `react-qr-code` (SVG output, ~3 KB, zero deps).
- **Idempotency:** `UNIQUE(wallet_id)` is the only dedup key. No per-call `idempotencyKey`.
- **Scope:** create + get + frontend display. Out of scope: revocation, dynamic/amount-encoded QRs, scanning, QR_PAY transactions.
