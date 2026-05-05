# Audit 2026-04-30 — execution progress

Tracking PR status against the audit findings. The full audit report is in conversation history (session of 2026-04-30); this file is the persistent execution ledger.

> **Convention**: each row is one PR. `iOS` PRs land on `Bmwarari2/thapsus-mobile` (base `main`); `server` PRs land on `Bmwarari2/Swiftcargo-main` (base `JS1`). When co-dependent, server PR merges + Railway redeploy first, then iOS PR.

## Sprint 0 — production blockers — **COMPLETE**

| # | Repo | Title | Status |
|---|------|-------|--------|
| S0-1 | server | `POST /api/auth/logout` + revoked_tokens | **PR #34 merged** |
| S0-2 | server | `POST /api/last-mile/runs`, `PATCH /api/consolidations/:id` | **already shipped** |
| S0-3 | server | privatise agent-invoices bucket + signed-URL endpoints | **PR #35 merged** |
| S0-4 | server | `/reset/*` AASA + reset-token route | open follow-up (cross-product) |
| S0-5 | iOS | replace `"operator"` + `"agent"` + `"rider"` literals | **PR #24 merged** |
| S0-6 | iOS | repoint 4 RLS-blocked writes to Express | **PR #39 merged** (bundled) |
| S0-7 | iOS | move tokens from NSUserDefaults to Keychain | **PR #26 merged** |
| S0-8 | iOS | call `/api/auth/logout` before clearing local state | **PR #39 merged** (bundled) |
| S0-9 | iOS | use signed URLs from server | **PR #39 merged** (bundled) |
| S0-10 | iOS | "Forgot password?" link + reset deep-link handler | **PR #39 merged** (iOS half; server AASA pending) |
| S0-11 | iOS | "Couldn't deliver" button + signature pad | **PR #28 merged** |

Three production-blocker fixes surfaced post-merge during PR #39's first build (SKIE Result<T> bridge, SwiftUI Section title+footer, K/N init-block ordering) — all landed on the same branch before merge. Documented as feedback memories.

## Sprint 1 — flow completeness — **COMPLETE**

All 16 items merged via the bundled PR #39 + the standalone PRs #24/#25/#26/#28/#29/#30/#31. See git history for the per-feature commits.

## Sprint 2 — quality & hardening — **COMPLETE**

| # | Repo | Title | Status |
|---|------|-------|--------|
| S2-1 | iOS | `chore/loose-numeric-dtos` | **PR #41 merged** |
| S2-2 | iOS | `chore/localize-customer-strings` | **PR #46 merged** — surface review only; view migration is post-translator follow-up |
| S2-3 | both | `feat/server-config-payload` | **server PR #40 + iOS PR #44 merged** — Railway env vars set 2026-04-30 |
| S2-4 | iOS | `chore/error-banners` | **PR #45 merged** — `ErrorBanner` + `InlineFieldError` primitives + 28-file sweep |
| S2-5 | DB | `feat/agent-realtime-publication` | **server PR #38 merged** + migration 008 applied |
| S2-6 | both | `perf/parcel-assign-single-patch` | **server PR #39 + iOS PR #43 merged** |
| S2-7 | DB/Auth | `chore/supabase-auth-hardening` | **server PR #37 merged** + migration 007 applied; **manual:** leaked-password protection dashboard toggle still pending |
| S2-8 | iOS | `chore/audit-config-secrets` | **verified clean** 2026-04-30 — `Config.local.xcconfig` correctly gitignored |

## Sprint 3 — feature completeness — **COMPLETE**

| # | Repo | Title | Status |
|---|------|-------|--------|
| S3-1 | iOS | public tracking timeline | **already shipped** — `PublicTrackingCard` in TrackingView |
| S3-2 | iOS | `KpiRepository.summary` founder tiles | **PR #47 merged** — Founder snapshot card on KPI dashboard |
| S3-3 | both | AML / DSAR / admin-logs / revenue admin tabs | **PR #48 merged** — AML inline in `AdminDashboardView`, three new admin views (`AdminRevenueView`, `AdminAuditLogsView`, `AdminDsarQueueView`) |
| S3-4 | both | NPS auto-prompt + cutoff banner | **already shipped** — `NpsAutoPromptModifier` + `CutoffBannerView` mounted on `CustomerDashboardView` + `CustomerHomeView` |

## Manual + cross-product follow-ups (not on the audit plan)

These are the items the audit did NOT track but are still open after the Sprint 0–3 push:

- **Webapp + Android insurance removal** — iOS stripped (PR #25); React webapp + Compose Android still need parity removal per `insurance_removed.md`.
- **Server AASA for `/reset/*`** — closes the deep-link half of S0-10 (iOS link + handler are in; server JSON manifest at `/.well-known/apple-app-site-association` needs the `/reset/*` route). Pairs with a `feat/password-reset-aasa` PR on Swiftcargo-main.
- **Promo code at order create** (deferred from S1-2) — server endpoint `/pricing-tiers/promotions/validate` exists; needs shared hook + iOS field.
- **Add/remove parcels post-create on existing dispatch runs** (deferred from S1-9) — server PATCH /runs/:id supports it via notes; needs operator UI.
- **APNs / Firebase credentials provisioning** — push notifications still blocked on Apple/Firebase signup per `project_state.md`.
- **Supabase leaked-password protection** — Auth → Providers → Email → Password Strength toggle. Manual.
- **S2-2 view migration** — `Localization.swift` keys are defined and `docs/localization_audit.md` lists per-view sweep status. Replace literals → `T(...)` after the Swahili translator delivers the SW pack.

## Notes

- Auto mode + explicit user authorisation drove the server/DB push. Migrations 007 + 008 applied to live Supabase 2026-04-30 after PR review. Railway env vars (`APP_WAREHOUSE_CODE`, `APP_SKU_PREFIX`, `APP_SUPPORT_WHATSAPP`, `APP_SUPPORT_EMAIL`, `APP_OTP_LENGTH`) set the same day.
- The previous PDF-upload debug arc (`agent_invoice_pdf_upload_debug.md`) is RESOLVED — folded into S0-3 / S0-9. See memory note.
- **S0-2 collapsed** into S0-6: server endpoints already existed; the iOS-side switch was the work.
