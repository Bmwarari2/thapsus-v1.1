# Architecture

How the Thapsus Cargo mobile platform fits together. Read this after
[`README.md`](./README.md) when you want the why-behind-the-what — KMP
layering, role routing, payments, realtime, the offline outbox, and the
non-obvious invariants the code depends on.

For build and run instructions stay in `README.md`. For the backend
details see [`Bmwarari2/swiftcargo-main`](https://github.com/Bmwarari2/swiftcargo-main).

---

## 1. Top-level shape

```
   ┌────────────────┐         ┌────────────────┐
   │   iOS app    │         │  Android app  │
   │  (SwiftUI)   │         │   (Compose)   │
   └─────┬────────┘         └────────┬───────┘
         │ SKIE wrappers          │ collectAsState
         │                        │
         └────────┬────────────────┘
                  ▼
   ┌───────────────────────────────────┐
   │  Shared KMP core (shared/)              │
   │   ThapsusSdk → di → presentation →      │
   │   repositories → data/remote + local   │
   └────┬──────────────────────────────┘
        │
        ├────────────────────┐
        ▼                       ▼
   ┌────────────┐      ┌─────────────────┐
   │ Express API │      │   Supabase     │
   │ (swiftcargo)│      │  Postgres +    │
   │  writes     │      │  Storage +     │
   │  webhooks   │      │  Realtime + RLS│
   └────────────┘      └─────────────────┘
```

Two native apps, one shared KMP core, two backends:
- **Express API** (`Bmwarari2/swiftcargo-main`) for all mutations
  (auth, orders, BFM, payments, tickets, admin) and webhook handling.
- **Supabase** for direct PostgREST reads + Realtime subscriptions,
  Storage (POD photos, invoices, tickets attachments), and short-lived
  JWT-gated access under RLS.

The Express tier is the only write path. The mobile apps read via
PostgREST + Realtime for low-latency UI; every mutation routes through
Express so business logic and auditing stay in one place.

---

## 2. KMP shared core layout

```
shared/src/commonMain/kotlin/com/thapsus/cargo/
├── ThapsusSdk.kt        # iOS + Android entry point façade
├── di/ThapsusModule.kt  # Koin DI wiring
├── data/
│   ├── dto/             # 35+ wire shapes mirroring Supabase + Express rows
│   ├── remote/          # SupabaseClientFactory, ThapsusApiClient (Ktor),
│   │                    # RealtimeSync, Tables, SecureSettings, AuthEventFlags
│   ├── local/           # ThapsusLocalCache (SQLDelight), DatabaseDriverFactory
│   └── repository/      # 25 facades — single read/write API per domain
├── domain/
│   ├── auth/PasswordPolicy.kt
│   ├── model/           # Money, ParcelDimensions, UserRole
│   ├── pricing/         # QuoteEngine, VolumetricWeightCalculator
│   └── prohibited/      # ProhibitedItemsCatalog (UK→KE)
├── presentation/        # 40+ StateFlow view models
└── util/                # CoroutineErrorHandler, UnhandledExceptionHook
```

SQLDelight files live under `shared/src/commonMain/sqldelight/com/thapsus/cargo/db/`:
`Consolidation.sq`, `CustomsEntry.sq`, `HomeGreetingSeen.sq`,
`LastMileRun.sq`, `Notification.sq`, `OutboxFailure.sq`, `Package.sq`,
`PendingMutation.sq`, `Ticket.sq`, `TicketMessage.sq`.

`ThapsusSdk.shared.start(supabaseUrl, supabaseAnonKey, apiBaseUrl, driverFactory, secureSettings)`
is the single entry point. It composes the Koin graph, configures the
Supabase client + Ktor `ThapsusApiClient`, opens the SQLDelight cache,
and exposes every view-model factory through the SDK façade. Both apps
call it once at start-up.

---

## 3. Authentication

The Express tier mints two tokens at `/auth/login` and `/auth/register`:

- **`sc_token`** — HS256 JWT, default 7-day lifetime (since PR #149).
  Used as `Authorization: Bearer <…>` on every Express call. The shared
  `AuthRepository` carries the latest token in `SecureSettings` (Keychain
  on iOS, EncryptedSharedPreferences on Android).
- **`supabase_token`** — Short-lived (default 1h) Supabase JWT, used for
  direct PostgREST + Realtime calls under RLS.

### Silent refresh

Both apps treat the `refreshed_token` field on `GET /auth/me` as the
canonical replacement token. iOS calls `/me` on `.onAppear` and on
`scenePhase == .active` foreground transitions (PRs #27, #28). Android
does the equivalent via lifecycle observers. This keeps live sessions
warm without ever forcing a re-login on schedule.

### Graceful 401

When the server returns 401 (token expired/revoked, password changed,
user deactivated), the shared `AuthEventFlags` flips and both apps
transition out of the authenticated state:
- **iOS** (#29): banner on `SignInView`, `AuthSession.state` moves to
  `.signedOut(reason: .expired)`.
- **Android** (#87): equivalent transition through the auth event flow.

Password reset bumps `users.password_changed_at` server-side; any JWT
with `iat` predating that timestamp is rejected, so reset invalidates
every outstanding token across both apps simultaneously.

### Sign-up policy

The shared `PasswordPolicy` (#91, NIST SP 800-63B) requires ≥8 chars,
≥1 letter, ≥1 number. Live UI hints render on both platforms' sign-up
forms. T&Cs / Privacy checkbox is mandatory (iOS #31, Android #90).

---

## 4. Role-based tab navigation

`Navigation/RootTabView.swift` (iOS) and `ui/RootScreen.kt` (Android)
branch on `UserRole` from the shared model. Each role gets a distinct
top-level set of tabs:

```kotlin
enum class UserRole { customer, operator, clearingAgent, rider, admin }
```

The Buy-for-me pivot (2026-05-13) reordered the customer tab order to
**Home · Shop (BFM) · Activity · Quote · Account** and the operator order to
**BFM Queue · Receive · Consols · Dispatch · Account** — BFM is the
default landing surface for both. The previous "Today" operator tab is
still reachable but no longer the default (PRs #64, #69).

---

## 5. Payments

Two providers, one outcome. The flow:

1. Customer taps Pay on an invoice card / BFM quote / parcel.
2. App calls Express to mint a provider intent
   (`/api/payments/stripe/intent` or `/api/payments/lipana/initiate`).
3. App presents the provider sheet:
   - **Stripe:** `StripePaymentSheet` SPM on iOS (≥ 25.13), Stripe
     Android SDK on Android (20.52, PR #76).
   - **Lipana:** custom glass sheet on iOS (#17), bottom-sheet on Android
     (#73). Both drive the Daraja STK Push prompt onto the customer's
     phone.
4. Provider webhook hits Express, which calls
   `utils/markPaymentPaid.js` once. That helper is the **only** code
   path for post-payment side-effects (parcel status flip, credit ledger
   debit, receipt email) — don't duplicate it elsewhere.
5. Realtime fanout pushes the new state to the app, which dismisses
   the sheet and shows a **full-screen confirmation overlay** (iOS #14,
   Android equivalent) instead of a missable inline banner. PR #53
   removed the bouncy spring animation that obscured the receipt
   summary.

Webhook idempotency is enforced server-side via `stripe_events_seen` /
`lipana_events_seen`; retries land on the same row twice but only run
side-effects once. See the backend `ARCHITECTURE.md` for the raw-body
mount ordering.

---

## 6. Quote calculator (shared engine)

`shared/.../domain/pricing/QuoteEngine.kt` implements the **six-knob**
pricing model server-side migration 051 introduced (PR #46):

- `pricing_settings` — base rates, FX margins, processing line.
- `customs_tiers` — weight-band lookup.
- `hs_code_tiers` — HS-code-driven multipliers.
- `electronics_fees` — electronics surcharge schedule.
- Volumetric weight: `max(actual_kg, L·W·H / 6000)`.
- FX refreshed per quote from a shared `FxState` (#60), with KES totals
  guaranteed sum-stable.

Customer surfaces render **KES** (server-side FX for parity);
operator/admin surfaces render **GBP** (#47, #208). The public Quote
calculator zeroes declared-value by default (parity with web, #58–59)
and hides the customs estimate (KRA charges separately on clearance,
#59). Weight cap raised from 80 kg to 1000 kg in #55.

The Android calculator (`QuoteCalculatorScreen`) and iOS
(`QuoteCalculatorView`) both share `QuoteViewModel` from the KMP layer
(PR #70 brought Android to parity). One engine, two presentations.

---

## 7. Realtime + offline outbox

### Realtime

`shared/.../data/remote/RealtimeSync.kt` consolidates Supabase Realtime
subscriptions for `packages`, `consolidations`, `customer_consolidations`,
`notifications` into a single coroutine `Flow`. Subscriptions are scoped
per-user via the `supabase_token` `sub` claim, and RLS gates what each
connection can observe. Both apps consume the Flow directly — SwiftUI
via `StateFlowObserver`, Compose via `collectAsState`.

### Outbox

Mutations that arrive while offline (or fail with a transient network
error) queue into SQLDelight (`PendingMutation.sq`,
`OutboxFailure.sq`). A worker drains the queue in FIFO order with
exponential backoff. Failures surface in the user-facing **Outbox**
screen — shared between operator and rider (#80) on both platforms —
so the user can inspect and retry without leaving the app.

POD capture (`PodCaptureScreen` / `PodCaptureView`, #82) bundles
signature + photo + OTP + GPS coordinates into one outbox-friendly
mutation. This is the highest-value offline path because rider runs
routinely traverse patchy coverage.

The backend honours the same idempotency guarantees that webhooks do
(every mutation is keyed on a client-issued idempotency token), so a
replay never double-applies.

---

## 8. Home greeting carousel

`shared/.../presentation/home/HomeGreeting.kt` defines a sealed class
with 25 prioritised variants (PR #95) across five buckets:

- **Urgent** — pending payments, expiring quotes
- **Status** — parcel arrived, BFM quoted, consolidation in transit
- **Engagement** — referral nudge, NPS prompt, FAQ tip
- **Onboarding** — first-shop guidance, address completion
- **Fallback** — generic time-of-day greeting

The carousel renders on the Home tab of both apps (#97 iOS, #98 Android).
Taps deep-link to the relevant detail screen (#99): invoice cards open
the invoice, ticket cards open the ticket reply, NPS opens the survey
sheet via `.task { onDone in ... }` (#100, #103). Seen markers are
persisted in `HomeGreetingSeenEntity` so the same greeting doesn't
resurface across launches (#101).

The **Pending Actions** card (#108) collapses multiple invoices into a
unified surface. PR #109 lifts `dashVm` and the consolidation observer
to the scaffold to prevent an Android Realtime crash when invoices
resolved mid-lifecycle.

---

## 9. Accessibility

- **Dynamic Type** wired across payment + scanner screens (PR #33),
  customer + operator screens (#34, 19 views), admin + agent + rider
  screens (#35, 12 views).
- **VoiceOver labels** added to icon-only buttons in #36 (slice F-9).
- Android equivalents ride on TalkBack via Compose `semantics`.
- `PasswordPolicy` surfaces live hints on sign-up (#91).
- Brand contrast — the iOS warehouse-card was tuned in #110, and the
  cream wordmark launch storyboard replaces the prior `BrandNavy` flash
  on cold-start (#23).

---

## 10. Theming

- **Brand Orange `#F5731A`** applied to iOS accent + gradient to match
  the Android baseline (PR #111).
- **Appearance toggle** (System / Light / Dark) under Account on both
  platforms (iOS earlier, Android #112).
- Android port of the real Thapsus logo asset (#89, #110).

---

## 11. Things that look weird but are intentional

- **SKIE 0.10.1 + Kotlin 2.1.10** are version-pinned together —
  upgrading either in isolation breaks the bridge. Multiple PRs (#49,
  #102) work around SKIE bridging quirks (default-param visibility,
  sealed-class nested-type syntax). Don't try to upgrade SKIE without
  matching the Kotlin pin in `libs.versions.toml`.
- **Gradle is excluded from Dependabot** (`.github/dependabot.yml`)
  because KMP iOS targets break Dependabot's Linux runners. The comment
  in the file documents the rationale. Bump Gradle deps manually.
- **`iosApp.xcodeproj` is still canonical**, `project.yml` (XcodeGen)
  is opt-in (#30). The migration plan is in `iosApp/XCODEGEN.md`. Don't
  delete the `.pbxproj` yet.
- **`Config.local.xcconfig` is gitignored**; only the `.example` is
  committed. The Xcode pre-build script reads `API_BASE_URL` from
  there and asserts it isn't the placeholder value at runtime (#51, #52).
- **CI builds Android only**, not iOS, for the Kotlin CodeQL leg (#43,
  #45). The Swift leg uses autobuild against `iosApp.xcodeproj` on a
  macOS runner. SARIF uploads to a workflow artifact (#44) rather than
  the Security tab because the repo is private and not on GHAS.
- **`build-shared-framework.sh` refuses JDK 24+** (#48). Pin to 17–23.
- **`Tables.WALLET`** was removed in #20 (parity with the backend's
  wallet → user_credits migration 028). Don't reintroduce wallet
  references; the credit ledger is the source of truth.
- **The OutboxScreen is shared between operator and rider** (#80) —
  same Kotlin VM, same Compose / SwiftUI surface, different role gates.
  When extending it, keep both roles in mind.

---

## 12. Product positioning (2026-05-13)

Buy-for-me ("Shop & ship") is the primary customer journey. Hero, tab
order, FAQ vocabulary, and email subject prefixes all lead with BFM
(iOS #62–#66, Android #67–#86). Standalone parcel forwarding remains
fully supported but is no longer the default landing surface. China
retailers were removed (iOS / KMP #57; Android #94); the platform is
UK-origin only.

---

## 13. Where to look next

- **`README.md`** — build instructions, repo layout, tech stack.
- **`docs/parity_audit.md`** — canonical webapp↔mobile feature-parity
  matrix.
- **`docs/system_audit_2026_05_09.md`** — cross-platform audit findings
  and their remediations.
- **`docs/ios_screens.md`** — screen-by-screen catalogue of the iOS app.
- **`docs/parcel_payment_audit_2026_05_03.md`** — deep-dive on the
  parcel ↔ payment lifecycle.
- **`server-patches/README.md`** — patches the companion Express
  backend needs in lockstep.
- **Backend repo:** [`Bmwarari2/swiftcargo-main`](https://github.com/Bmwarari2/swiftcargo-main) —
  Express 5 API, React webapp, Postgres migrations.

---

*Last updated 2026-05-15.*
