# Thapsus Cargo — Cross-Platform Technical Audit

**Audit date:** 2026-05-09
**Scope:** iOS app (`thapsus-v1.1`) ⇄ Web + Backend (`Swiftcargo-main`, branch `JS1`) ⇄ Supabase project `zzdfxsfuhosuqvsugtfd`
**Method:** Read-only inventory + Supabase advisor queries + RLS / function / storage inspection. No code modified during audit.

---

## 1. Executive Summary

Thapsus Cargo is a 5-role logistics platform (customer / operator / clearing-agent / rider / admin) consisting of a Kotlin Multiplatform + SwiftUI iOS app (~974 Swift files, 23 KMP repositories), an Express 5 + React 19 web app (~140 endpoints, ~40 pages, 41 migrations), and a Supabase Postgres 17.6 backing store (48 tables, all RLS-enabled, 3 private storage buckets).

**Overall health: Good, with focused gaps.** Authentication, rate limiting, RLS posture, dependency hygiene, and secret handling are above industry baseline for a single-developer product. The architecture is coherent — KMP repositories, Ktor for Express calls, and Supabase Realtime/Storage cleanly partition responsibilities. Migrations 040/041 show active advisor-driven security cleanup.

**Material risks** are concentrated in three areas:

- **Operational test coverage is effectively absent** — the only test script is a DB connectivity check, leaving every HTTP route, role-check, and webhook signature validator untested.
- **iOS-only workflows** (rider POD capture with OTP, operator scanner intake with AirPrint, rider Outbox/offline mutation queue, offline-first KPI cache) have no Web equivalent — this violates the stated "every iOS feature must exist in Web" baseline rule.
- **A small set of hygiene issues** (axios 1.6.2 with known low/medium CVEs, Supabase leaked-password protection off, ~50 unused indexes, no CSRF tokens — currently mitigated only by JWT-in-header SPA pattern).

**No CRITICAL findings.** The HIGH findings are addressable in 1–3 working days each.

---

## 2. Feature Parity Report (iOS as baseline)

The baseline rule: *every iOS feature must exist in the Web app, and any extras in Web must be flagged.*

### 2.1 Endpoint parity (consumed vs offered)

The iOS app calls **~85 distinct endpoints**; the Express backend exposes **~140**. iOS is a strict subset of the API surface — **no iOS-called endpoint is missing on the server.** This is the well-controlled half of the picture.

### 2.2 Capability parity (UI workflows)

This is where the gaps live. Workflows present in iOS but absent (or stub-only) on Web:

| iOS capability | iOS file/screen | Web equivalent | Gap severity |
|---|---|---|---|
| Operator barcode-scan intake (camera + AVFoundation/Vision) | `OperatorScannerSheet`, `OperatorReceiveView` | `OpsConsole.jsx` has manual-entry form; no scanner | **High** |
| AirPrint label printing on intake | `LabelPrinter`, `ManifestPrinter` (`@MainActor`) | None | **High** |
| Rider Today + Run Stops + POD capture (photo + OTP + signature) | `RiderRunView`, `RunStopListView`, `PodCaptureView` | None | **Critical for parity** |
| Offline mutation queue (Outbox) with manual flush | `OutboxView`, `OutboxViewModel`, SQLDelight outbox table | None — Web is online-only | **High** |
| Offline-first KPI dashboard (real-time updated via Realtime) | `KPIDashboardView` | `KpiDashboard.jsx` exists but online-only (no SQLDelight equivalent) | Medium |
| Quote calculator (fully offline `QuoteEngine`) | `QuoteCalculatorView` | `PricingCalculator.jsx` (online, calls API) | Low |
| Customs entry submission (IDF + entry# + CIF/duty/VAT + doc URL) | `CustomsListView` | `customs.js` route exists but **no clearing-agent UI page** | **High** |
| Clearing-agent invoice submit/track (PDF upload, status badge) | `AgentInvoicesView` | Backend route `/api/agent-invoices/*` exists, **no React page** | **High** |
| Admin DSAR queue + per-request approve/export | `AdminDsarQueueView` | Customer-facing `DsarRequest.jsx` only | Medium |
| Admin error-logs viewer with clear button | `AdminErrorLogsView` | `error-logs` endpoints exist; verify `AdminDashboard.jsx` surfaces them | Low |
| Deep-link payment from email (`thapsus://pay/<id>`) | `PublicPaymentView` | `Payment.jsx` / `PublicPayment.jsx` | Parity present |
| Push notifications inbox | `NotificationInboxView` | `notifications` API exists; verify Web inbox presence | Low |
| Real-time package state via Supabase Realtime | KMP `RealtimeSync` | Web uses Express SSE (`/api/events`) | Different mechanism — acceptable |

Workflows present on Web but **not in iOS** (deltas to flag, not bugs):

- **Marketing surface:** `Home.jsx`, `FAQs.jsx`, `ShipInstructions.jsx`, `TermsOfService.jsx`, `PrivacyPolicy.jsx`, `Pricing.jsx`, `ExchangeRate.jsx`, `WarehouseAddresses.jsx`. Correctly absent on iOS.
- **SEO/discovery infrastructure:** `sitemap.xml`, `robots.txt`, `apple-app-site-association`, `MetaPixel.jsx`, `GoogleAnalytics.jsx`, `CookieConsent.jsx`. Correctly absent on iOS.
- **Public service-worker** (`sw.js`) for PWA caching — iOS doesn't need it.
- **Server-Sent Events** (`GET /api/events`) — superseded by Supabase Realtime on iOS.
- **Insurance UI page** (`Insurance.jsx`) — iOS has `insuranceViewModel()` but no equivalent customer screen in the inventory; verify whether shipped on iOS.

### 2.3 Parity verdict

**Per the baseline rule, the rider workflow is the largest violation.** On Web there is no path for a rider to view today's runs, capture POD, or flush offline mutations. The clearing-agent role has API endpoints but no React pages. These two role gaps explain why several React `Ops*` components do double-duty as the only operator surface.

---

## 3. Security & Database Vulnerabilities

Findings ranked by exploit-likelihood × business impact.

### CRITICAL — none observed.

### HIGH

**H-1. No automated test coverage on auth/permission paths.**
Only `tests/db-test.js` exists. Every JWT validation branch, `requireRole(...)` allowlist, and webhook-signature verifier is untested. A regression in `middleware/auth.js:91-127` would not be caught.
**Mitigation:** add Jest + supertest tests for `/auth/*`, `/admin/*`, `/payments/stripe/webhook`, `/payments/lipana/webhook`, plus a smoke per role-gated route. Target 70% line coverage on `middleware/` and `routes/auth.js`, `routes/admin.js`, `routes/payments.js`.

**H-2. CSRF protection absent.**
No CSRF tokens, no double-submit cookie, no `SameSite=Strict` enforced session cookie. Currently mitigated *only* by the SPA pattern (JWT in `Authorization` header, not cookie) and CORS allowlist. Risk surfaces if a future change adds a cookie-auth fallback or a same-origin form.
**Mitigation:** stay on header-only auth; document this in a `SECURITY.md`; consider `csurf` (or its modern fork) for any future form-encoded endpoint.

**H-3. iOS Android sister-platform stores tokens in plain `SharedPreferences`.**
Per `SecureSettings.kt`, the Android implementation is a TODO/parallel-track; the iOS implementation correctly uses Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. If Android is ever shipped, this is an immediate finding.
**Mitigation:** wire `EncryptedSharedPreferences` (AndroidX Security) before any Android build is submitted.

### MEDIUM

**M-1. `axios` 1.6.2 in `client/package.json`.**
Version 1.6.2 (Dec 2023) predates several CVEs (e.g., CVE-2024-39338 SSRF via protocol-relative URLs, CVE-2025-27152 SSRF/credential-leak). Bump to **1.7.9+** (or the latest 1.7.x) — drop-in compatible.

**M-2. Supabase Auth — leaked-password protection disabled.**
Single Supabase advisor WARN (`auth_leaked_password_protection`). Enable HaveIBeenPwned check in **Auth → Policies** in the dashboard. Dashboard-only setting; no migration needed.

**M-3. CORS wildcard fallback in development.**
`server.js:125-130` defaults `CORS_ORIGIN='*'` when `NODE_ENV='development'`. Acceptable on a developer's laptop, but if `NODE_ENV` is ever unset on Railway, the prod allowlist is bypassed.
**Mitigation:** invert the check — fail closed if `CORS_ORIGIN` is unset *or* `'*'`, regardless of `NODE_ENV`.

**M-4. Stripe + Lipana webhooks bypass rate limiters.**
Correct (signature is the gate), but observe that signature verification *must* run before any DB write. Spot-check `routes/payments.js` to ensure the early-return on bad signature is truly the first thing in the handler.

**M-5. Body-parser limit (10 MB) applied uniformly.**
`express.json({ limit: '10mb' })` is large for non-upload endpoints (auth, admin, etc.). A single misbehaving client can force ~10 MB of JSON parsing.
**Mitigation:** narrow to `'200kb'` globally and bump to 10 MB only on `/api/*/upload-url` mint endpoints.

**M-6. JWT lifetime — `JWT_EXPIRY` defaults to `30d`.**
30 days is long for bearer tokens with no refresh-token rotation. With logout-revocation in place this is acceptable, but a 7-day default + silent-refresh on `/auth/me` would shrink the blast radius of a stolen token.

### LOW / INFO

- **L-1. ~50 unused indexes** flagged by Supabase performance advisor. All on tables with low or zero rows (unbuilt features). Fine to leave; revisit when traffic exists.
- **L-2. CSP allows `'unsafe-inline'` styles.** Necessary for Tailwind without a build-step nonce strategy. Industry-standard tradeoff; document, don't fix.
- **L-3. In-memory rate-limit store.** Fine on a single Railway dyno; will not survive horizontal scaling. Note in deployment docs.
- **L-4. iOS `IPHONEOS_DEPLOYMENT_TARGET = 26.0`.** Confirmed correct for Xcode 26 / iOS 26 (current as of 2026-05). Inventory agent's flag was a training-cutoff false-positive.

### Database (Supabase) detail

| Check | Result |
|---|---|
| RLS enabled on all 48 public tables | OK |
| Storage buckets private, MIME-restricted, ≤10 MB | OK (`agent-invoices`, `pods`, `ticket-attachments`) |
| All `public` schema functions have explicit `search_path` | OK (post-migration 040) |
| `customer_consolidations` policy with `{public}` role + `auth.uid()` qual | OK — anon evaluates to `false`, effectively authenticated-only |
| Deny policies on `password_reset_tokens`, `revoked_tokens`, `pod_otps`, `last_mile_run_parcels` | OK — `qual = false` denies anon+authenticated direct access |
| Schema check constraints on enums (status, method, currency, role) | OK |
| Postgres version | 17.6.1.084 (current GA) |

**No SQL injection vectors detected.** All `pg.query` calls use parameterized `$N` placeholders, including the dynamic `SET` clause builder in `admin.js:446-514` (placeholders, not column-name string concat).

---

## 4. Dependency Audit

### Backend (`Swiftcargo-main` root)

All majors current: `express ^5.2.1`, `helmet ^8.1.0`, `jsonwebtoken ^9.0.3`, `pg ^8.11.3`, `bcryptjs ^3.0.3`, `ws ^8.20.0`, `xss ^1.0.15`, `stripe ^22.1.1`, `@supabase/supabase-js ^2.45.0`. **No upgrade required.**

Notes:
- `react`, `react-dom`, `recharts`, `react-router-dom` appear in the root `dependencies` block — these belong in the client only, or at minimum should be `devDependencies` at root. Cleanup, not a security issue.

### Frontend (`Swiftcargo-main/client`)

| Package | Current | Recommended | Action |
|---|---|---|---|
| **axios** | ^1.6.2 | **^1.7.9** | **Upgrade** (M-1) |
| @stripe/stripe-js | ^9.4.0 | current | hold |
| @stripe/react-stripe-js | ^6.3.0 | current | hold |
| react / react-dom | ^19.2.0 | current | hold |
| react-router-dom | ^7.15.0 | current | hold |
| recharts | ^3.8.1 | current | hold |
| lucide-react | ^0.577.0 | current | hold (do not jump to v1 — brand icons dropped) |
| vite | ^7.3.2 | hold | do not jump to v8 (esbuild dropped) |

### iOS / KMP (`thapsus-v1.1`)

Kotlin 2.1.10, Coroutines 1.10.1, Serialization 1.7.3, Ktor 3.1.2, Supabase-kt 3.1.4, SQLDelight 2.0.2, Koin 4.0.0, SKIE 0.10.1 — all recent. Stripe iOS SDK ≥25.13.0 (with `@preconcurrency` import to bridge Swift 6 strict-concurrency until Stripe ships annotated headers). **No upgrade required.**

---

## 5. Logic & Architecture Review

### 5.1 System architecture (one-paragraph summary)

SwiftUI views observe Kotlin `StateFlow`s via SKIE-bridged `AsyncSequence` adapters (`StateFlowObserver`). The Kotlin layer (`ThapsusSdk` Koin singleton) exposes ~40 view-models, each delegating to one of 23 repositories. Repositories route to Ktor (Express REST), Supabase PostgREST (RLS-gated reads), Supabase Realtime (WebSocket subscriptions for packages/consolidations/notifications), Supabase Storage (signed-URL direct uploads from rider/agent), or SQLDelight (offline cache + outbox). Express is the source of truth for writes; the Express auth tier mints both an `sc_token` (HS256 JWT, 30d) and a Supabase JWT (per request via `/auth/supabase-token`) so PostgREST RLS can enforce `auth.uid()` at the DB. The iOS Outbox queues writes when offline and replays on reconnect; the Web app does not have this primitive.

### 5.2 Strengths

- **Separation of concerns is clean.** No business logic in SwiftUI; no rendering logic in Kotlin; no auth checks in React (correct — the server is authoritative).
- **Defense in depth on auth.** JWT signature → revocation table → `password_changed_at` invariant → role allowlist → RLS at DB. Four layers, each independent.
- **Idempotency is in place** for Stripe (`stripe_events_seen`) and Lipana (`lipana_events_seen`) webhooks. Critical for a payment system; easy to forget; done correctly here.
- **Realtime on iOS, SSE on Web** — different transports for the same concern, but each is appropriate for its platform's network model. iOS over flaky cellular benefits from Supabase Realtime's reconnect handling.

### 5.3 Concerns

- **Two consolidation namespaces** (`/api/consolidation/*` legacy, `/api/consolidations*` v2). The legacy namespace still appears in `routes/`. Audit and remove if no client calls it; otherwise document the deprecation.
- **Order/payment domain is split across many endpoints.** `/api/orders`, `/api/admin/orders`, `/api/customer-consolidations`, `/api/buy-for-me`, `/api/payments`, `/api/admin/payments`. Each pair (customer/admin) has slightly different shapes. Consider a `views/` folder of shared row-projection helpers to ensure customer-vs-admin responses don't drift.
- **iOS Outbox is the only client-side write retry primitive.** If the user is on Web and loses connectivity mid-payment, the optimistic UI lies. Current `Payment.jsx` flow appears to be fully online — verify and at minimum show a clear "lost connection" toast tied to a retry path.
- **No request correlation ID.** Adding `X-Request-Id` (or UUIDv7) at the edge and threading it through morgan + `error_logs` would make incident response much easier. ~30 minutes of work.
- **Rate-limit store is in-memory.** Already noted as L-3. If Railway scaling ever happens, switch to a Redis-backed store (`rate-limit-redis`).

### 5.4 Race conditions / N+1 / hot paths

A full N+1 sweep was not run. The patterns most worth checking on the Web side: `Dashboard.jsx` real-time updates against `Orders` + `Packages` + `Notifications` could fan out into multiple round-trips on hydration. iOS has `RealtimeSync` consolidating subscriptions; verify the Web `/api/events` SSE stream does the same multiplexing rather than three separate fetches.

---

## 6. UI / UX Usability Report

### 6.1 iOS — HIG conformance

Strengths from the inventory:

- Tab-bar-driven navigation per role (5 tabs each) — matches HIG.
- `@MainActor` discipline keeps state mutations on the main thread.
- AirPrint integration for label/manifest printing — proper use of `PrintKit`.
- Liquid-glass design system is consistent and follows iOS 26 visual idiom.

Items to verify (cannot confirm without runtime testing):

- **Dynamic Type** on long-list views (`AdminUsersView`, `ConsolidationListView`) doesn't truncate at large text sizes.
- **VoiceOver labels** on icon-only buttons (FAB on Home, scanner overlay) — easy to forget, big a11y wins.
- **Empty states** — `OutboxView` when empty, `NotificationInboxView` when fresh — should have illustration + actionable copy, not blank list.
- **Confirmations** — money-moving actions correctly use `PaymentConfirmationOverlay`. Spot-check that *every* destructive admin action (deactivate user, cancel order, clear error logs) has the same overlay and not a system alert.
- **Dark-mode contrast** — watch `Brand.ink` as button fill (light-on-light flip) and `.plusLighter` over `.ultraThinMaterial`.
- **No duplicate titles** — verify on any new screens shipped since the last commit.

### 6.2 Web — Responsive / WCAG

Strengths:

- Tailwind + Liquid-glass component set; Vite 7 build chain.
- `helmet` CSP shields against XSS; `xss` library scrubs body/query.

Risks (would need a Lighthouse / axe-core run to confirm):

- **Public pages with marketing copy** (`Home.jsx`, `Pricing.jsx`, `FAQs.jsx`) often regress contrast and heading hierarchy. Run Lighthouse a11y audit; aim ≥95.
- **Operator dashboards with dense tables** (`OpsConsole.jsx`, `OpsConsolidations.jsx`) are the biggest mobile-responsive risk. Confirm they are usable at 768px breakpoint or set an explicit "desktop only" message.
- **`PayInvoiceModal` + Stripe Elements** — Stripe handles its own a11y, but the modal wrapper must trap focus and restore on close. Verify with keyboard-only navigation.
- **Forms** — confirm every input has an associated `<label>`, every error message is `aria-live="polite"`, and required fields don't carry "(optional)" suffixes.
- **Cookie consent banner** must be reachable by keyboard before interactive content is enabled.

### 6.3 Cross-platform consistency

- iOS shows real-time package state; Web uses SSE — UX should not appear different to a customer who switches devices mid-shipment. Spot-check the timeline rendering on both for stale-state hangs.
- Quote calculator: iOS is offline, Web is online. Ensure both produce identical numbers for the same inputs.

---

## 7. Code Quality & Performance

### Backend

- Single `server.js` ~600 lines, 34 route files in `routes/`. Reasonable. The middleware layering (helmet → cors → sanitize → rate-limit → auth → role) is correct order.
- `errorLogger` writes to `error_logs` (table has 6 rows as of audit). Verify retention/rotation — `DELETE /api/admin/error-logs` exists, but no auto-rotation cron.
- Migration discipline is strong (41 numbered files, audit clusters 040/041 are explicit cleanups).

### iOS / KMP

- Swift 6.0 with strict concurrency — every existing concurrency pattern reviewed in the inventory passes.
- 974 Swift files — substantial but not bloated for a 5-role app. View-files dominate; logic stays in Kotlin.
- SKIE bridging gotchas — three layers of defence against unhandled-exception suspend functions are in place.
- iOS `pbxproj` has classic group references; resource files require manual UUIDs in `PBXResourcesBuildPhase`. No tooling automates this — high risk of merge pain on parallel branches. Consider adopting `xcodegen` or `tuist` to regenerate `project.pbxproj` from a manifest.

### Performance

- DB hot paths look indexed (`idx_packages_status`, `idx_orders_user_created`, etc.). The unused-index list is fine — it exists ahead of traffic.
- Rate-limit windows (10/15min on auth, 60/15min on tracking) are tight enough to defend, loose enough not to hassle real users.
- iOS uses Supabase Realtime + SQLDelight cache — KPI tile re-renders should be O(1) on reactive update; verify no unnecessary list rebuilds in `KPIDashboardView` (`@Observable` granularity).

### Memory leaks

- iOS `StateFlowObserver` lifecycle — if any view holds the observer past `.task` cancellation, Kotlin coroutine scopes will leak. Worth a one-pass audit of `.task { for await … }` blocks for missing cancellation.
- Express has no obvious leak vectors (no global mutable state aside from the in-memory rate-limit store).

---

## 8. Strategic Roadmap

Priority order (highest ROI first). Estimates assume one developer.

### Sprint 1 (this week — 1–3 days)

1. **Bump axios to 1.7.9+** in `client/package.json`. (5 min — addresses M-1.)
2. **Enable Supabase leaked-password protection** in dashboard. (1 min — addresses M-2.)
3. **Tighten `express.json` limit** to 200kb globally; raise to 10mb only on upload-url endpoints. (30 min — addresses M-5.)
4. **Invert CORS dev-fallback** so `'*'` requires explicit `CORS_ORIGIN='*'` env var, not just `NODE_ENV !== 'production'`. (15 min — addresses M-3.)
5. **Add `X-Request-Id` middleware** + thread through morgan and `error_logs.metadata`. (30 min — better incident response.)

### Sprint 2 (1–2 weeks) — close the parity gap and add tests

6. **Write Jest + supertest tests** for: auth (register/login/forgot/reset/logout/me), admin role enforcement (one route per role gate), Stripe webhook signature path, Lipana webhook signature path. Target 70% coverage on `middleware/auth.js`, `routes/auth.js`. (3–5 days — addresses H-1.)
7. **Build the rider Web UI** (Today / Run Stops / POD capture). Camera capture via `getUserMedia`; OTP input mirroring iOS. The data layer already exists — this is pure UI. (2–3 days — biggest parity violation.)
8. **Build the clearing-agent Web UI** (Customs entries + Agent Invoices submit/track). The endpoints exist; the React pages don't. (2 days — second-biggest parity violation.)

### Sprint 3 (2–4 weeks) — operational maturity

9. **Reduce JWT expiry to 7 days** + add silent-refresh on `/auth/me` (server returns rotated token if iat-old; clients swap in storage). (1 day — addresses M-6.)
10. **Switch to `xcodegen` or `tuist`** for iOS project regeneration. (1 day — eliminates pbxproj merge pain.)
11. **Add Lighthouse a11y CI step** on the React client. Fail PR if score drops below 90. (2 hours.)
12. **Document the architecture** in `ARCHITECTURE.md` (auth flow, RLS model, Realtime subscriptions, Outbox semantics). (Half day.)
13. **Switch rate-limit store to Redis** *if and when* horizontal scaling is on the table. (Half day.)

### Optional / nice-to-have

14. **Operator scanner-equivalent on Web** — `getUserMedia` + a JS barcode lib (`@zxing/browser` or `quagga2`). Won't match iOS scan quality but unblocks emergency operator coverage. (2 days.)
15. **Web Outbox** using IndexedDB + Service Worker `sync` event. Significant build; only worth it if the rider Web UI ships and offline rider use becomes a real scenario. (1 week.)
16. **Audit log retention policy** — auto-rotate `error_logs` rows older than 30 days; `admin_logs` older than 365 days. (2 hours.)

---

## Appendix A — Quick reference

- **iOS-consumed endpoints not exposed by backend:** none.
- **Backend-exposed endpoints not consumed by iOS:** ~55 (mostly public marketing/static, `/api/events` SSE, legacy `/api/consolidation/*`, public `pricing/*` variants).
- **Critical findings:** 0
- **High findings:** 3 (test coverage, CSRF posture, Android plain-prefs)
- **Medium findings:** 6 (axios, leaked-pw, CORS, webhook ordering, body-size, JWT expiry)
- **Low/Info findings:** 4 (unused indexes, CSP inline, in-mem rate store, iOS deploy target false-positive)
- **DB advisor findings:** 1 WARN (leaked-password), 50 INFO (unused indexes)

---

## Appendix B — Corrections (added 2026-05-09 after remediation began)

While executing the remediation plan I discovered that **several Workstream 4 ("Web parity build") items the audit listed as missing were already shipped** on `Swiftcargo-main` (`JS1`). Read the corrections below before treating the original §2.2 capability gap matrix as authoritative.

### Items the audit flagged as missing — but which already exist on JS1

| Audit item | Original claim | Reality on JS1 |
|---|---|---|
| W4A.1 — Rider Today page | "no Web equivalent" | ✅ `client/src/pages/partner/RiderPwa.jsx` renders Today's runs at `/partner/rider`. |
| W4A.2 — Rider Run Stops | "no Web equivalent" | ✅ Same file — parcel list per run is in-line. |
| W4A.3 — Rider POD capture | "no Web equivalent" | ✅ Same file — file-picker camera capture (`capture="environment"`), 4-digit OTP entry, signed-URL upload via `lastMileApi.podUploadUrl()`, fail-with-reason flow. |
| W4A.5 — Rider role in nav | "missing" | ✅ Wired in `client/src/components/LiquidGlassNav.jsx` rider role group. |
| W4B.1 — Customs entries UI | "no clearing-agent UI page" | ✅ `client/src/pages/partner/AgentPortal.jsx` at `/partner/agent` renders assigned consolidations + per-parcel entry form (IDF, entry no, duty, VAT, IDF fee, RDL). |
| W4B.2 — Agent invoices UI | "no React page" | ✅ `AgentInvoices` named export in the same `AgentPortal.jsx`, mounted at `/partner/agent/invoices`. |
| W4B.3 — Clearing-agent in nav | "missing" | ✅ Wired in `LiquidGlassNav.jsx` clearing-agent role group. |
| W4D.2 — Admin error-logs viewer | "deferred" | ✅ `AdminDashboard.jsx` has an `errorLogs` tab (with stats badge + clear-button). |

### Why this happened
The audit was assembled by sub-agent inventories whose role was to *describe expected feature surfaces* per role. The sub-agents reported "no Web rider workflow" because they were searching by role-keyword (`/rider/*` URL patterns) rather than reading every file under `client/src/pages/`. The actual rider/agent surfaces live under `pages/partner/`, which the role-keyword pass missed. The lesson, captured in `feedback_audits_must_grep.md`: **never claim "X is missing" without grep-verifying on the current branch.**

### Items that were genuinely missing — and have since shipped

| Plan item | Status |
|---|---|
| W4D.3 — Customer notifications inbox | Shipped in PR #143 (now `/notifications`). |
| W4D.1 — Admin DSAR queue | Shipped in PR #144 (`/admin/dsar`). |
| W4C.1 — OpsConsole barcode scanner | Shipped in PR #145 (camera scanner via `@zxing/browser`). |
| W4C.2 — Browser-print parcel label | Shipped in PR #146 (`<style>` + `window.print()` + Code128 via react-barcode). |
| W5 — iOS marketing-link entries | Shipped in iOS PR #25 (`MarketingLinksSection` on every role's Account hub). |

### Items still genuinely missing as of this corrigendum

- **W4A.4 — Web Outbox.** `RiderPwa.jsx` is fully online. No IndexedDB queue, no Service Worker `sync` event. iOS has SQLDelight outbox; Web does not. This is the only meaningful rider-vs-iOS divergence left.
- **W4C.2 manifest print** for `/ops/consolidations` (the parcel-label flavour shipped in #146; multi-parcel manifest is a follow-up).
- **Workstream 6** — operational maturity items (JWT 7-day silent refresh, xcodegen/tuist, Lighthouse a11y CI, log retention cron, `ARCHITECTURE.md`).

### Net audit accuracy

Of the 13 W4 items in the original plan, **8 were already shipped, 5 were genuine gaps.** Strategic Roadmap §8 still stands directionally, but the day-cost estimates for Sprint 2 were inflated by ~60 % because they double-counted shipped work.

---

End of report.
