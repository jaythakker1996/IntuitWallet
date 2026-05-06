# 006 — Create and get wallet

- **Status:** Approved
- **Author:** IntuitWalletService
- **Last updated:** 2026-05-06
- **Implements:** TBD (link added when PR opens)
- **Related ADRs:** [003-wallet-domain](../adrs/003-wallet-domain.md), [004-transactions-ledger](../adrs/004-transactions-ledger.md)

## Problem

The `wallets` table exists in the DB (per spec 005 / V2 migration) but nothing reads or writes it yet. A user with an `intuit_account_id` in the `users` table has no way to provision a wallet, and no way to look one up afterward.

This spec lands the first wallet endpoints — `POST /api/v1/wallets` (create) and `GET /api/v1/wallets/...` (read) — and, as a side effect, lands the **first real Temporal workflow + activities** in the codebase. The repo has had Temporal in dependencies and `application.yml` from day one, but no `@WorkflowImpl` / `@ActivityImpl` until now. Per ADR 003 §1, wallet **mutations** go through Temporal; reads stay direct (`controller → core → repository → DB`).

Auth (JWT) is intentionally out of scope. The POST takes `intuitAccountId` directly in the request body; in production this value is extracted from the JWT subject claim by an auth filter. A future ADR + spec will wire up real auth.

## Goals / Non-goals

- **Goals**
  - `POST /api/v1/wallets` creates a `USER`-type wallet for an existing user, idempotent on `intuitAccountId`.
  - `GET /api/v1/wallets/{walletId}` returns a wallet by id.
  - `GET /api/v1/wallets?intuitAccountId={id}` returns the single wallet associated with a user (1:1 cardinality per ADR 003).
  - `POST` is orchestrated by a Temporal workflow with two sequential activities: validate the user exists, then create-if-missing.
  - GETs are direct, not workflow-orchestrated.
- **Non-goals**
  - JWT / OAuth2 / any auth. Caller is trusted; `intuitAccountId` comes from the request payload.
  - Creating `SYSTEM` wallets via the API. Those are seeded by V2 (ADR 004 §8) and aren't user-creatable.
  - Wallet status mutations (`FREEZE`, `CLOSE`). Future spec.
  - Wallet update (`PATCH`). No mutable fields exist on the wallet row beyond status.
  - Listing all wallets. There's no admin endpoint in this spec.

## API

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/wallets` | Provision a `USER` wallet for the given `intuitAccountId`. Idempotent. |
| GET  | `/api/v1/wallets/{walletId}` | Fetch a wallet by `wallet_id`. |
| GET  | `/api/v1/wallets?intuitAccountId={id}` | Fetch the wallet for a given user. |

### POST `/api/v1/wallets`

**Request:**
```json
{ "intuitAccountId": "550e8400-e29b-41d4-a716-446655440000" }
```

Validation:
- `intuitAccountId`: required, valid UUID.

**Response (201 Created on first call, 200 OK on duplicate):**
```json
{
  "walletId": "11111111-2222-3333-4444-555555555555",
  "intuitAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "USER",
  "status": "ACTIVE",
  "createdAt": "2026-05-06T07:00:00Z",
  "updatedAt": "2026-05-06T07:00:00Z"
}
```

A retry with the same `intuitAccountId` returns 200 OK with the existing wallet (same shape as `POST /api/v1/users` — see spec 002).

| Status | When |
| --- | --- |
| 201 | Wallet was created on this call. |
| 200 | A wallet for the given `intuitAccountId` already existed; existing row is returned. |
| 400 | Body missing or `intuitAccountId` not a UUID. |
| 404 | No user with that `intuitAccountId` exists in the `users` table. |

### GET `/api/v1/wallets/{walletId}`

Response: same `WalletResponse` shape as POST.

| Status | When |
| --- | --- |
| 200 | Wallet found. |
| 400 | `walletId` not a valid UUID. |
| 404 | No wallet with that `walletId`. |

### GET `/api/v1/wallets?intuitAccountId={id}`

Response: same shape.

| Status | When |
| --- | --- |
| 200 | A wallet exists for that user; row returned. |
| 400 | `intuitAccountId` query param missing or not a UUID. |
| 404 | No wallet exists for that user yet. |

## Data model

No schema change. The `wallets` table is already in place from spec 005 / V2.

JPA entity (`com.intuit.walletservice.dal.entity.Wallet`) maps the existing columns. No FK declarations on the entity (matches the ADR 004 §2 "no FKs anywhere" rule). Repository is `JpaRepository<Wallet, UUID>` with one custom finder: `findByIntuitAccountId(UUID)`.

## Workflow

- **Workflow name:** `CreateWalletWorkflow` (`@WorkflowInterface`).
- **Implementation:** `CreateWalletWorkflowImpl` annotated `@WorkflowImpl(workers = "wallet-service-worker")`.
- **Task queue:** `wallet-service` (already configured in `application.yml`).
- **Method signature:** `CreateWalletResult createWallet(UUID intuitAccountId)`.
- **Workflow ID:** `create-wallet-{intuitAccountId}-{random UUID}`. Random suffix means each POST is its own workflow run; no `WorkflowExecutionAlreadyStarted` to handle in the controller. **Idempotency comes from the DB unique constraint inside the create activity, not from the workflow id.** Acceptable trade-off for the rudimentary POC: two concurrent POSTs both run workflows, both call the create activity, but the second sees the existing row via the `UNIQUE(intuit_account_id)` race-resolution path (same pattern `UserCoreService.createUser` already uses).
- **Activities (sequential):**
  - `ValidateUserActivity.validate(UUID intuitAccountId)` — calls `UserCoreService.getUser(...)`. Throws `UserNotFoundException` if the user is missing. `UserNotFoundException` is registered as **non-retryable** in the activity options so a missing user fails fast instead of retrying the lookup. Start-to-close timeout: 5s. Default retry policy on transient failures.
  - `CreateWalletActivity.createIfMissing(UUID intuitAccountId)` — calls `WalletCoreService.createIfMissing(...)`, returns `CreateWalletResult(WalletView wallet, boolean created)`. Idempotent at the DB level. Start-to-close timeout: 5s. Retries on transient DB failures only.
- **Signals / queries:** none.
- **Idempotency:** DB unique constraint on `wallets.intuit_account_id` is the source of truth. The activity's `createIfMissing` does a read-then-insert, catches `DataIntegrityViolationException` on race, and resolves by reading the winning row. Same pattern as `UserCoreService.createUser`.

## Acceptance criteria

- [ ] `POST /api/v1/wallets` with a valid existing `intuitAccountId` returns 201 and a `WalletResponse` with `type=USER`, `status=ACTIVE`, fresh `walletId`.
- [ ] A second `POST` with the same `intuitAccountId` returns 200 and the same `walletId` as the first call.
- [ ] `POST` with an `intuitAccountId` that doesn't exist in `users` returns 404.
- [ ] `POST` with a malformed UUID or missing field returns 400.
- [ ] `GET /api/v1/wallets/{walletId}` returns 200 with the row, or 404 if missing, or 400 on malformed UUID.
- [ ] `GET /api/v1/wallets?intuitAccountId={id}` returns 200 with the row, or 404 if no wallet exists for that user, or 400 on malformed UUID / missing param.
- [ ] A successful `POST` produces exactly one Temporal workflow execution (visible in the Temporal UI at `http://localhost:8233`).
- [ ] No GET request produces a Temporal workflow execution.
- [ ] `./gradlew build` is clean.

## Test plan

- **`WalletCoreServiceTest`** (`@DataJpaTest`, no Temporal):
  - `createIfMissing_newUser_persistsAndReturnsCreatedTrue`.
  - `createIfMissing_existingWallet_returnsExistingAndCreatedFalse`.
  - `createIfMissing_concurrentInsertRace_returnsExisting` (simulated via direct second insert; mirrors the existing `UserCoreServiceTest` race test).
  - `getById_existingId_returnsView`.
  - `getById_unknownId_throwsWalletNotFoundException`.
  - `getByIntuitAccountId_existing_returnsView`.
  - `getByIntuitAccountId_unknown_throwsWalletNotFoundException`.

- **`CreateWalletWorkflowTest`** (`TestWorkflowEnvironment`):
  - `createWallet_validUser_runsValidateThenCreate_returnsResult` — both activities mocked, asserts call order and return.
  - `createWallet_unknownUser_propagatesUserNotFound` — mock `ValidateUserActivity` to throw `UserNotFoundException`; assert workflow fails with the expected `ApplicationFailure` type and `CreateWalletActivity` is never called.

- **`WalletControllerTest`** (`@WebMvcTest`, mocked `WorkflowClient` + `WalletCoreService`):
  - `post_validBodyNewUser_returns201`.
  - `post_validBodyExisting_returns200`.
  - `post_unknownUser_returns404` — mock the workflow stub to throw `WorkflowFailedException` whose cause is `UserNotFoundException`-equivalent; controller maps to 404.
  - `post_blankBody_returns400`.
  - `post_malformedUuid_returns400`.
  - `getById_existing_returns200`.
  - `getById_unknown_returns404`.
  - `getById_malformedUuid_returns400`.
  - `getByIntuitAccountId_existing_returns200`.
  - `getByIntuitAccountId_unknown_returns404`.
  - `getByIntuitAccountId_missingParam_returns400`.

- **Manual smoke** via `docker compose up -d --build`:
  ```bash
  ID=$(curl -s -X POST http://localhost:8081/api/v1/users \
       -H 'Content-Type: application/json' \
       -d '{"email":"alice@example.com","role":"CONSUMER","homeRegion":"us-east-1"}' \
       | jq -r .intuitAccountId)
  # First POST: 201
  curl -i -X POST http://localhost:8081/api/v1/wallets \
       -H 'Content-Type: application/json' \
       -d "{\"intuitAccountId\":\"$ID\"}"
  # Second POST same id: 200
  curl -i -X POST http://localhost:8081/api/v1/wallets \
       -H 'Content-Type: application/json' \
       -d "{\"intuitAccountId\":\"$ID\"}"
  # Lookup by user
  curl -i "http://localhost:8081/api/v1/wallets?intuitAccountId=$ID"
  # Lookup by wallet id (extract from prior response)
  curl -i http://localhost:8081/api/v1/wallets/<walletId>
  # Unknown user → 404
  curl -i -X POST http://localhost:8081/api/v1/wallets \
       -H 'Content-Type: application/json' \
       -d "{\"intuitAccountId\":\"$(uuidgen)\"}"
  ```
- Temporal UI (`http://localhost:8233`) shows one `CreateWalletWorkflow` execution per successful POST.

## Open questions

- None at approval. Auth (JWT) is explicitly deferred to a future ADR + spec.
