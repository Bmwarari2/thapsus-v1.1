# Feature audit — iOS · Android · Swiftcargo-main backend

**Date:** 2026-05-16
**Repos:**
- Backend: `~/Documents/PROJECTS/Swiftcargo-main` (Express + Postgres-on-Supabase, Railway-deployed; still the production backend until the Thapsus-webapp-and-backend cutover)
- Mobile: `~/Documents/PROJECTS/thapsus-v1.1` — `iosApp/` (SwiftUI, iOS 26 Liquid Glass) + `androidApp/` (Jetpack Compose)
- Shared KMP module owns 100% of business logic; this audit is purely about the **UI surface** that each client exposes vs the **HTTP surface** the backend offers.

**Method:** three parallel codebase enumerations (backend route files, iOS view files, Android Compose screens), then cross-referenced by domain.

---

## TL;DR

- **Feature scope is broadly at parity across iOS and Android.** The Android catch-up plan (Phases 0–5) is in, and the gap to iOS is now narrow.
- **One real parity gap with a customer/admin impact:** Android `AdminPaymentsScreen` is still a Phase 0 stub. The route exists, but the screen renders a placeholder — admin payment review is blocked on Android until a Phase 5.3-style rewrite lands.
- **A handful of smaller gaps** (label/manifest printing, AML risk queue surfacing, public unauth tracking, admin user detail drill-down, home greeting carousel) — listed in §4.
- **Several backend domains are not consumed by either mobile client** — promotions/promo codes, admin backups, manual FX override, SSE event stream, admin email-config and test-email endpoints, the legacy `/api/wallet/*` (410 Gone) and `/api/consolidation` V1 (deprecated). Most are intentionally web-only.
- **One contradiction to verify in code:** the Android `NewOrderScreen` audit notes an "insurance tier" input on the form, but `project_insurance_removed.md` says the customer-facing picker was stripped on Android in PR #94 (2026-05-13). One of the two is wrong; flagged in §5.

---

## 1. Coverage matrix

Legend: **✓** = wired UI consumes the backend domain · **stub** = route + scaffold exist but UI is placeholder · **—** = no UI for that surface on this client · **n/a** = not applicable (web-only, internal, etc.)

| # | Backend domain | Roles | iOS | Android | Notes |
|---|----------------|-------|-----|---------|-------|
| 1 | Auth — register/login/profile/password/reset/JWT exchange | All | ✓ | ✓ | iOS has explicit `ForgotPasswordView`; Android folds it into `SignInScreen` + `PasswordResetScreen`. Functional parity. |
| 2 | Buy-for-me (concierge) | Customer + Ops + Admin | ✓ | ✓ | iOS embeds quote cards into Tracking; Android keeps quotes on Shop only (per scope-singular rule). Both deliberate. |
| 3 | Parcel orders (pre-register) | Customer + Admin | ✓ | ✓ | |
| 4 | Tracking (auth + public) | Customer + Public | ✓ | partial | iOS has `PublicTrackingViewModel` for unauthenticated THP-prefix tracking. Android has no public-tracking surface in audit. **Gap.** |
| 5 | Payments — Stripe + M-Pesa + Lipana STK | Customer | ✓ | ✓ | Both fully wired post PR #76 (Stripe SDK on Android). Comments in `PayInvoiceScreen` claiming "SDK not wired" are stale. |
| 6 | Public payment (deep-link `/pay/:id`) | Public | ✓ | ✓ | |
| 7 | Pending payment review | Admin | ✓ | **stub** | Android `AdminPaymentsScreen` is Phase 0 placeholder ("Check back soon"). **Real gap.** |
| 8 | Credit / referral wallet | Customer | ✓ | ✓ | Both use `CreditCenter` post-wallet-rip. |
| 9 | Transactions ledger | Customer | ✓ | ✓ | |
| 10 | Referral program | Customer | ✓ | ✓ | |
| 11 | Customer consolidation (self-service) | Customer + Admin | ✓ | ✓ | |
| 12 | Consolidations V2 (operator) | Operator + Admin | ✓ | ✓ | Shared `ConsolidationDetailScreen` between ops + admin on Android — clean reuse. |
| 13 | Invoices archive | Customer | ✓ | ✓ | Realtime via Supabase channel on both. |
| 14 | Issue invoice | Admin | ✓ | ✓ | |
| 15 | Pricing calculation / FX | Customer + Admin | partial | partial | Both have customer quote UIs. Neither has an admin UI for HS-codes / customs tiers / electronics fees / manual FX override — managed via DB/SQL or web frontend. Probably fine. |
| 16 | Insurance policies + claims | Customer + Admin | n/a | n/a | Customer picker removed 2026-04-30 (web/iOS) and 2026-05-13 (Android). Backend routes remain operational for admin/historical use. Shared enum kept. See §5 for one inconsistency. |
| 17 | Promo codes / pricing tiers | Customer + Admin | — | — | No mobile UI for promotion validation; backend domain unused by mobile. Probably web-only. |
| 18 | Warehouse intake (operator) | Operator | ✓ | ✓ | Both have scanner-driven receive flow. |
| 19 | Hardware printing (labels + manifests) | Operator | ✓ | partial | iOS has explicit `LabelPrinter` (AirPrint) + `ManifestPrinter` + `ReceiveLabelSheet`. Android: `androidx.print` declared and `ClientTerminalScreen` mentions print calls (per PR #79), but real scope and parity unclear — flagged. |
| 20 | Last-mile runs + POD (rider) | Rider + Operator (dispatch) | ✓ | ✓ | Android uses raw `LocationManager` + `HttpURLConnection` upload (no-new-deps workaround); functionally equivalent. |
| 21 | Customs / clearing agent | Agent + Admin | ✓ | ✓ | |
| 22 | Agent invoices | Agent + Admin | ✓ | ✓ | |
| 23 | Tickets (support) | Customer + Admin | ✓ | ✓ | |
| 24 | NPS survey | Customer + Admin | ✓ | ✓ | iOS auto-prompt disabled per UX feedback; manual trigger via home carousel still works. Android exposes as bottom sheet. |
| 25 | DSAR (GDPR) | Customer + Admin | ✓ | ✓ | Both have customer form + admin queue. |
| 26 | AML flags | Admin | ✓ | — | iOS `AdminDashboardView` surfaces "AML risk queue"; Android `AdminConsoleScreen` doesn't expose one. **Gap.** |
| 27 | Admin user management | Admin | ✓ | partial | iOS has `AdminUsersView` + `AdminUserDetailView` (drill-down). Android audit shows `AdminUsersScreen` (list) but no detail screen — **likely gap**, worth verifying. |
| 28 | Admin orders | Admin | ✓ | ✓ | List + detail on both. |
| 29 | Admin revenue / KPI | Admin | ✓ | ✓ | iOS has separate `KPIDashboardView`; Android has `KPIDashboardScreen` on its own tab. Backend revenue UNIONs `transactions` + `payments` — both clients consume the corrected endpoint. |
| 30 | Audit logs | Admin | ✓ | ✓ | |
| 31 | Error logs | Admin | ✓ | ✓ | |
| 32 | Backups | Admin | — | — | Backend `/api/admin/backups` not surfaced on either client. Admin DBA-only — intentional. |
| 33 | Email config check | Admin | partial | — | iOS `AdminDashboardView` shows Gmail readiness card (client ID lengths, sender email). Android omits. Minor gap. |
| 34 | Test email endpoint | Admin | — | — | Dev helper; neither client. |
| 35 | Notifications (in-app inbox) | Customer + Admin | ✓ | ✓ | |
| 36 | Notification banner (transient toast) | All | ✓ | ✓ | |
| 37 | Retailers directory | Customer | ✓ | ✓ | Marquee on iOS, catalogue on Android — both wired. |
| 38 | App config / feature flags | All | likely | likely | Both clients are expected to hit `/api/app-config` at boot; not explicitly enumerated in screen audits because it's plumbing, not a screen. |
| 39 | Warehouse addresses | Customer | ✓ | ✓ | |
| 40 | Prohibited items search | Customer | ✓ | ✓ | Dual-source (server canonical + bundled `ProhibitedItemsCatalog.kt` fallback) — see `project_prohibited_items_catalogue.md`. |
| 41 | Exchange rates (read) | All | ✓ | ✓ | Quote calculators on both use server `/exchange/rates`. |
| 42 | SSE event stream (`/api/events`) | All authenticated | — | — | Neither mobile client consumes SSE; both use Supabase Realtime channels instead. Backend route is web-only. |
| 43 | Stripe webhook | n/a (server) | n/a | n/a | |
| 44 | Lipana M-Pesa webhook | n/a (server) | n/a | n/a | |
| 45 | Sitemap / robots / SEO | n/a | n/a | n/a | Web-only. |
| 46 | Legacy `/api/wallet/*` | — | — | — | Returns 410 Gone since mig 028. Correctly unused. |
| 47 | Legacy `/api/consolidation` V1 | — | — | — | Sunset 2026-05-23. Correctly unused. |
| 48 | Home greeting carousel / pending actions | Customer | ✓ | partial | iOS has `HomeGreetingCarousel` + `HomeGreetingNavigation` + `PendingActionsView`. Android has a `HomeScreen` + `PendingActionsScreen` but no carousel mechanism. Likely Phase 6 polish item. |
| 49 | Appearance / theme settings | All | ✓ | ✓ | |
| 50 | Ops settings | Admin | ✓ | partial | Android `OpsSettingsScreen` exists but per audit "some fields may be TBD". Worth verifying. |
| 51 | Client terminal (walk-in kiosk) | Operator | ✓ (`ClientTerminalView`) | ✓ (`ClientTerminalScreen`) | Both wired. iOS labels it a "debug/test surface"; Android describes it as a real walk-in kiosk. Behavioural parity worth double-checking. |

**Totals:** ~51 backend feature domains. iOS exposes ~43, Android exposes ~41 (with one stub). Most of the 8–10 backend-only domains are intentionally web/admin/internal-only.

---

## 2. iOS-only surfaces (not present on Android)

| Surface | iOS file | Why it matters |
|---------|----------|----------------|
| Public unauthenticated tracking | `PublicTrackingViewModel` — reads THP via Supabase RLS without login | Customers can paste a tracking number into the app without signing in. Android offers no equivalent. |
| Admin user detail drill-down | `AdminUserDetailView` | Android has the user list but no detail screen — admin can't edit role/risk notes from a phone. |
| AML risk queue surfacing | `AdminDashboardView` AML section | Compliance touchpoint. Backend domain works; Android admin can't see it. |
| Email config readiness check | `AdminDashboardView` Gmail card | Minor — admin can verify SMTP without leaving the dashboard. |
| Home greeting carousel + auto-prompts | `HomeGreetingCarousel`, `HomeGreetingNavigation`, `NpsAutoPromptModifier` | Customer engagement loop. Android `HomeScreen` is static-section based. |
| Forgot-password as separate screen | `ForgotPasswordView` | UX preference — Android folds it into sign-in. Functional parity. |
| Explicit hardware label/manifest printing | `LabelPrinter`, `ManifestPrinter`, `ReceiveLabelSheet`, `SkuScannerView` (with print) | Android has `androidx.print` declared and `ClientTerminalScreen` calls into print per PR #79, but the dedicated "print a SKU label on receive" sheet flow needs verification. |

## 3. Android-only surfaces (not present on iOS)

None of substance. The Android audit lists no domain that lacks an iOS counterpart. Where iOS is the design lead, Android trails; nothing flipped the other way.

(The Android audit notes "ML Kit barcode scanning for recipient ID on POD". iOS scanner uses native `AVFoundation` so this is implementation detail, not a missing feature.)

## 4. Backend-only surfaces (neither client consumes)

These are intentional in most cases — flagging so we have a complete picture:

- **`/api/admin/backups`** — DBA-only, no mobile UI ever planned.
- **`/api/admin/test-email`**, **`/api/admin/email-config`** — dev helpers. iOS surfaces config-readiness; neither surfaces the test-send.
- **`/api/admin/exchange-rates` (PUT manual override + refresh trigger)** — manual FX override is web/admin only.
- **`/api/events` (SSE)** — both mobile clients use Supabase Realtime instead. Either deprecate SSE or document it as web-only.
- **`/api/pricing/*` admin mutations (HS-codes, customs tiers, electronics fees, settings)** — managed via DB/SQL or web frontend.
- **`/api/pricing-tiers/*` (promotions, fees)** — promo codes have no mobile UI. If promos are part of the product, this is a gap; if web-only, fine.
- **`/api/insurance/quote` + policies + claims** — customer picker removed by product decision (2026-04-30 web/iOS, 2026-05-13 Android). Backend remains operational for admin/historical use only.
- **Legacy `/api/wallet/*`** — 410 Gone since mig 028. Correctly unused.
- **Legacy `/api/consolidation` V1** — sunset 2026-05-23. Correctly unused.

## 5. Findings worth a follow-up

### F1 — Android `AdminPaymentsScreen` is a stub (Phase 5.3 work)
**Severity:** real gap. Admin cannot approve pending M-Pesa payments from an Android device. iOS has the queue. This is the only post-Phase-5 feature gap noted in both audits.
**Action:** Schedule a P5.4-equivalent PR. Surface `AdminApprovePaymentScreen` + integrate `/admin/payments/pending`, `/admin/payments/:id/approve`, `/admin/payments/:id/reject`.

### F2 — Conflict: Android `NewOrderScreen` "insurance tier" input
The Android audit notes the form collects "insurance tier" as a field. `project_insurance_removed.md` says PR #94 stripped the customer picker on Android on 2026-05-13. Either the audit picked up a stale shared-VM parameter that isn't surfaced as a UI control (most likely — the shared `InsuranceTier` enum is kept and clients pass `STANDARD`), or the picker survived the cleanup.
**Action:** read `androidApp/.../NewOrderScreen.kt` and confirm there is no segmented control / radio for insurance tier. If there is, remove it.

### F3 — Android lacks an unauthenticated public-tracking surface
**Severity:** medium. iOS lets a customer paste a THP into a "track without signing in" search. Android only exposes tracking after login. Public tracking is a low-friction acquisition surface and the backend route (`/api/tracking/:trackingNumber`) supports it.
**Action:** add a public-tracking screen on the Android sign-in page, mirroring iOS.

### F4 — Android `AdminUsersScreen` has no detail drill-down
**Severity:** medium. The list exists, but per the audit there's no `AdminUserDetailScreen` to view/edit role, risk notes, AML flags, or to reset password/resend welcome. iOS has `AdminUserDetailView`.
**Action:** verify in code (audit may have missed it), and if absent, add a P5-style PR.

### F5 — Android admin scaffold doesn't expose AML queue
**Severity:** medium. iOS `AdminDashboardView` shows AML risk queue cards. Android `AdminConsoleScreen` doesn't enumerate one in the audit. Backend `/api/admin/aml-flags` is functional.
**Action:** add an `AdminAmlQueueScreen` and link it from the console.

### F6 — Android operator label/manifest printing parity
**Severity:** low-medium. iOS has explicit `ReceiveLabelSheet` + `LabelPrinter` AirPrint sheet on receive. Android: per PR #79, `ClientTerminalScreen` calls `LabelPrinter.print` and `ManifestPrinter.print`, but the receive-flow printing UX flow may differ. Worth a side-by-side run.
**Action:** test on an Android device with a print service installed; compare to iOS receive flow.

### F7 — Android home lacks the greeting carousel + auto-prompts
**Severity:** low (Phase 6 polish territory). iOS has a `HomeGreetingCarousel` driving customer re-engagement. Android `HomeScreen` shows static sections.
**Action:** group with Phase 6 polish if/when scheduled.

### F8 — SSE endpoint unused on mobile
**Severity:** informational. `/api/events` exists but neither client uses it (both use Supabase Realtime). If web is the only consumer, document it. If it's truly unused everywhere, schedule for deletion.

### F9 — Stale comment in Android `PayInvoiceScreen`
The Android audit noted code comments claiming "Stripe SDK not yet wired into Gradle deps" — these contradict reality (SDK was added in PR #76 and PaymentSheet is live). Cosmetic cleanup, but the kind of stale claim that misleads future readers.
**Action:** delete the comment when next touching that file.

### F10 — Android `DispatchScreen` and `OpsSettingsScreen` have placeholder fields
**Severity:** low. The audit calls these "wired structure, scope TBD". Likely deliberate Phase 6 work, but worth listing the missing fields so we can size the work.
**Action:** diff against iOS equivalents and produce a sub-issue.

---

## 6. Risk-ordered punch list

If we close these in order, Android reaches functional parity with iOS:

1. **F1** — Build the Android admin payment-approval screen. (P5.4)
2. **F4** — Add Android `AdminUserDetailScreen` (or confirm it exists and audit missed it).
3. **F5** — Add Android AML queue surface.
4. **F3** — Add Android public-tracking entry on sign-in.
5. **F2** — Verify and (if needed) remove residual insurance-tier input on `NewOrderScreen`.
6. **F6** — Verify Android receive-flow label printing end-to-end.
7. **F7, F10** — Group into Phase 6 (visual polish + small admin-screen gaps).
8. **F8, F9** — Documentation / cleanup, no user impact.

Item 1 is the only one with a customer-visible impact (an admin on Android can't approve a real customer's M-Pesa payment). Items 2–4 are operator/admin convenience parity. The rest are polish.

---

## 7. Methodology notes

- Three Explore agents ran in parallel — one per codebase — to keep the audit fresh and reduce cross-contamination.
- The auditors enumerated UI screens / route files only; they did NOT speculate about cross-platform parity (that synthesis is this report).
- Total surface counted: backend ~150 endpoints across 34 route files; iOS ~65 primary views; Android ~61 screens across 41 routes.
- Memory snapshots were used as context only; ground truth was the agents' direct reads of the current code on `main` of each repo.
- This audit is point-in-time. Feature parity should be re-checked after each phase of Android work lands.

---

## Appendix — domain → route file → screen quick lookup

(Only the columns that have entries on all three sides — for cross-debugging.)

| Domain | Backend file | iOS view | Android screen |
|--------|--------------|----------|----------------|
| Auth | `routes/auth.js` | `SignInView`, `PasswordResetView` | `SignInScreen`, `PasswordResetScreen` |
| BFM | `routes/buyForMe.js` | `BuyForMeView`, `OpsBuyForMeQueueView`, `AdminCreateBuyForMeView` | `BuyForMeScreen`, `OpsBuyForMeQueueScreen`, `AdminCreateBuyForMeScreen` |
| Orders | `routes/orders.js` | `NewOrderView`, `ParcelDetailView` | `NewOrderScreen`, `ParcelDetailScreen` |
| Tracking | `routes/tracking.js` | `TrackingView`, `PublicTrackingViewModel` | `TrackingScreen` |
| Payments | `routes/payments.js`, `payment.js` | `PayInvoiceView`, `MpesaSubmitSheet`, `LipanaStkSheet`, `PublicPaymentView` | `PayInvoiceScreen`, `MpesaSubmitBottomSheet`, `LipanaPhoneSheet`, `PublicPaymentScreen` |
| Payment review | `routes/adminPayments.js` | `AdminPaymentsView` | `AdminPaymentsScreen` (stub) |
| Credit | `routes/payments.js` (`/me/credit`) | `CreditCenterView`, `TransactionsView` | `CreditCenterScreen`, `TransactionsScreen` |
| Referral | `routes/referral.js` | `ReferralView` | `ReferralScreen` |
| Consolidation V2 | `routes/consolidationsV2.js` | `ConsolidationListView`, `ConsolidationDetailView` | `ConsolidationListScreen`, `ConsolidationDetailScreen` |
| Customer consolidation | `routes/customerConsolidations.js` | `CustomerConsolidationView`, `CustomerInvoicesView` | `CustomerConsolidationsScreen`, `CustomerInvoicesScreen` |
| Last-mile | `routes/lastMile.js` | `RiderRunView`, `RunStopListView`, `PodCaptureView` | `RiderRunsScreen`, `RunStopListScreen`, `PodCaptureScreen` |
| Customs | `routes/customs.js` | `CustomsListView` | `CustomsScreen` |
| Agent invoices | `routes/agentInvoices.js` | `AgentInvoicesView` | `AgentInvoicesScreen` |
| Tickets | `routes/tickets.js` | `TicketsListView`, `TicketDetailView` | `TicketsScreen`, `TicketDetailScreen` |
| DSAR | `routes/dsar.js` | `DsarView`, `AdminDsarQueueView` | `DsarScreen`, `AdminDsarQueueScreen` |
| NPS | `routes/nps.js` | `NpsSurveyView` | `NpsSurveyBottomSheet` |
| Prohibited | `routes/prohibited.js` | `ProhibitedSearchView` | `ProhibitedSearchScreen` |
| Notifications | `routes/notifications.js` | `NotificationInboxView`, `NotificationBannerView` | `NotificationInboxScreen`, `NotificationBanner` |
| Warehouse addresses | `routes/warehouse.js` | `WarehouseAddressView` | `WarehouseAddressScreen` |
| Retailers | `routes/retailers.js` | retailer marquee inside `BuyForMeView` | retailer catalogue inside `BuyForMeScreen` |
| Exchange rates | `routes/exchange.js` | `QuoteCalculatorView` | `QuoteScreen` |
| Admin users | `routes/admin.js` | `AdminUsersView`, `AdminUserDetailView` | `AdminUsersScreen` (no detail per audit) |
| Admin orders | `routes/admin.js` | `AdminOrdersView`, `AdminOrderDetailView` | `AdminOrdersScreen`, `AdminOrderDetailScreen` |
| Admin revenue / KPI | `routes/admin.js` | `KPIDashboardView`, `AdminRevenueView` | `KPIDashboardScreen`, `AdminRevenueScreen` |
| Admin logs | `routes/admin.js` | `AdminAuditLogsView`, `AdminErrorLogsView` | `AdminAuditLogsScreen`, `AdminErrorLogsScreen` |
| AML flags | `routes/amlFlags.js` | `AdminDashboardView` (AML section) | (no screen) |
