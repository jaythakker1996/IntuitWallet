# 010 — Frontend POC implementation

- **Status:** Approved
- **Author:** IntuitWalletService
- **Last updated:** 2026-05-06
- **Implements:** TBD (filled with PR/commit)
- **Related ADRs:** [005-frontend-poc](../adrs/005-frontend-poc.md)

## Problem

ADR 005 fixed the frontend tech stack, repo layout, page list, and user flows. The repo now has an empty `frontend/` directory. This spec lands the actual code: the Vite scaffold, all 9 pages, the API client, the component primitives, and the small backend addition (CORS) required to talk to `:8081` from `:5173`.

This is one large mechanical spec, not a per-page split, because ADR 005 already made every per-page decision and the work is mostly typing.

## Goals / Non-goals

- **Goals**
  - Self-contained `frontend/` Vite + React + TypeScript app scaffold.
  - All 9 pages from ADR 005 §4 wired into React Router v6 routes.
  - API client (`api/client.ts`) with `HttpError` that surfaces `{error, message}` bodies from `ApiExceptionHandler`.
  - One file per backend resource under `api/` (`users.ts`, `wallets.ts`, `payments.ts`).
  - Hand-written TS interfaces in `types/api.ts` mirroring Java DTOs exactly.
  - Hand-rolled component primitives: `<Nav>`, `<RequireUser>`, `<Field>`, `<Button>`, `<ErrorBox>`, `<TransactionRow>`, `<BalanceRow>`.
  - Single global stylesheet `index.css` — plain CSS, ~150 lines.
  - Backend `CorsConfig` allowing `http://localhost:5173` for `/api/**`.
  - `npm install && npm run dev` boots on `:5173` and serves the full UI; manual smoke in §Acceptance criteria.
- **Non-goals**
  - Tests. No vitest, no React Testing Library, no Playwright.
  - Linting / Prettier / pre-commit hooks. The TS compiler's `--strict` is the only gate.
  - Bundling the `dist/` into the Spring Boot jar (deferred per ADR 005 §5).
  - Auth, JWT, login-token storage. localStorage `intuitAccountId` + `walletId` only.
  - Service worker, PWA, offline support, dark mode, animations.
  - Any production-style error reporting (Sentry, etc.).
  - QR scan implementation. The button exists, disabled, with "(coming soon)" label.

## API (frontend → backend)

This spec adds **no new backend endpoints**. Login uses the existing idempotent `POST /api/v1/users` per ADR 005 §4.

The CORS config is the only backend change:

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PATCH", "DELETE")
                .allowedHeaders("*");
    }
}
```

Lives at `backend/src/main/java/com/intuit/walletservice/service/CorsConfig.java` next to the existing `OpenApiConfig`.

## Files

### Backend (1 new file)

- `backend/src/main/java/com/intuit/walletservice/service/CorsConfig.java`

### Frontend scaffold (root files)

- `frontend/package.json`
- `frontend/vite.config.ts`
- `frontend/tsconfig.json`
- `frontend/tsconfig.node.json`
- `frontend/index.html`

### Frontend source

- `frontend/src/main.tsx` — entry, mounts `<App>` inside `<BrowserRouter>`.
- `frontend/src/App.tsx` — `<Nav>` + `<Routes>` for all 9 pages, with `<RequireUser>` wrapping protected routes.
- `frontend/src/index.css` — global styles.

#### Types (1 file)
- `frontend/src/types/api.ts` — TS interfaces for every backend response shape (`UserResponse`, `WalletResponse`, `WalletBalancesResponse`, `WalletBalanceResponse`, `WalletTransactionResponse`, `WalletTransactionsResponse`) plus `ApiError`.

#### API (4 files)
- `frontend/src/api/client.ts` — `BASE_URL`, `HttpError`, `get`, `post`, `postWithStatus` (returns body + status so 201 vs 200 can drive UX).
- `frontend/src/api/users.ts` — `loginOrCreate(email)`, `createUser(email, role, region)`, `getUser(id)`.
- `frontend/src/api/wallets.ts` — `createWallet`, `getWalletByUser`, `getWalletById`, `getBalances`, `getBalance`, `getTransactions`, `getTransaction`.
- `frontend/src/api/payments.ts` — `sendPayment`, `fundWallet`.

#### Components (7 files)
- `frontend/src/components/Nav.tsx` — top bar; hidden on `/` and `/signup`. Log out clears localStorage.
- `frontend/src/components/RequireUser.tsx` — auth-gate wrapper.
- `frontend/src/components/Field.tsx` — labeled input/select.
- `frontend/src/components/Button.tsx` — styled `<button>` with `primary` / `secondary` / `danger` variants.
- `frontend/src/components/ErrorBox.tsx` — renders `HttpError` body or generic message.
- `frontend/src/components/TransactionRow.tsx` — one row in a tx list, links to detail.
- `frontend/src/components/BalanceRow.tsx` — one row in a balances list, links to per-stablecoin page.

#### Pages (9 files)
- `frontend/src/pages/LoginPage.tsx` — email-only; `loginOrCreate`; toast on 201.
- `frontend/src/pages/SignupPage.tsx` — email + role + region; `createUser`.
- `frontend/src/pages/HomePage.tsx` — three navigation cards.
- `frontend/src/pages/WalletPage.tsx` — branches on whether wallet exists; lists balances; "View transaction history" link.
- `frontend/src/pages/StablecoinPage.tsx` — single balance + filtered transactions (client-side filter per ADR 005 §8).
- `frontend/src/pages/TransactionsPage.tsx` — full tx list.
- `frontend/src/pages/TransactionDetailPage.tsx` — every field of the wallet-scoped tx response.
- `frontend/src/pages/TransactPage.tsx` — send form + disabled QR placeholder.
- `frontend/src/pages/FundPage.tsx` — fund form.

## Acceptance criteria

- [ ] `cd backend && ./gradlew build` — green.
- [ ] `cd frontend && npm install` — installs cleanly with no peer-dep warnings.
- [ ] `cd frontend && npm run dev` — Vite serves on `http://localhost:5173`.
- [ ] `cd frontend && npm run build` — TypeScript compiles strict; Vite produces `dist/`.
- [ ] **End-to-end browser smoke** with `cd docker && docker compose up -d --build` running:
  - Visit `http://localhost:5173` → LoginPage renders with one email field.
  - Enter a fresh email, click "Sign in" → toast "(account created…)" appears, navigates to `/home`.
  - HomePage shows three cards: Wallet / Transact / Fund.
  - Click Wallet → "Create wallet" button (no wallet yet). Click → wallet appears with empty balances + "Fund your wallet" link.
  - Click Fund → form. Stablecoin USDC, amount 100. Submit → redirect to /wallet, USDC row shows 100.
  - Click Transact → fill in another wallet's UUID (create a second user/wallet first), amount 10, USDC. Submit → redirect to TransactionDetailPage with full tx fields.
  - Click "View transaction history" from /wallet → list shows FUND (INBOUND, CREDIT) and SEND (OUTBOUND, DEBIT), newest first.
  - Click a tx row → detail page renders every field.
  - Click USDC row on /wallet → StablecoinPage shows balance + transactions filtered to USDC.
  - Click "Log out" → localStorage cleared; back at LoginPage.
- [ ] Visit `/wallet` directly without logging in → redirected to `/`.
- [ ] CORS works (no console errors when frontend talks to `:8081`).

## Out of scope confirmations (echoed from ADR 005)

- No tests, no lint, no Prettier.
- No bundling into Spring Boot jar.
- No auth, no JWT, no protected routes beyond the localStorage gate.
- No QR functionality (button exists, disabled).
- No real-time updates (no WebSocket / SSE).
- No mobile responsiveness audit. Layout works on desktop; phone view not optimized.

## Open questions

None. ADR 005 resolved everything; this spec is execution.
