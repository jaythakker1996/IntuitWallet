# 006 — QR domain (POC stub)

- **Status:** Accepted
- **Date:** 2026-05-06
- **Author:** IntuitWalletService

## Context

QR-initiated payments are part of the broader product vision: a payer scans a payee's QR, the wallet UI pre-fills the recipient, and the payer enters amount + stablecoin to send. Spec 007 already reserved a `QR_PAY` value in `transactions.type`, and the `TransactPage` UI from spec 010 has a disabled "Scan QR (coming soon)" placeholder. Neither side has a backing model.

This ADR introduces the `qr_codes` domain entity and pins the POC behaviour: **one QR per wallet, lasts forever, payload is a static reference to the wallet**. Generation is the only operation in scope. Scanning, payment-via-QR, and dynamic / time-bound / single-use QR semantics are explicitly deferred.

The QR domain is **POC-scoped**, same caveat as the User domain (ADR 002): the schema choices are POC choices and get revisited before any production-bound feature ships, especially around expiry, single-use semantics, payment amount encoding, and the mapping between scanned payload and the eventual `transactions.from_party` / `to_party` / `compliance_check_id` flow.

## Decision

### 1. QR mutations skip Temporal (POC carve-out)

QR creation is a single-row insert with no money movement. Per the ADR 001 amendment that allows the `controller → core → repository → DB` path for non-orchestrated flows, QR CRUD takes the same POC carve-out as User CRUD (ADR 002) — controller calls `QrCoreService` directly, no workflow, no activity.

Justification (mirrors ADR 002):
- Generation is "find existing or insert new" — exactly what the existing idempotent-create pattern handles (`UserCoreService.createUser`, `WalletCoreService.createIfMissing`).
- No retries beyond the standard `DataIntegrityViolationException` race-resolution that the core service already does.
- Workflow-orchestration is reserved for state changes that affect `transactions` or the `ledger` (per CLAUDE.md). QR creation touches neither.

The eventual `QR_PAY` transaction flow — when a scanned QR is converted into a payment — **does** go through Temporal, because that path writes `transactions` + `ledger_entries`. Per ADR 003 §1 / ADR 004 §1: state changes affecting transactions are non-negotiably workflow-orchestrated. That flow is out of scope for ADR 006; it lands in a future spec that re-uses the existing `SendPaymentWorkflow` (or a sibling) with `transactions.type = 'QR_PAY'`.

### 2. `qr_codes` table schema

Flyway migration `V4__qr_codes.sql` (created by spec 011, not by this ADR):

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

Field rules (style mirrors ADR 003 §2):

- `qr_code_id` — UUID PK, generated app-side in `QrCoreService`. Random UUIDv4; no time-ordering requirement (QRs aren't on a hot read path).
- `wallet_id` — `UNIQUE NOT NULL`. Enforces **one QR per wallet** for the POC. **No database foreign key** to `wallets`, per ADR 004 §2 ("no FKs anywhere") — wallets and QR codes will live in the same Ledger Core DB in production today, but the schema must remain portable to the cross-region active-active deployment that ADR 004 establishes. Existence is verified in code (`QrCoreService` calls `WalletCoreService.getById` before inserting).
- `payload` — TEXT, the literal string encoded into the QR image at render time. Generated server-side as `intuit-wallet://pay?walletId={wallet_id}` (custom URI scheme). The exact format is decided here so all consumers — the frontend renderer, the future scanner, the audit/replay path — agree. Choice rationale below in §3.
- `type` — `STATIC` or `DYNAMIC`. Only `STATIC` is created in the POC; `DYNAMIC` is reserved for future amount-encoded or merchant-tied QRs. The enum is here from day one so introducing dynamic QRs doesn't require a `CHECK` migration.
- `status` — `ACTIVE` on creation; `REVOKED` reserved for the future revocation flow (not in scope for spec 011). The DB constraint is just the enum.
- `expires_at` — nullable. **`NULL` means "never expires"**, which is the POC behaviour for every QR generated. The column exists from day one so future time-bound QRs don't require a migration; the application layer interprets `NULL` correctly.
- `created_at` / `updated_at` — DB defaults; `updated_at` bumped on any mutation, same pattern as `users` and `wallets`.

### 3. Payload format: `wallet:{wallet_id}`

The QR-encoded string is a minimal URI-style identifier — a `wallet:` scheme prefix followed by the wallet UUID. Examples:

```
wallet:11111111-2222-3333-4444-555555555555
```

Why this shape:
- **Scheme prefix gives semantic intent without ceremony.** A bare UUID is ambiguous (could be a user_id, tx_id, anything); a single-word prefix makes the meaning explicit and lets a future scanner validate before treating it as a wallet identifier.
- **No path, no query string, no `://` separator.** That's what "keep it simple" means here. The full URI shape (`scheme://host/path?query`) is overkill — there's no host, no resource hierarchy, no parameters. A two-line parser handles every case.
- **Easy to extend.** Adding parameters later (e.g., dynamic QR with amount) becomes `wallet:{walletId}?amount=10&stablecoin=USDC`. Backwards-compatible: the prefix and walletId-extraction logic stays identical.

Parsing on the consumer side is two lines:

```ts
if (!payload.startsWith("wallet:")) throw new Error("Not a wallet QR");
const walletId = payload.slice("wallet:".length);
// validate walletId is a UUID, look up the wallet, etc.
```

This payload format is **stable contract**: clients (the frontend QR renderer, the future scanner) parse it directly. Changing it is a versioning event.

The rendered QR image (the actual visual matrix of squares) is **generated client-side** from the payload string by a JS library. The backend never returns image bytes; it just returns the payload string. This keeps the backend simple, makes the payload auditable, and lets the frontend choose ECC level / size / color without backend changes.

### 4. What's deliberately NOT in this ADR

- **Scanning / payment-via-QR.** That's a `QR_PAY` transaction flow (ADR 004 §3 enum has it). Future spec; uses Temporal because it writes `transactions` + `ledger_entries`.
- **Dynamic QRs with embedded amount / stablecoin.** Lives in a future ADR or spec when there's a real merchant use case to validate the shape against.
- **Single-use vs multi-use semantics.** All POC QRs are multi-use (scan as many times as you like; each scan creates a separate transaction). Single-use needs a `consumed_at` column or a separate `qr_redemptions` table — out of scope.
- **Time-bound expiry.** `expires_at` exists but is always `NULL` for POC. The expiration-enforcement logic (reject scans of expired QRs) lands with the scanning flow.
- **Revocation.** `status='REVOKED'` is reserved in the enum; no API path to revoke yet.
- **QR rotation / regeneration.** A user gets one QR forever in the POC. Rotation needs either a versioned table or a "soft revoke + new row" flow — out of scope.
- **Compliance integration.** No `compliance_check_id` referenced from QR rows; compliance kicks in at scan time on the `QR_PAY` transaction, not at generation time.
- **Merchant-tied QRs.** A merchant might want a fixed-amount QR for "scan to pay $10". That's a dynamic QR (`type='DYNAMIC'`) with a different payload format — future ADR.
- **QR rendering on the backend.** Backend stores the payload string; frontend renders the image. Backend never returns base64 PNGs.

## Consequences

- **Positive**
  - QR creation is a one-row idempotent insert with the same race-resolution pattern as wallet creation. Reuses existing infrastructure (controller → core, no new exception types beyond the existing `WalletNotFoundException`).
  - Payload format is fixed early so the future scanner / parser doesn't have to be coordinated with the generator across PRs.
  - `type`, `status`, `expires_at` columns exist from day one; future expiry / dynamic / revocation specs add code, not migrations.
  - One-QR-per-wallet via `UNIQUE` makes "show me my QR" a single index lookup (no need for "latest active" filtering).
  - No FK to `wallets` keeps the schema cross-region-portable without a future migration to drop constraints.

- **Negative**
  - The "lasts forever" decision means any QR ever printed on a poster / shared in a chat / engraved on a card stays valid until manual revocation (which doesn't exist in POC). Acceptable for POC; production likely needs at minimum a revocation API and probably default expiry.
  - Hardcoded `intuit-wallet://` URI scheme commits us to that scheme as the long-term identity; if the product later picks a different scheme, every printed QR has to be re-issued. Mitigated by parsing being centralized — only the future scanner code cares about the literal scheme.
  - One QR per wallet means a user can't have separate QRs for different contexts (personal vs business, multi-currency-specific, time-bound for a single event). Acceptable for POC; lifted by relaxing the `UNIQUE(wallet_id)` constraint and adding a `label` column.
  - `payload` is denormalized — it carries the wallet_id which is also a column on the same row. A bug that desyncs the two is possible but practically prevented by the payload being generated server-side once at insert time and never updated.

- **Follow-ups**
  - First QR feature spec (spec 011, this PR's sibling): adds `V4__qr_codes.sql`, the `QrCode` entity / repository, `QrCoreService`, DTOs, and the `POST /api/v1/wallets/{walletId}/qr` endpoint.
  - Frontend spec to render the QR on a wallet detail page (likely follow-up `012-frontend-qr-display.md`).
  - Scanning + `QR_PAY` transaction flow — separate ADR/spec when the scanner-side UX exists.
  - Dynamic QR ADR for amount-encoded merchant payments.
  - Revocation API + UI when there's a real reason to revoke.
  - Default expiry policy when production rollout requires it.

## Alternatives considered

- **Encode the payload as plain `walletId` UUID with no scheme prefix.** Rejected: every scanner has to hardcode the assumption that "this UUID = wallet". Custom URI scheme makes intent explicit and gives the future native app a natural deep-link entry.
- **Encode as an HTTPS URL (e.g. `https://wallet.intuit.com/pay/{walletId}`).** Rejected for POC: implies a hosted page that needs to exist, and forces the scanner-side to either fetch the URL or hardcode parsing of an HTTPS URL. Custom URI scheme is closer to a real-world payment QR (Venmo, Cash App use similar). Revisit if Intuit picks a hosted-link strategy for QR adoption.
- **Encode as a JSON blob (`{"v":1,"walletId":"..."}`).** Rejected: more bytes for the same information; QR pixel density goes up; harder to scan reliably on a low-end phone. URI scheme is denser and more conventional.
- **Generate the QR image (PNG/SVG) on the backend and return base64.** Rejected: backend now has to import a QR rendering library, decisions about size / ECC level / margin / color all become backend API design questions, and the payload is not directly auditable from the response. Frontend rendering is simpler and more flexible.
- **Allow many QRs per wallet (drop the `UNIQUE(wallet_id)` constraint), with a `label` column.** Rejected for POC: forces every endpoint that fetches "my QR" to have a "which one?" dimension. Single-QR-per-wallet maps to the spec's UX ("here's my QR") cleanly. Trivially relaxed in a future spec.
- **Tie the QR to `intuit_account_id` (user) instead of `wallet_id`.** Rejected: a user with no wallet has no money-receiving capability, so a user-scoped QR is meaningless until a wallet exists. Wallet-scoped also matches the API path conventions established in spec 006 (`/wallets/{walletId}/...`).
- **Use a Temporal workflow for QR creation anyway, for uniformity.** Rejected: same reasoning as ADR 002 §1 — pure overhead with no orchestration value. Temporal is reserved for state changes that affect `transactions` / `ledger`.
- **Skip the `qr_codes` table entirely; derive payload deterministically from `walletId` (e.g. `intuit-wallet://pay?walletId={wallet_id}`).** Tempting because the QR can be regenerated any time without storage. Rejected because (a) future expiry / revocation / dynamic QRs need a row anyway, (b) auditing / debugging benefits from knowing exactly which QR was issued and when, (c) one-QR-per-wallet enforcement requires the row to exist.
