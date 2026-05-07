# 005 — Frontend POC architecture

- **Status:** Accepted
- **Date:** 2026-05-06
- **Author:** IntuitWalletService

## Context

Specs 002–009 build the wallet service's HTTP API end-to-end (create user, create wallet, fund, send, get balances, get transactions). There is no UI; the only way to exercise the system is `curl` + pgweb. To demo the flows and surface UX issues that aren't visible from the API shape alone, the POC needs a thin web UI sitting in front of the existing Spring Boot service.

This ADR records the cross-cutting decisions for that UI: tech stack, folder structure, the dev loop, the repository layout that introducing it forces, and what's intentionally out of scope. Per-page UX and component decisions live in feature specs.

The frontend is **POC-scoped** in the same sense the User domain (ADR 002) and the wallet domain (ADR 003) are — every choice here gets revisited before any production-bound feature ships, especially auth, error handling, and the build/deploy pipeline. The goal is "drive the existing API end-to-end through a browser" with the smallest plausible amount of code.

## Decision

### 1. Add a React-based frontend

A small, self-contained web UI lives alongside the backend. **Single-page React** running in the browser, talking to the existing `:8081` API over HTTP. No SSR, no Node.js process in production, no GraphQL layer, no BFF.

Concrete stack picks (each chosen for "smallest plausible thing that works for a POC"):

- **Build tool: Vite.** Plain `npm create vite@latest`. Fast cold start, ~2s HMR, no framework opinion baked in. Static `dist/` output that can be served by anything.
- **Language: TypeScript.** The Java DTOs you've already shipped are strongly typed; surfacing that on the client side costs nothing and prevents the obvious "I sent the wrong field" class of bugs.
- **UI: React 18+** with **React Router v6** for client-side routing.
- **Styling: plain CSS** in a single global stylesheet (`src/index.css`). No Tailwind, no CSS-in-JS, no design tokens. Promote to Tailwind only if the styling surface grows past ~200 lines of CSS.
- **No component library.** No shadcn/ui, no MUI, no Chakra. Hand-rolled `<Field>`, `<Button>`, `<Table>` primitives.
- **No global state library.** `useState` + URL params + a small `useUser()` hook reading from `localStorage`. No Redux, no Zustand, no React Context (yet).
- **HTTP: plain `fetch`** wrapped in a small `api/client.ts`. No axios, no TanStack Query — fewer moving parts to debug, no cache invalidation surface to think about.
- **Forms: HTML5 `required` + manual checks.** No zod, no react-hook-form. The forms here are 3–5 fields each.
- **No client-side tests.** The backend is well-tested; the frontend is a thin shell over the API. Manual smoke via `npm run dev` is the test plan.

### 2. Repository layout

Adding a frontend forces the repo to grow a second top-level concern. Rather than nesting frontend under backend or sprinkling files at root, the repo is restructured into peer subdirectories:

```
IntuitWallet/
├── adrs/                   ← cross-cutting; covers FE + BE
├── specs/                  ← cross-cutting; covers FE + BE
├── docker/
│   ├── docker-compose.yml
│   └── (Dockerfiles when added)
├── backend/                ← was the repo root
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew, gradlew.bat
│   ├── gradle/wrapper/
│   └── src/
├── frontend/               ← new
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── src/
├── CLAUDE.md
├── README.md
└── .gitignore
```

**Rationale for the layout:**
- **`backend/` and `frontend/` as peers** — neither owns the other; either can be replaced or removed without restructuring.
- **`docker/` as its own folder** — compose files and Dockerfiles are infrastructure, not code; isolating them keeps the build surface clean and lets future `docker/` siblings (CI configs, helm charts) land naturally.
- **`adrs/` and `specs/` stay at root** — they're cross-cutting. A spec for "send payment" implicates both sides; an ADR like this one spans both. Putting either inside `backend/` or `frontend/` would imply ownership the docs don't have.

**Why now:** moving the backend after the frontend exists means updating both at once and dealing with paths in both directions. Doing it before the frontend is created means the frontend lands in its final location on day one. The git rename is auto-detected; commit history follows.

### 3. Frontend folder structure

Inside `frontend/`, deliberately flat:

```
frontend/src/
├── main.tsx              ReactDOM entry
├── App.tsx               root layout: nav + <Routes>
├── api/
│   ├── client.ts         fetch wrapper, base URL, JSON helpers
│   ├── users.ts          createUser, getUser
│   ├── wallets.ts        createWallet, getWallet, getBalances, getTransactions
│   └── payments.ts       sendPayment, fundWallet
├── types/
│   └── api.ts            TS interfaces mirroring Java DTOs
├── pages/
│   ├── HomePage.tsx
│   ├── CreateUserPage.tsx
│   ├── WalletPage.tsx    balances + send/fund forms for one wallet
│   └── TransactionsPage.tsx
├── components/
│   ├── Nav.tsx
│   ├── Field.tsx         labeled input
│   └── ErrorBox.tsx      renders the {error, message} body
└── index.css             single global stylesheet
```

**Rules that keep it flat:**
- One file per backend resource in `api/`, mirroring the controllers.
- Pages own their state. No `contexts/`, `stores/`, or `hooks/` folders until a folder would have ≥3 files.
- Types match the Java DTOs exactly — no camelCase/snake_case translation, no field renaming. When you add a Java field, you add a TS field.
- No barrel files (`index.ts` re-exports), no premature folders.
- "Promote when needed": `pages/` splits into `pages/wallet/` only after 8+ files; `hooks/` exists only after the 3rd reusable hook appears.

### 4. Pages, routing, and user flows

The UI surfaces nine pages behind a flat React Router map. Pages are listed by route, with the API calls each page makes.

#### Routing map

| Route | Page | Purpose |
| --- | --- | --- |
| `/` | `LoginPage` | Email-only login. Link to `/signup`. |
| `/signup` | `SignupPage` | Create user form. |
| `/home` | `HomePage` | Three big cards: **Wallet**, **Transact**, **Fund**. |
| `/wallet` | `WalletPage` | List of stablecoin balances OR "Create wallet" button. "View transaction history" link. |
| `/wallet/balances/:stablecoin` | `StablecoinPage` | Single stablecoin balance + transactions filtered to that stablecoin (client-side filter — see §8). |
| `/wallet/transactions` | `TransactionsPage` | Latest 100 transactions involving this wallet. |
| `/wallet/transactions/:txId` | `TransactionDetailPage` | One transaction's full detail. |
| `/transact` | `TransactPage` | Send-payment form. Disabled placeholder for "Scan QR" (deferred). |
| `/fund` | `FundPage` | Fund-wallet form (stablecoin dropdown + amount). |

All routes except `/` and `/signup` are gated behind a tiny `<RequireUser>` wrapper that reads `intuitAccountId` from `localStorage` and redirects to `/` if absent.

#### Session model

No real auth. After login or signup, the frontend persists exactly two values in `localStorage`:

- `intuitAccountId` — the UUID returned by the User API.
- `walletId` — the UUID of the user's wallet, fetched lazily on first wallet visit (and cached). If the user hasn't created a wallet yet, this stays `null` until they do.

A "Log out" button in the nav clears both keys and routes back to `/`. There's no token, no expiry, no refresh — clearing localStorage is the entire logout path. This is consistent with the rest of the POC's "auth deferred to a future ADR" stance.

#### Per-page detail

**`LoginPage` (`/`)**
- Single field: email.
- Submit calls `POST /api/v1/users` with `{email, role: "CONSUMER", homeRegion: "us-east-1"}`. The existing endpoint is idempotent on email (per spec 002) — returns 200 with the existing record if the email is already registered, 201 with a freshly created record otherwise. The hardcoded `role` and `homeRegion` defaults are only used when the user is new; for returning users the stored values are preserved (the API ignores the body's role/region on the dedup path).
- 201 → store `intuitAccountId`; navigate to `/home`. Show a small "(account created with default role)" toast — surfaces that the email wasn't recognized so a typo doesn't silently spawn a duplicate-feeling account.
- 200 → store `intuitAccountId`; navigate to `/home`.
- 400 → "Invalid email" inline error.
- Footer link: "Want to choose your role / region? Sign up".

**Why this shape:** the backend has no `GET /users?email=` endpoint, and adding one purely for the frontend's login flow isn't worth a backend change at POC scope. The idempotent POST already does "get-or-create", which is what email-only login needs. The trade-off — typing an unfamiliar email auto-creates an account with defaults — is acceptable for POC; production auth (where this whole flow gets replaced) won't have this concern.

**`SignupPage` (`/signup`)**
- Three fields: email, role (dropdown: `CONSUMER`, `MERCHANT`), homeRegion (dropdown: `us-east-1`, `us-west-2` — the two regions used in existing tests).
- Submit calls `POST /api/v1/users`. Idempotent on email per spec 002; existing user returns 200 with the same payload as create.
- Stores `intuitAccountId`, navigates to `/home`.

**`HomePage` (`/home`)**
- No API calls. Just three large clickable cards: **Wallet** → `/wallet`, **Transact** → `/transact`, **Fund** → `/fund`.
- Tiny header: "Welcome back" + email (if we want to surface it; require a follow-up `GET /api/v1/users/{intuitAccountId}` call on home — optional).

**`WalletPage` (`/wallet`)**
- On load: `GET /api/v1/wallets?intuitAccountId=<id>`.
  - 200 → cache `walletId` in localStorage; fetch balances via `GET /api/v1/wallets/<walletId>/balances`; render the balance list.
  - 404 → render a single "Create wallet" button. Click → `POST /api/v1/wallets`, then re-render.
- Balance list: one row per stablecoin showing `stablecoin`, `runningAvailable`, and a small "›" affordance. Clicking the row navigates to `/wallet/balances/{stablecoin}`.
- Footer link: "View transaction history" → `/wallet/transactions`.

**`StablecoinPage` (`/wallet/balances/:stablecoin`)**
- On load: `GET /api/v1/wallets/<walletId>/balances/<stablecoin>` for the single balance.
  - 200 → render the balance plus a transactions sub-list (next bullet).
  - 404 with `STABLECOIN_NOT_ENABLED` → render "You haven't transacted in `<stablecoin>` yet" and a back link.
- Sub-list: `GET /api/v1/wallets/<walletId>/transactions`, then **client-side filter** to entries whose `stablecoin` matches the path param. Renders the same transaction-row component used on `TransactionsPage`. Each row links to `/wallet/transactions/{txId}`.

**`TransactionsPage` (`/wallet/transactions`)**
- On load: `GET /api/v1/wallets/<walletId>/transactions`. Renders the array as a list, newest first (already sorted by the backend). Each row shows `direction` arrow (↗ outbound, ↙ inbound), `type`, `amount`, `stablecoin`, `status`, and a relative timestamp. Click → `/wallet/transactions/{txId}`.

**`TransactionDetailPage` (`/wallet/transactions/:txId`)**
- On load: `GET /api/v1/wallets/<walletId>/transactions/<txId>`.
  - 200 → render every field from `WalletTransactionResponse` as a labeled key/value (txId, type, direction, entryType, fromWalletId, toWalletId, amount, stablecoin, fee, status, runningAvailableAfter, runningPendingAfter, entrySequence, createdAt).
  - 404 with `TRANSACTION_NOT_FOUND` → "Transaction not found for this wallet" + back link.

**`TransactPage` (`/transact`)**
- Form: `toWalletId` (UUID input), `amount` (decimal input), `stablecoin` (dropdown: hardcoded `USDC`, `USDT`, `EURC`).
- `idempotencyKey` is generated by the frontend (`crypto.randomUUID()`) when the form mounts; not user-editable, but stable across submit retries (resets only on full page navigation).
- Submit calls `POST /api/v1/payments` with `fromWalletId = localStorage.walletId`.
  - 201 → navigate to `/wallet/transactions/{txId}` for the just-created tx.
  - 200 → same destination; "(idempotent retry)" toast.
  - 404 → "Recipient wallet not found" inline error.
  - 422 with `INSUFFICIENT_BALANCE` → "Not enough `<stablecoin>` to send" inline error.
  - 422 with `IDEMPOTENCY_CONFLICT` → forces an `idempotencyKey` regeneration and re-submit prompt.
- **QR placeholder**: a disabled secondary button labeled "Scan QR (coming soon)". No camera access wired up. Lives in the markup so the layout reflects the eventual two-modal UX.

**`FundPage` (`/fund`)**
- Form: `stablecoin` (same dropdown), `amount`. Same `idempotencyKey` handling as Transact.
- Submit calls `POST /api/v1/wallets/<walletId>/fund`.
  - 201 → navigate to `/wallet`.
  - 200 → same destination; "(idempotent retry)" toast.
  - 404 with `WALLET_NOT_FOUND` → redirect to `/wallet` (forces wallet creation if missing).

#### User flows

These are the end-to-end paths the POC is designed to support. Each step is one user action; numbers in parens are HTTP calls.

1. **First-time signup → fund → check balance.**
   `/` → "Sign up" → `/signup` (fill email/role/region, submit, **POST /users**) → `/home` → click Wallet → `/wallet` (**GET /wallets?intuitAccountId**, 404) → click "Create wallet" (**POST /wallets**, **GET /wallets?intuitAccountId** to refresh, **GET /balances** returns empty) → back → click Fund → `/fund` (fill USDC/100, submit, **POST /wallets/{id}/fund**) → `/wallet` (**GET /balances** shows USDC: 100).

2. **Returning user logs in and sends.**
   `/` (enter email, **GET /users?email**) → `/home` → click Transact → `/transact` (fill recipient/amount/stablecoin, submit, **POST /payments**) → `/wallet/transactions/{txId}` (**GET /transactions/{txId}**).

3. **User browses history, drills into a transaction.**
   `/home` → Wallet → click "View transaction history" → `/wallet/transactions` (**GET /transactions**) → click a row → `/wallet/transactions/{txId}` (**GET /transactions/{txId}**).

4. **User explores a single stablecoin.**
   `/wallet` → click USDC row → `/wallet/balances/USDC` (**GET /balances/USDC**, **GET /transactions** filtered client-side).

5. **User who has never used a stablecoin clicks it.**
   Currently can't — the WalletPage only renders stablecoins the user has held. The 404 `STABLECOIN_NOT_ENABLED` path on StablecoinPage is reachable only by typing the URL directly. Documented as the safety net.

6. **User logs out.**
   Click "Log out" in nav → localStorage cleared → redirect to `/`.

#### Component primitives

Hand-rolled, single-file each, no exports beyond the component:

- `<Nav>` — shows brand + logged-in email + Log out button. Hidden on `/` and `/signup`.
- `<Field label="..." name="..." type="..." />` — labeled input, supports `text` / `email` / `number` / `select` (with options array).
- `<Button variant="primary"|"secondary"|"danger">` — single styled `<button>`.
- `<ErrorBox>` — renders the `{error, message}` body returned by `ApiExceptionHandler`. Color-coded by `error` code.
- `<TransactionRow tx={WalletTransactionResponse}>` — used by both `TransactionsPage` and `StablecoinPage`.
- `<BalanceRow balance={WalletBalanceEntry}>` — used by `WalletPage`.
- `<RequireUser>` — auth-gate wrapper that reads localStorage and redirects to `/` if missing.

### 5. Dev loop: two processes during local development

The two builds run independently:

| Process | Command (from root) | Port |
| --- | --- | --- |
| Backend | `cd backend && ./gradlew bootRun` (or `docker compose -f docker/docker-compose.yml up -d`) | `:8081` |
| Frontend | `cd frontend && npm run dev` | `:5173` |

Vite's dev server proxies nothing; the React code calls `http://localhost:8081/api/v1/...` directly. This requires a small **CORS config** on the backend allowing the dev origin (`http://localhost:5173`). Five lines, one new `@Configuration` class. POC-scoped — the eventual production CORS rules belong in the future auth ADR.

**Single-command demo deferred.** A future option (sketched in §6) is to have Gradle build the frontend and bundle `dist/` into Spring Boot's `static/` so `docker compose up -d` brings the entire stack up on `:8081`. Worthwhile when the demo story matters; not worth the build coupling during iteration.

### 6. TypeScript ↔ Java DTO contract

Hand-written TS interfaces in `frontend/src/types/api.ts` that mirror the Java response records (`UserResponse`, `WalletResponse`, `TransactionResponse`, `WalletBalancesResponse`, `WalletTransactionsResponse`, etc.). When a Java DTO field changes, the TS interface gets the same change in the same PR.

**No code generation** (e.g. OpenAPI codegen, springdoc → typescript-fetch). The DTO surface is small (~10 records); a code-gen pipeline costs more than it saves at POC scale and adds a build coupling that complicates the iteration loop. Revisit when the API surface gets to ~30+ DTOs or when drift bites in practice.

### 7. Auth: out of scope

Same carve-out as specs 006–009. The frontend takes user-supplied identifiers (intuitAccountId, walletId, etc.) directly from form inputs or URL params. There's no login screen, no session, no JWT. A future "auth" ADR + spec adds: backend OAuth2 resource-server config, frontend login flow, axios/fetch interceptor that attaches the token, and the production CORS policy. None of that is here.

### 8. Backend changes required by the frontend

One small addition, POC-scoped:

1. **CORS configuration.** A `@Configuration` class implementing `WebMvcConfigurer.addCorsMappings(...)` to allow `http://localhost:5173` for `/api/**`. Five lines. Will be replaced with a stricter policy when auth lands.

That's it. The login flow reuses the existing idempotent `POST /api/v1/users` — see `LoginPage` in §4. No new endpoints, no new core-service methods. The CORS class lands as part of the first frontend feature spec.

#### Client-side filtering caveat

The `StablecoinPage` filters transactions by `stablecoin` client-side because the backend's `GET /api/v1/wallets/{walletId}/transactions` doesn't support a `?stablecoin=` filter. With the existing 100-row cap, that's fine for POC. A native filter is a follow-up backend spec when the per-stablecoin view starts hitting the cap.

### 9. What's out of scope for this ADR

- **Specific pages** (which pages exist, what fields they show, what state they manage). Each page is a feature spec — likely `010-frontend-create-and-fund.md`, `011-frontend-send-payment.md`, etc.
- **Component library or design system.** None for POC.
- **Real-time balance updates** (WebSocket / SSE). Backend doesn't have an event stream yet (deferred per ADR 004 outbox follow-up).
- **Mobile.** No React Native, no PWA.
- **Internationalization, accessibility audit, animation libraries, charting, dark mode.**
- **CI for the frontend.** No GitHub Actions, no preview deployments. `npm run build` is the smoke test.
- **Production deployment topology.** Vite's `dist/` is currently a static folder; whether it's served by Spring Boot, an nginx sidecar, a CDN, or a separate service is a future deployment ADR.
- **A `Makefile` or root-level command runner.** If the `cd backend && ./gradlew ...` typing gets old, that's a 5-minute Makefile addition; it doesn't deserve an ADR.

## Consequences

- **Positive**
  - The wallet system becomes demonstrable end-to-end in a browser, not just via curl.
  - Backend and frontend evolve independently — no SSR coupling, no Node-in-the-Spring-Boot-jar gymnastics during POC iteration.
  - Hand-written TS types enforce the API contract at compile time; refactors that drift the DTO show up as TS errors before they ship.
  - The new repo layout mirrors how a real product team would structure this: peers under root, infra in its own folder, cross-cutting docs at root. Easy to onboard.
  - Vite + plain React + plain CSS is the smallest plausible toolchain. Faster than any alternative for this scale.

- **Negative**
  - Two ports during dev is one more thing to remember. CORS config exists solely to enable this and will need to be replaced by a stricter production policy.
  - No code-generated TS types means manual sync work. Not a real cost at 10 DTOs; would be at 100.
  - Repository move forces one disruptive commit (every existing path under `backend/`). Subsequent path-bearing references — `CLAUDE.md`, `README.md`, `docker-compose.yml`, plan files in `~/.claude/plans/` — all need updating in the same PR. Manageable, but worth doing carefully.
  - Skipping a component library means re-implementing primitives like dialogs and toasts when needed. Acceptable for the POC's small page count; revisit if the page count grows past ~10.

- **Follow-ups**
  - First frontend feature spec lands the actual scaffold (Vite init, the `frontend/` tree above, the CORS config on backend, README updates) plus the first page or two.
  - Auth ADR + spec when login becomes a requirement.
  - "Single-command demo" ADR/spec (option 2 from the earlier discussion: Gradle task that bundles `dist/` into Spring Boot's `static/`) when the demo story matters more than iteration speed.
  - Real-time balance updates land alongside the broader outbox + Kafka follow-up from ADR 004.
  - CI integration when there's a remote that needs gating.

## Alternatives considered

- **Server-rendered Thymeleaf inside Spring Boot.** Drops all frontend tooling — no Node, no separate dev server, no CORS, ships in one PR. Rejected because it locks the UI into the same JVM build as the API, makes future SPA features (real-time balance updates, optimistic UI) painful, and trains the wrong reflexes for a system that wants a real frontend eventually. Worth it for an internal admin where a real frontend will never happen; not for a wallet POC that's adjacent to consumer-facing flows.
- **Static `index.html` + plain JS, no framework, no build.** Lightest possible — one file, open in browser, done. Rejected because past three forms the manual DOM updates get painful, and there's no type safety against the API.
- **Next.js (App Router) instead of Vite + React Router.** Standard for production React, has SSR, server components, file-based routing. Rejected for POC because none of those features are needed and they add complexity (server-component vs client-component dance, deployment requires a Node runtime). Re-evaluate if the product needs SEO, server-side rendering, or middleware-style auth.
- **Bundle the frontend `dist/` into Spring Boot's static resources from day one.** Single deployment artifact, single port, no CORS. Rejected because every frontend rebuild then requires a Gradle task; HMR feels worse; the toolchains are coupled. Better landed later as a packaging optimization, not as the dev model.
- **Monorepo with the existing backend code at root and frontend nested inside `web/` or `client/`.** Avoids moving the backend. Rejected because it implies ownership ("the web layer is part of the backend") that the system shouldn't have, and because once frontend exists with its own toolchain, the asymmetry feels wrong. Doing the move now is cheap; doing it after the frontend has matured is expensive.
- **A component library (shadcn/ui, MUI, Chakra) from day one.** Pretty UI for free. Rejected because the page count is small enough that the library setup cost (Tailwind + theme + first-time config) exceeds the cost of hand-rolled inputs and tables. Easy to add later if styling work compounds.
- **A `Makefile` or `package.json` workspaces at root for unified commands.** Real product convenience. Rejected for now — `cd backend && ./gradlew ...` and `cd frontend && npm run ...` are explicit and unambiguous, and the only thing they'd save is keystrokes. Add when keystrokes start to hurt.

## Decisions made (all picked for "smallest plausible POC")

- **CSS:** plain CSS, single `index.css`. Tailwind deferred until styling surface > ~200 lines.
- **Routing:** React Router v6.
- **HTTP:** plain `fetch` wrapped in `api/client.ts`. No TanStack Query, no axios.
- **Forms:** HTML5 `required` + manual checks. No zod, no react-hook-form.
- **Single-command demo:** deferred. Two ports during dev (`:8081` backend, `:5173` frontend).
- **Type sync:** hand-written TS interfaces in `frontend/src/types/api.ts` matching the Java DTO field names exactly. No codegen.
- **Root convenience commands:** none. `cd backend && ./gradlew ...` and `cd frontend && npm run ...` stay explicit. A `Makefile` becomes worthwhile if either invocation pattern grows past two arguments.

## Sequencing

1. **This PR (ADR 005 + repo move).** Once this ADR is Accepted: move the existing repo into `backend/`, add `docker/docker-compose.yml` (relocated), update `CLAUDE.md` / `README.md` / `docker-compose.yml` paths, commit. No frontend code yet, no Vite scaffold.

2. **First frontend feature spec (`010-frontend-scaffold-and-login.md`).** Lands the `frontend/` Vite scaffold, the global stylesheet, the routing skeleton, the `<Nav>` / `<RequireUser>` / `<Field>` / `<Button>` / `<ErrorBox>` primitives, and the **LoginPage** + **SignupPage** + **HomePage** flows. Same PR adds the backend's CORS config and the `GET /api/v1/users?email=` endpoint required by login.

3. **Subsequent frontend specs**, one per page or pair of pages, in roughly this order:
   - `011-frontend-wallet-and-balances.md` — `WalletPage` + `StablecoinPage`.
   - `012-frontend-transactions.md` — `TransactionsPage` + `TransactionDetailPage`.
   - `013-frontend-transact-and-fund.md` — `TransactPage` + `FundPage`.

   Each spec is small and ships independently.
