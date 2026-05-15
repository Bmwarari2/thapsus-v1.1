# Thapsus Cargo — Native Mobile Apps (iOS + Android)

UK → Kenya parcel forwarding and **Buy-for-me** (concierge "Shop & ship") logistics, shipped as native iOS (SwiftUI / iOS 26 Liquid Glass) and Android (Jetpack Compose) apps over a shared Kotlin Multiplatform core.

The backend Express API + React webapp live in [`Bmwarari2/swiftcargo-main`](https://github.com/Bmwarari2/swiftcargo-main) (private).

> **Product positioning (2026-05-13):** Buy-for-me is the primary customer journey across every surface. Standalone parcel forwarding remains supported but is no longer the default landing tab. China retailers were removed in 2026-05-11; the platform is UK-origin only.

## Repo layout

```
thapsus-mobile/
├── shared/          # Kotlin Multiplatform core (Android + iOS targets)
│   └── src/
│       ├── commonMain/     # 35+ DTOs, 25 repositories, 40+ StateFlow VMs, pricing, prohibited catalog
│       ├── androidMain/    # platform actuals (SQLDelight driver, Ktor engine)
│       └── iosMain/        # platform actuals + SKIE bridging
├── iosApp/          # SwiftUI app (iOS 26 Liquid Glass)
│   ├── iosApp/         # Swift sources — 60+ feature screens
│   ├── Configuration/  # Config.xcconfig + .local.xcconfig overrides
│   ├── Scripts/        # build-shared-framework.sh (Xcode pre-build hook)
│   ├── iosApp.xcodeproj/
│   └── project.yml     # XcodeGen scaffold (opt-in; .pbxproj is still canonical)
├── androidApp/      # Jetpack Compose app
│   └── src/main/kotlin/com/thapsus/cargo/android/
│       ├── ui/             # role-based screens: admin/, agent/, auth/, customer/, operator/, rider/
│       ├── hardware/       # CameraX + ML Kit barcode
│       └── nav/            # nav graph + role routing
├── server-patches/  # SQL + setup notes for the companion Express backend
├── docs/            # audits, parity matrix, screen catalogue, user guide PDF
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Tech stack

| Layer | Tech |
| --- | --- |
| Shared core | Kotlin **2.1.10**, Coroutines 1.10.1, kotlinx-serialization 1.7.3, Ktor 3.1.2, Supabase Kotlin BOM 3.1.4 (Auth/Postgrest/Storage/Realtime/Functions), SQLDelight 2.0.2, Koin 4.0.0, Turbine 1.2.0 |
| iOS | SwiftUI · iOS 26 · Swift 6 (strict concurrency complete) · SKIE 0.10.1 for KMP→Swift bridging · Stripe iOS SDK (`StripePaymentSheet` SPM ≥ 25.13) |
| Android | Jetpack Compose (BOM 2024.10.01) · minSdk 26 / compileSdk 34 · navigation-compose · CameraX 1.4 + ML Kit barcode-scanning 17.3 · Stripe Android SDK 20.52 · Koin Android |
| Build | Gradle 8 · AGP 8.13.2 · JDK 17 · Xcode 26 |
| Backend (separate repo) | Express 5 on Railway · Postgres on Supabase · see [`Bmwarari2/swiftcargo-main`](https://github.com/Bmwarari2/swiftcargo-main) |

The iOS app uses [SKIE](https://skie.touchlab.co/) to generate idiomatic Swift wrappers from KMP — sealed classes become `enum`s with associated values, `StateFlow`s become `AsyncStream`s, suspend funs become `async throws`. A few PRs (#49, #102) work around SKIE bridging quirks; see commits for the patterns.

## Roles & tab navigation

Both apps drive a role-based `TabView` (iOS `RootTabView.swift`) / Compose root scaffold (Android `RootScreen.kt`). Five roles, distinct top-level tabs:

| Role | Tabs |
| --- | --- |
| **customer** | Home · Shop (BFM) · Activity · Quote · Account |
| **operator** | BFM Queue · Receive · Consols · Dispatch · Account |
| **clearing_agent** | Customs · Invoices · Account |
| **rider** | Today · Outbox · Account |
| **admin** | Console · KPI · Customer · Shipping · Account |

The Home tab carries a prioritised greeting carousel (25 variants across Urgent / Status / Engagement / Onboarding / Fallback buckets — see PR #95) plus a unified Pending Actions card that collapses invoice-due and quoted-BFM cards (PR #108).

## Building

Requires:
- **JDK 17–23** (24+ refused by `iosApp/Scripts/build-shared-framework.sh`; install via `brew install --cask temurin@17`)
- **Xcode 26** (for iOS)
- **Android Studio Hedgehog or newer** (for Android)

### Configure environment

The shared core needs Supabase + API endpoints at build time.

**iOS** — copy the local override template and fill in real values:

```bash
cp iosApp/Configuration/Config.local.xcconfig.example iosApp/Configuration/Config.local.xcconfig
# Edit:
#   SUPABASE_URL     = https://<ref>.supabase.co
#   SUPABASE_ANON_KEY= eyJ…
#   API_BASE_URL     = https://thapsus.uk/api
#   TEAM_ID          = <your Apple Team ID>
#   APP_BUNDLE_ID    = uk.thapsus.cargo
```

**Android** — add to `local.properties` (auto-loaded by Gradle, never commit):

```properties
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_ANON_KEY=eyJ…
API_BASE_URL=https://thapsus.uk/api
```

If the values are missing, both apps assert at start-up rather than silently pointing at a placeholder host.

### iOS

```bash
# Generate the KMP framework first (Xcode's pre-build phase does this automatically,
# but you can drive it from the CLI for CI / sanity checks):
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Open the Xcode project and Cmd-R
open iosApp/iosApp.xcodeproj
```

The framework artifact lands at `shared/build/bin/iosSimulatorArm64/debugFramework/ThapsusShared.framework`. The Xcode `Run Build Script` phase invokes `iosApp/Scripts/build-shared-framework.sh` for every build configuration / arch.

XcodeGen support exists (`iosApp/project.yml`) but `.xcodeproj` is still the canonical source — see [`iosApp/XCODEGEN.md`](./iosApp/XCODEGEN.md) for the migration plan introduced in PR #30.

### Android

```bash
./gradlew :androidApp:assembleDebug                # produces app-debug.apk (~46 MB)
./gradlew :androidApp:installDebug                 # installs onto a connected device / running emulator
```

Open the project root in Android Studio for the full development experience (Compose preview, Logcat, profiler).

### Shared module tests

```bash
./gradlew :shared:iosSimulatorArm64Test            # runs commonTest on iOS sim
./gradlew :shared:testDebugUnitTest                # JVM target
```

`commonTest/` covers `QuoteEngine`, `VolumetricWeightCalculator`, `Money` arithmetic, `PasswordPolicy`, and the prohibited-items catalog.

## Architecture summary

Layered, offline-first, single source of truth:

```
 data/dto         → wire shapes mirroring Supabase + Express row schemas
 data/remote      → SupabaseClientFactory + Ktor ThapsusApiClient + RealtimeSync
 data/local       → SQLDelight cache + outbox (PendingMutation, OutboxFailure)
 data/repository  → 25 repository facades — single read/write API per domain
 domain/          → Money, ParcelDimensions, UserRole, QuoteEngine, PasswordPolicy, ProhibitedItemsCatalog
 presentation/    → 40+ StateFlow VMs — dashboard, intake, rider, quote, payments, admin, KPIs
 di/ThapsusModule → Koin DI module wiring all of the above
 ThapsusSdk       → iOS-facing façade; entry point .start(supabaseUrl, anonKey, apiBaseUrl, driverFactory, secureSettings)
```

From there, **iOS** consumes via SKIE-generated Swift wrappers + `StateFlowObserver` bridging into `@Observable`. **Android** uses Koin Android + `collectAsState` directly. Both apps render through their respective platform UI toolkits with shared `Brand` colours (orange `#F5731A`) and an in-app appearance toggle (System/Light/Dark) reachable from the Account hub.

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the full picture.

## Payments

| Provider | iOS | Android | Backend route |
| --- | --- | --- | --- |
| **Stripe (cards)** | `StripePaymentSheet` via `PayInvoiceView` | `PayInvoiceScreen` (Stripe Android 20.52, PR #76) | `/api/payments/stripe/intent` + raw-body webhook |
| **M-Pesa Lipana STK Push** | `LipanaStkSheet` three-stage glass sheet (PR #17) | `PayInvoiceScreen` live (PR #73) | `/api/payments/lipana/initiate` + HMAC-verified webhook |

The payment flow uses an outbox-friendly orchestration: the backend mints an intent, the app drives the provider sheet, and a webhook flips parcel state + credit ledger on confirmation. The customer sees a full-screen confirmation overlay (iOS PR #14, Android equivalent) rather than a missable inline banner. Both providers converge on the same backend `markPaymentPaid` code path so post-payment side-effects (status flip, receipt email, credit ledger) run identically.

## Quote calculator

The shared `QuoteEngine` (`shared/.../domain/pricing/QuoteEngine.kt`) implements the **six-knob pricing model** introduced server-side in migration 051 (PR #46):

- `pricing_settings` — global base rates, FX margins
- `customs_tiers` — weight-band lookup
- `hs_code_tiers` — HS-code-driven multipliers
- `electronics_fees` — electronics surcharge
- Volumetric: `max(actual_kg, L·W·H / 6000)`
- FX: refreshed per quote (PR #60)

**Currency convention:** customer surfaces render KES (server-side FX for parity); operator/admin surfaces render GBP. Both apps zero declared-value by default in the public Quote calculator (parity with web, PRs #58–#59) and hide the customs estimate (KRA charges on clearance, PR #59).

Weight cap raised from 80 kg to 1000 kg in PR #55.

## Realtime + offline

The shared `RealtimeSync` orchestrates Supabase Realtime channels for `packages`, `consolidations`, `customer_consolidations`, and `notifications` — per-user filtering happens via the Supabase JWT's `sub` claim under RLS. Each subscription is exposed as a `Flow` that both apps observe directly.

Offline mutations queue into the SQLDelight outbox (`PendingMutation.sq`). On reconnect a worker drains the queue in order, retrying with exponential backoff. The shared **Outbox screen** (iOS `OutboxView`, Android `OutboxScreen`) lets operators and riders inspect and retry failures — critical for rider POD captures in patchy coverage. POD capture (`PodCaptureScreen` / `PodCaptureView`, PR #82) bundles signature + photo + OTP + GPS into one outbox-friendly mutation.

## Accessibility

Dynamic Type is wired across all customer + operator + admin + agent + rider surfaces (PRs #33–#35). VoiceOver labels were added to icon-only buttons in PR #36. The Android equivalents ride on TalkBack via Compose `semantics`. The shared `PasswordPolicy` (PR #91) surfaces live UI hints (≥8 chars, ≥1 letter, ≥1 number — NIST SP 800-63B) on both platforms' sign-up forms.

## Companion backend (`swiftcargo-main`)

Both apps consume the Express 5 API at `API_BASE_URL`. The repo's [`server-patches/`](./server-patches) directory carries patches the backend repo needs in lockstep with mobile releases:

- `routes/auth.patch.md` — adds Supabase JWT minting on `/login`+`/register`, plus the standalone `POST /api/auth/supabase-token` endpoint.
- `routes/admin_create_order_validation.patch.md`, `orders_create_email.patch.md` — admin/order surface tweaks.
- `database/migrations/*.sql` — 31 migrations (RLS lockdown v1+v2, realtime publication wiring, payments & credits, prohibited-items seed, pricing-settings, NPS invitations).
- `utils/supabaseJwt.js` — JWT minting helper to drop into the backend's `utils/`.
- `PUSH_NOTIFICATIONS_SETUP.md` — setup notes for transactional push.

The `server-patches/README.md` describes the apply order. When a public API contract changes here, the DTOs in `shared/src/commonMain/kotlin/com/thapsus/cargo/data/dto/` must move in lockstep.

## CI / dependency management

- **`.github/workflows/codeql.yml`** — CodeQL SAST, matrix over **swift** and **java-kotlin** languages. Runs on PR + push to `main`, plus Monday 06:00 UTC cron. Builds on `macos-15` (needs Xcode). Swift uses autobuild; Kotlin runs `./gradlew :androidApp:assembleDebug` in manual mode. `security-extended` query pack. SARIF uploaded as a workflow artifact (this is a private repo without GHAS).
- **`.github/dependabot.yml`** — GitHub Actions updates only (weekly). Gradle is intentionally excluded — KMP iOS targets break Dependabot's Linux runners; the rationale is captured in the file's comments. SPM lives in the `.xcodeproj`, not a `Package.swift`, so it's also excluded.
- There is no test CI workflow yet; tests are run locally via `./gradlew`.

## Docs index ([`docs/`](./docs))

- `parity_audit.md` — canonical webapp↔mobile feature-parity matrix.
- `system_audit_2026_05_09.md` — cross-platform audit (iOS + web + Supabase): 3 High / 6 Medium / 4 Low findings, most now remediated.
- `parcel_payment_audit_2026_05_03.md` — deep-dive on the parcel ↔ payment lifecycle.
- `e2e_manual_test_plan_2026_05_04.md` — end-to-end manual test plan.
- `ios_screens.md` — screen-by-screen catalogue of the iOS app.
- `clearing_agent_debug_plan.md` — the clearing-agent feature debug plan.
- `localization_audit.md` — strings inventory and l10n posture.
- `audit_2026_04_30_progress.md` — audit follow-up tracker.
- `_audit_workspace/db_schema_snapshot.md` — database schema as-of audit.
- `_audit_workspace/webapp_routes.md` — enumeration of webapp routes for parity checks.
- `guide/Thapsus_Cargo_User_Guide.pdf` — customer-facing user guide.

## Recent highlights

- **Buy-for-me pivot** (2026-05-13) — BFM is the primary CTA on every role's home/dashboard, and the operator console lands on the BFM queue (iOS #62–#66, Android #67–#86).
- **Android parity roadmap complete** (Phase 0–5, 2026-05-13) — Customer + Operator + Rider + Admin surfaces all shipped (PRs #67–#86).
- **Home greeting carousel** (2026-05-14–15) — prioritised contextual greetings on Home, with deep links to invoice / ticket / NPS sheets (#95–#104).
- **Pending Actions card** (PR #108) — collapses multiple invoices into a unified surface that's resilient to Realtime mid-lifecycle (PR #109 fixes Android crash).
- **Theming parity** — Brand Orange `#F5731A` adopted on iOS accent + gradient (PR #111); Android appearance toggle (System/Light/Dark) under Account, parity with iOS (PR #112).
- **Sign-up parity** — T&Cs + Privacy checkbox, name/phone/country fields, password policy with live hints on both platforms (iOS #31, Android #90–#91).
- **UK-only** — China market stripped from KMP + iOS (PR #57) and Android `NewOrderScreen` (PR #94).

See the PR history on `main` for the full audit-driven workstream (W4–W6, F-6 through F-12, H-4) and the iOS/Android parity tracker.

## License

Proprietary. Thapsus Cargo team.
