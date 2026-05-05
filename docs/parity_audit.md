# Webapp ↔ iOS ↔ Supabase parity audit

**Generated:** 2026-04-28
**Webapp:** `Bmwarari2/Swiftcargo-main` @ `JS1` (commit `a6302df`)
**iOS:** `/Users/mrwanderi/Documents/thapsus-mobile` (no git)
**Supabase project:** `zzdfxsfuhosuqvsugtfd` (eu-north-1)

Total surface area inventoried:
- Webapp: 27 route files mounted under `/api/*`, ~134 endpoints.
- iOS: 19 repositories, 28 DTO files, 40+ SwiftUI views, 1 SDK entry (`ThapsusSdk.kt`).
- React: 35 page components + ~10 cross-cutting components.
- DB: 39 public tables, 4 in `supabase_realtime`, RLS enabled on 1 (`exchange_rates`, with **zero** policies).

Scratch workspace: `docs/_audit_workspace/` (route + iOS + DB snapshots — not part of the audit, retained for follow-up phases).

Status legend: ✅ matches · ⚠ DTO drift · ✗ missing on iOS · ✗web missing on web · ☠ broken (server bug, schema gap, or unauthenticated path that should have been protected).

---

## Cross-cutting findings (read these first)

### F1 — `LooseNumeric` hardening is incomplete (Phase 2 driver)
Only **`AdminDtos.kt`** and **`AdminExtraDto.kt`** currently use the tolerant `LooseInt/LooseLong/LooseDouble` serializers from `data/dto/LooseNumericSerializers.kt`. The `/admin/stats` crash that motivated the pattern is the same shape that occurs in 20+ other endpoints — every one returns server-side aggregates (COUNT/SUM/AVG/COUNT FILTER/BOOL_AND) that pg-node returns as strings or null. Endpoints that need the same hardening:

- `GET /api/admin/users/:id` — `referralStats.{total,completed,pending,total_earned}`
- `GET /api/admin/referrals/stats` — stats + top_referrers
- `GET /api/admin/users` and `GET /api/admin/orders` — `pagination.total`
- `GET /api/orders` — `pagination.total`
- `GET /api/notifications` — `unread`
- `GET /api/referral` — `statistics.{total_referrals,…,total_earned}`; `referred_users[].orders_count`
- `GET /api/referral/history` — `pagination.total`
- `GET /api/kpi` — every field on the kpi object (kg/parcels/percentages)
- `GET /api/kpi/marketing` — `utm[].signups`, `retention_90d.{cohort_size,repeat_in_90d,pct}`
- `GET /api/nps/summary` — every field on summary
- `GET /api/ops/today` — every field on today object
- `GET /api/consolidation` — `total_weight_kg`
- `GET /api/consolidation/requests` — `packageCount` + aggregated booleans
- `GET /api/consolidations/current` — `total_kg`, `total_parcels`
- `GET /api/wallet` — `balance` + `recent_transactions[].amount`
- `GET /api/wallet/transactions` — `pagination.total`
- `GET /api/tracking/user/packages` — `pagination.total`
- `GET /api/admin/error-logs` and `…/error-logs/stats` — pagination + counts
- `POST /api/ops/parcels/:id/receive` — `weight_kg/volumetric_kg/chargeable_kg` (recomputed)
- `POST /api/consolidations/:id/manifest` — `manifest.{parcels_count,total_kg,total_declared_value_gbp}`

### F2 — iOS has 5 PostgREST direct-write paths that bypass Express
Each of these reaches Supabase via the API key without hitting the Express layer. None of them fire the corresponding push events, emails, or aggregate updates. All are under `iOS repo+vm+view → DB` in the per-domain table, but listed here for triage.

| iOS site | Verb | Table | Webapp counterpart | Action |
|---|---|---|---|---|
| `PackageRepository.upsert` (called by `ParcelPreRegViewModel`) | UPSERT | `packages` | `POST /api/orders` (which itself INSERTs `orders`+`packages` and pushes `order_update`+`admin_stats`) | **Phase 4 priority #1** — the orders-routing fix. Drop the upsert; route via `OrdersRepository.createOrder` per `orders_routing_followup.md`. |
| `PackageRepository.updateStatus` | UPDATE | `packages` | None — there is no Express endpoint for customer-driven status changes (admin-only via `PUT /tracking/:id/status`). | Confirm whether iOS should ever call this. If not, delete the method. |
| `ConsolidationRepository.lock` | UPDATE | `consolidations` | None — operator path is `POST /api/consolidations` + `PATCH /api/consolidations/:id`. | Repoint at `PATCH /api/consolidations/:id` (operator-only). |
| `CustomsRepository.submitEntry` | UPSERT | `customs_entries` | `POST /api/customs/entries` and `PATCH /api/customs/entries/:id`. | Repoint at the Express endpoints. |
| `LastMileRepository.flushOutbox` | INSERT | `pod_events` | None — POD capture is not exposed via Express in JS1. | Acceptable for now — this is the offline-safe outbox pattern. Add an Express endpoint when push notifications + online POD capture are wired (post-Phase 4). |

### F3 — Realtime publication gap
`supabase_realtime` currently publishes `consolidations`, `packages`, `transactions`, `wallet`. **Missing:** `notifications`, `tickets`, `ticket_messages`, `buy_for_me_orders`, `aml_flags`. Live updates for the customer support thread (Phase 4 priority #6 banners) and admin AML queue depend on these.

### F4 — `exchange_rates` has RLS enabled but **zero policies**
`pg_tables.rowsecurity = true`, `pg_policies` count = 0. Anonymous and authenticated PostgREST reads of `exchange_rates` return empty results. The webapp `GET /api/exchange/rates` falls back to `DEFAULT_RATES` silently — the iOS Ops Settings exchange-rate editor (Phase 4 priority #5) needs the table to be readable. Fix: either disable RLS on `exchange_rates` (if all reads go through Express anyway) or add a SELECT policy for `authenticated` and an UPDATE policy gated to `service_role`/admin claims.

### F5 — Two server endpoints stage edits the iOS app cannot use
- `POST /api/orders` does **not** call `sendOrderCreatedEmail`. The patch in `server-patches/routes/orders_create_email.patch.md` exists locally but has not been applied to JS1. Without it, the Phase 4 priority #1 fix only delivers half its value (admin counts move; customer email still doesn't fire).
- `PUT /api/admin/orders/:id/edit` does call `sendOrderUpdatedEmail`. The customer-side iOS detail screen does not yet display the recomputed `cost_breakdown` returned from this endpoint.

### F6 — Pricing schema is sane but iOS `PricingTierDto` is the only DTO using the tolerant enum pattern
`PricingChannelSerializer` accepts any casing and falls back. Other enum-shaped strings on the wire — `orders.status`, `packages.status`, `tickets.status`, `tickets.priority`, `ticket_messages` (no enum), `agent_invoices.status` (PG enum), `dsar_requests.{type,status}` (PG enum), `consolidations.status` (PG enum), `customs_entries.status` (PG enum), `tudor_invoices.status` (PG enum), `pvoc_documents.type` (PG enum), `last_mile_runs.status` (PG enum), `insurance_policies.{tier,status}` — should not crash if the server returns a value the iOS DTO doesn't know about. Today they're typed as plain `String` in DTOs (no parse), which is safe; if any DTO promotes them to a typed Kotlin enum, follow the `PricingChannelSerializer` precedent.

### F7 — Currency duality is everywhere
Every monetary table carries both an integer `*_pence`/`*_cents` column (legacy) and a `real`/`numeric` major-unit column (current). The webapp reads the major-unit column. The iOS `Money` domain stores pence and `QuoteEngine` bridges. **Don't reintroduce `*_pence` columns to DTOs.** Tables with both columns: `agent_invoices`, `customs_entries`, `fees`, `insurance_policies`, `parcel_items`, `promotions`, `transactions`, `tudor_invoices`. `packages.declared_value_gbp_pence` is the lone holdout (no major-unit twin) and is `bigint NOT NULL` — anything that writes a new packages row must supply it.

### F8 — RLS is off for everything except `exchange_rates`
Direct PostgREST reads/writes succeed unconditionally with the project anon key. This is why F2 has been silent in production. It also means: for Phase 3, when we add tables to `supabase_realtime`, we should also add SELECT policies + enable RLS on those tables before they go live, otherwise any anon caller can replay the customer's notification stream.

### F9 — Webapp routes that the previous agent missed (now captured)
The first inventory pass under-enumerated four files. Fully captured below in the per-domain rows but listed here for completeness:
- `routes/ops.js`: `POST /parcels/:id/hold`, `POST /parcels/:id/release` (the OpsConsole UI calls both).
- `routes/buyForMe.js`: `POST /:id/cancel` (customer), `PATCH /:id` (operator quotes / advances status).
- `routes/pricingTiers.js`: `POST /promotions/validate` (public).
- `routes/consolidationsV2.js`: `PATCH /:id`, `POST /:id/assign-parcel`, `POST /:id/remove-parcel`, `POST /:id/pallets`, `POST /:id/manifest`.

---

## Per-domain audit

### Auth

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `POST /api/auth/register` | `Register.jsx` | `AuthRepository.signUpWithEmail` → `AuthViewModel` → `SignInView` | `users`, `wallet`, `referrals` | ✅ | None. |
| `POST /api/auth/login` | `Login.jsx` | `AuthRepository.signInWithEmail` → `AuthViewModel` → `SignInView` | `users` | ✅ | None. |
| `GET /api/auth/me` | (implicit on app boot) | `AuthRepository.refreshCurrentUser` (inferred) | `users` | ✅ | Confirm path is wired into refresh. |
| `PUT /api/auth/profile` | `Dashboard.jsx` (profile sheet) | `AuthRepository.updateProfile` → `ProfileEditViewModel` → `ProfileEditView` | `users` | ✅ | None. |
| `PUT /api/auth/password` | profile sheet | `AuthRepository.changePassword` | `users` | ✅ | None. |
| `POST /api/auth/forgot-password` | `ForgotPassword.jsx` | `AuthRepository.forgotPassword` | `users`, `password_reset_tokens` (+email) | ✅ | None. |
| `POST /api/auth/reset-password` | `ResetPassword.jsx` (deep link) | ✗ missing on iOS | `users`, `password_reset_tokens` | ✗ | Add Universal Link handler + reset screen. Server already mints reset tokens. |
| `POST /api/auth/supabase-token` | n/a | `AuthRepository.refreshSupabaseToken` | none (mints JWT) | ✅ | None. |

### Orders (customer-facing)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `POST /api/orders` | `NewOrder.jsx` | `OrdersRepository.create` exists, **but** `ParcelPreRegViewModel.submit` calls `PackageRepository.upsert` → PostgREST write to `packages`. `NewOrderView.swift` is the iOS analogue. | `orders` (INSERT), `packages` (INSERT), `referrals` (UPDATE), `users.wallet_balance` (UPDATE), `wallet` (UPDATE), `transactions` (INSERT ×2) — **plus** `pushToUser('order_update')` + `pushToAdmins('admin_stats')` | ☠ | **Phase 4 priority #1.** Drop the PostgREST upsert; route through `OrdersRepository.create` and `packages.refreshForUser`. Apply `server-patches/routes/orders_create_email.patch.md` so `sendOrderCreatedEmail` fires. |
| `GET /api/orders` | `Orders.jsx` (customer list) | `OrdersRepository.fetchOrders` (PostgREST) → `CustomerDashboardViewModel`, also `observeOrders(userId)` realtime | `orders` | ⚠ | iOS reads `orders` directly via PostgREST. With RLS off this works, but admin/customer list parity is fine. **No DTO change needed if we move to Express** — `OrdersRepository.list(...)` would mirror server pagination + status/market filters. Defer until after #1 lands. |
| `GET /api/orders/:id` | `OrderDetail.jsx` | `OrdersRepository.detail` (PostgREST) → consumed by `ParcelDetailView` indirectly | `orders`, `packages` | ⚠ | The webapp returns a synthesised `cost_breakdown` recomputed live; iOS's PostgREST path doesn't include it. The detail screen should hit `GET /api/orders/:id` so the cost breakdown card renders. |
| `PUT /api/orders/:id/status` | (admin only — `AdminDashboard.jsx`) | covered by `AdminRepository.bulkUpdateOrders` (different endpoint) | `orders` (+push) | ✗web→iOS | This single-order admin status mutator has no iOS caller. Bulk update suffices for AdminOrdersView; verify in Phase 4 priority #2. |

### Orders (admin)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/admin/orders` | `AdminDashboard.jsx` Orders tab | `AdminRepository.listOrders(page,limit,status,market,startDate,endDate)` → `AdminOrdersViewModel` → `AdminOrdersView` | `orders` JOIN `users` | ✅ | Phase 2 hardened pagination (`AdminPagination` Loose). Phase 4.2 added market + date-range filters and load-more pagination. |
| `GET /api/orders/:id` (admin bypass) | `OrderDetail.jsx` | `AdminRepository.orderDetail` → `AdminOrderDetailViewModel` → `AdminOrderDetailView.swift` | `orders`, `packages` | ✅ | Phase 4.2: full drill-down with live `cost_breakdown` (base shipping, electronics handling, handling fee, insurance, customs estimate) + attached packages list. |
| `PUT /api/admin/orders/bulk-update` | bulk status dropdown | `AdminRepository.bulkUpdateOrders` | `orders` (+ push per order + admin_stats) | ✅ | None. |
| `PUT /api/admin/orders/:id/edit` | edit modal | `AdminRepository.editOrder` | `orders` (+ `packages` if weight changes), `admin_logs` (INSERT), email `sendOrderUpdatedEmail`, push | ✅ | Phase 4.2 confirmed: edit sheet exposes weight, actual_cost, customs_duty, status, description, **electronics_item**, internal notes. |
| `POST /api/admin/orders/:id/cancel` | per-row Cancel button | `AdminRepository.cancelOrder` | `orders` | ✅ | Phase 4.2: cancel sheet now captures an optional reason instead of hardcoding "Admin cancel". |
| `POST /api/admin/orders/:id/request-payment` | request-payment button | `AdminRepository.requestPayment` | none (email expected — verify webapp implementation) | ✅ | Confirm webapp actually sends the email; the route was not deeply read. |
| `POST /api/admin/orders/:id/send-reminder` | reminder button | `AdminRepository.sendReminder` | none (email expected) | ✅ | Phase 4.2: surfaced as a "Send reminder" item in the row's Payment menu (alongside Request payment). |
| `POST /api/admin/orders/create-for-client` | "Create Order for Client" sheet | `AdminRepository.createOrderForClient` → AdminOrdersView "Create order for client" entry | `orders`, `packages` (server-side) | ✅ | Validate the patch in `server-patches/routes/admin_create_order_validation.patch.md` — already staged but worth re-confirming. |

### Packages (intake / detail / consolidation)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/tracking/user/packages` | `TrackPackage.jsx` (logged-in tab) | `PackageRepository.refreshForUser` (PostgREST), consumed by `IntakeViewModel`, `CustomerDashboardViewModel` | `packages` JOIN `orders` | ⚠ | iOS uses PostgREST, so pagination is missing. Deferred — fine for now. |
| `GET /api/tracking/:trackingNumber` | `TrackPackage.jsx` (public) | `TrackingViewModel` (inferred — no clear repo wrapper) → `TrackingView` | `orders`, `packages` | ✗ | iOS does not yet have a public-tracking caller. Add `TrackingRepository.publicTrack(trackingNumber)`. |
| `PUT /api/tracking/:id/status` | (admin) | covered by `AdminRepository.editOrder`/`bulkUpdateOrders` | `packages` + sendInAppNotification | ✗web→iOS | No direct iOS caller; admin uses Order edit. Defer. |
| n/a | n/a | `PackageRepository.upsert` (☠ direct PostgREST) — used by `ParcelPreRegView` | `packages` | ☠ | Removed by Phase 4 priority #1. |
| n/a | n/a | `PackageRepository.updateStatus` (direct PostgREST) | `packages` | ☠ | Likely dead code. Verify and delete in Phase 4 priority #1 cleanup. |

### Tracking

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/tracking/:trackingNumber` (public) | `TrackPackage.jsx` | ✗ — `TrackingView.swift` exists but no repository call. | `orders`, `packages` | ✗ | Add public-tracking caller (no auth). Use the tracking number to render the 7-step timeline (matches React UX). |

### Wallet

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/wallet` | `Wallet.jsx` | `WalletRepository.observeWallet` (Realtime) → `WalletViewModel` → `WalletView` | `wallet`, `transactions` | ⚠ | `recent_transactions[].amount` is `real`; tighten DTO with `LooseDouble` for safety on edge null/string cases. |
| `GET /api/wallet/mpesa-info` | `Wallet.jsx` | ✗ — iOS shows hardcoded paybill in `WalletView`. | none | ✗ | Add `WalletRepository.mpesaInfo()` and surface paybill/account/business name. |
| `POST /api/wallet/mpesa-confirm` | `Wallet.jsx` deposit flow | ✗ — iOS has no M-Pesa deposit submit form. | `transactions` (status=pending), `admin_logs`, `notifications` | ✗ | Add deposit confirmation form on `WalletView` (mpesa_message, optional order_id, amount). |
| `POST /api/wallet/pay` | (used internally on order payment) | `WalletRepository.topUp` exists but points at `/api/wallet/topup` — **endpoint not in webapp**. | `wallet` (FOR UPDATE), `transactions` | ☠ | iOS calls `/api/wallet/topup` which is not in JS1. Either rename iOS path to `/api/wallet/pay` (current impl) or add a topup endpoint server-side. Recommend adding `POST /api/wallet/topup` as an alias since the current /pay handler is order-payment, not wallet top-up. |
| `GET /api/wallet/transactions` | `Wallet.jsx` history | `WalletRepository.transactions` (inferred) | `transactions` | ⚠ | `pagination.total` → `LooseInt`. |

### Tickets / Support

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/tickets` | `Support.jsx` (sidebar) | `TicketsRepository.list` → `TicketsListViewModel` → `TicketsView` | `tickets` | ✅ | None. |
| `POST /api/tickets` | `Support.jsx` "+ New Ticket" | `TicketsRepository.create` | `tickets` (+email + push) | ⚠ | Webapp accepts file attachment via multipart/form-data (per `Support.jsx` createTicket signature). iOS does not yet support file attachments — open follow-up. |
| `GET /api/tickets/:id` | thread view | `TicketsRepository.detail` → `TicketDetailViewModel` → `TicketDetailView` | `tickets`, `ticket_messages` | ✅ | None. |
| `POST /api/tickets/:id/message` | reply input | `TicketsRepository.postMessage` | `ticket_messages` (+push to user/admins) | ✅ | None. |
| `PUT /api/tickets/:id/status` | (admin) | covered by `TicketsRepository`-style admin call (verify) | `tickets` (+push) | ✗web→iOS | Add admin reply+status pathway from `AdminTicketsView` if not present. |
| `GET /api/tickets/admin/all` | `AdminDashboard.jsx` Tickets tab | `TicketsRepository.listAllAdmin` | `tickets` JOIN `users` | ✅ | None. |
| Realtime stream | SSE `/api/events`: `ticket_update` | iOS polls only | `tickets`, `ticket_messages` | ✗ | **Phase 4 priority #4** — add `tickets` + `ticket_messages` to `supabase_realtime`, then subscribe in `TicketDetailViewModel`. Will eliminate the need for SSE on this surface. |

### Admin Users

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/admin/users` | Users tab | `AdminRepository.listUsers` → `AdminUsersViewModel` → `AdminUsersView` | `users` | ⚠ | `pagination.total` → `LooseInt`. |
| `GET /api/admin/users/search` | customer search | `AdminRepository.searchUsers` | `users` | ✅ | None. |
| `GET /api/admin/users/:id` | (drill-down) | `AdminRepository.userDetail` → `adminUserDetailViewModel(userId)` (exported `ThapsusSdk.kt:200`) → `AdminUserDetailView` (`AdminUsersView.swift:259`) | `users`, `orders`, `transactions`, `referrals` | ⚠ | `referralStats.{total_referrals,completed_referrals,pending_referrals,total_earned}` are pg-aggregates → wrap with `LooseInt`/`LooseDouble` in Phase 2. (Drill-down already wired — Phase 1 inventory was wrong.) |
| `PUT /api/admin/users/:id` | role/status edit | `AdminRepository.updateUser` / `setUserActive` | `users`, `admin_logs` | ✅ | None. |
| `DELETE /api/admin/users/:id` | delete button | `AdminRepository.deleteUser` | cascades 8 tables | ⚠ | Server-side cascade is large; iOS UI should show a confirmation dialog with the cascade scope. (Already does — verify copy.) |
| `POST /api/admin/users/create` | + Create User | `AdminRepository.provisionUser` | `users`, `wallet` | ✅ | None. |
| `POST /api/admin/users/:id/reset-password` | reset button | n/a — handled by sending reset email | `password_reset_tokens` (+email) | ✗ | Add `AdminRepository.adminResetPassword(id)` and surface in AdminUsersView detail sheet. |
| `POST /api/admin/test-email` | (admin debug) | `AdminRepository.testEmail` (already in iOS Admin Console) | none (+email) | ✅ | None. |

### Admin Stats / Revenue / Logs

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/admin/stats` | AdminDashboard Overview | `AdminRepository.statsFull` → `KPIDashboardViewModel`, `AdminDashboardViewModel` | many | ✅ | Already hardened with `LooseInt`/`LooseDouble` (this was the original crash site). |
| `GET /api/admin/revenue` | revenue charts | ✗ — iOS Admin shows stats only, no revenue tab. | `transactions` GROUP BY | ✗ | Optional add later; not in Phase 4 list. |
| `GET /api/admin/revenue/export` | CSV export | ✗ | `transactions` | ✗ | Not on iOS. Skip. |
| `GET /api/admin/logs` | (Admin Logs panel) | ✗ — iOS lacks an admin_logs viewer. | `admin_logs` JOIN `users` | ✗ | Optional add later; not in Phase 4 list. |
| `GET /api/admin/error-logs` (+ stats, DELETE) | Error Logs tab | `AdminRepository.errorLogs` / `errorLogStats` / `clearErrorLogs` → `AdminErrorLogsViewModel` → `AdminErrorLogsView` | `error_logs` | ⚠ | `pagination.total` and stats counts → `LooseInt`. |
| `GET /api/admin/transactions/pending` | Payments tab | `AdminRepository.pendingPayments` → `AdminPaymentsViewModel` → `AdminPaymentsView` | `transactions` JOIN `users` LATERAL `admin_logs` | ✅ | Phase 4.3: `PendingTransactionRow` realigned to the actual wire shape (`name`/`email`/`mpesa_message`/`payer_name`/`payer_phone` — the previous `customer_name`/`customer_email` keys never existed and silently defaulted to null). Proof + payer details are now inlined in the row, no separate `/proof` round-trip. |
| `POST /api/admin/transactions/:id/approve` | approve button | `AdminRepository.approvePayment` → `ApprovePaymentSheet` | `transactions` | ✅ | Phase 4.3: confirmation sheet shows customer + amount + reference, optional notes input, banner echoes "wallet credited and receipt emailed". |
| `POST /api/admin/transactions/:id/reject` | reject button | `AdminRepository.rejectPayment` → `RejectPaymentSheet` | `transactions` | ✅ | Phase 4.3: required reason input (button disabled until non-empty); banner echoes "customer notified". |

### Pricing (calculator + admin tiers/fees/promotions)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `POST /api/pricing/calculate` | `PricingCalculator.jsx` | `QuoteEngine` (local) — does NOT call this endpoint | (none, server-side) | ⚠ | iOS computes locally from `pricing_tiers`+`fees`. Acceptable; document the reason and ensure tier/fee tables are kept in sync. |
| `GET /api/pricing/electronics` | calculator UI | hardcoded in iOS quote engine? | none | ⚠ | iOS may have hardcoded electronics handling fees. Switch to fetching from this endpoint to keep editable from admin Ops Settings. |
| `GET /api/pricing/rates` | `PricingCalculator.jsx` | ✗ | `shipping_rates` (fallback DEFAULT_RATES) | ✗ | Not exposed on iOS. Phase 4 priority #5 should cover via OpsSettings. |
| `PUT /api/pricing/rates` | AdminDashboard Rates tab | ✗ — iOS edit not exposed | `shipping_rates`, `admin_logs` | ✗ | OpsSettings now edits exchange rates for all four pairs (Phase 4.5). The `shipping_rates` per-market editor is still web-only and **out of Phase 4 scope** — track separately if iOS admins need to retune market rates. |
| `PUT /api/pricing/electronics` | AdminDashboard Rates tab | ✗ | `electronics_fees` (auto-created) | ✗ | Phase 4 priority #5. |
| `GET /api/pricing-tiers/tiers` | `OpsSettings.jsx` Tiers tab | `PricingRepository.fetchActiveTiers` → `QuoteViewModel` (+`OpsSettingsViewModel`) | `pricing_tiers` | ✅ | None. Already uses `PricingChannelSerializer`. |
| `POST /api/pricing-tiers/tiers` | OpsSettings | ✗ — iOS only PATCHes existing rows | `pricing_tiers` | ✗ | Phase 4 priority #5: add tier-creation flow. |
| `PATCH /api/pricing-tiers/tiers/:id` | OpsSettings inline edit | `PricingTiersRepository.updateFee` (sic; misnamed — verify) | `pricing_tiers` | ⚠ | Method name `updateFee` lives in `AdminRepository.kt` but the path is `/pricing-tiers/fees/:id`. Confirm there's a separate `updateTier`. |
| `GET /api/pricing-tiers/fees` | OpsSettings Fees tab | `PricingRepository.fetchActiveFees` and `PricingTiersRepository.fees` | `fees` | ✅ | DTO already handles `degraded` flag. |
| `PATCH /api/pricing-tiers/fees/:id` | OpsSettings inline edit | `AdminRepository.updateFee` → `OpsSettingsView` feesSection | `fees` | ✅ | Phase 4.5: each row now exposes label/code/amount + currency-or-`%` + active toggle, with an inline editable amount + Save (was read-only). |
| `GET /api/pricing-tiers/promotions` | OpsSettings Promotions | `AdminRepository.promotions` | `promotions` | ✅ | None. |
| `POST /api/pricing-tiers/promotions` | OpsSettings | `AdminRepository.createPromotion` | `promotions` | ✅ | None. |
| `POST /api/pricing-tiers/promotions/validate` (public) | (NewOrder.jsx may apply codes) | ✗ | `promotions` | ✗ | iOS NewOrder has no promo-code field. Optional add. |

### Customs (clearing-agent portal)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/customs/agent/consolidations` | `pages/partner/AgentPortal.jsx` | `CustomsRepository.fetchAgentQueue` (inferred) → `CustomsAgentViewModel` → `CustomsListView` | `consolidations` | ✅ | Confirm method name and wiring. |
| `GET /api/customs/agent/consolidations/:id/parcels` | AgentPortal detail | `CustomsRepository.fetchParcels` (inferred) | `orders`, `users`, `customs_entries` | ✅ | Same. |
| `POST /api/customs/entries` | per-parcel entry form | n/a — `CustomsRepository.submitEntry` writes PostgREST direct | `customs_entries`, `orders` (status='customs') | ☠ | Repoint at this Express endpoint per F2. |
| `PATCH /api/customs/entries/:id` | mark released | n/a | `customs_entries`, `orders` (status='released' if released) | ☠ | Add an Express-routed update method on `CustomsRepository`. |

### LastMile / Rider

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/last-mile/dispatch` | `OpsDispatch.jsx` | `LastMileRepository.dispatch` (inferred) → `DispatchView` | `orders`, `last_mile_runs` | ✅ | Confirm. |
| `POST /api/last-mile/runs` | OpsDispatch create-run | `LastMileRepository.createRun` (inferred) | `last_mile_runs`, `orders` (status='out_for_delivery') | ✅ | Confirm. |
| `PATCH /api/last-mile/runs/:id` | OpsDispatch edit | `LastMileRepository.updateRun` (inferred) | `last_mile_runs` | ✅ | Confirm. |
| `GET /api/last-mile/rider/today` | `pages/partner/RiderPwa.jsx` | `LastMileRepository.todayRuns` (inferred) → `RiderRunViewModel` → `RiderRunView` | `last_mile_runs` | ✅ | Confirm. |
| n/a (no Express endpoint) | `RiderPwa.jsx` POD form posts to `/api/last-mile/rider/runs/:runId/pod` and `/fail` | `LastMileRepository.capturePod` (offline outbox) → `flushOutbox` → PostgREST `pod_events.insert` | `pod_events` | ✗web | Webapp UI calls Express POD endpoints that **don't exist in routes/lastMile.js**. iOS uses outbox+PostgREST. **Open a server gap**: add `POST /api/last-mile/rider/runs/:runId/pod` (and `/fail`) so the React PWA actually works AND iOS can switch off the direct-write path. |

### Notifications / Realtime / Events

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/notifications` | `Dashboard.jsx` (banner badge) | `NotificationsRepository.list` → `NotificationInboxViewModel` → `NotificationInboxView` | `notifications` | ⚠ | `unread:int` → `LooseInt`. |
| `PUT /api/notifications/:id/read` | tap row | `NotificationsRepository.markRead` | `notifications` | ✅ | None. |
| `PUT /api/notifications/read-all` | "Mark all read" | `NotificationsRepository.markAllRead` | `notifications` | ✅ | None. |
| `GET /api/events` (SSE) | `useOrderUpdates`/`useTicketUpdates`/`useWalletUpdates`/`useAdminStats` hooks | ✗ — iOS subscribes to Supabase Realtime instead. | none | ✅ | Phase 4.6: iOS surfaces in-app banners via `NotificationBannerView` (overlaid on `CustomerDashboardView` + `CustomerHomeView`) driven by the `notifications` Realtime stream. NPS auto-prompt fires on package status transitions to `delivered`, idempotent via UserDefaults `thapsus.nps.asked.<parcelId>`. |
| `notifications` in `supabase_realtime` | banner / inbox | `NotificationsRepository.observeForUser` → `NotificationInboxViewModel` (cache-first, Realtime fan-in) | `notifications` | ✅ | Migrations 007 + 009 applied. iOS now SQLDelight-caches each row + applies INSERT/UPDATE events filtered by `user_id = auth.uid()::text`. |
| `tickets` + `ticket_messages` in `supabase_realtime` | inbox + thread | `TicketsRepository.observeMine` / `observeAdminAll` / `observeThread` → `TicketsListViewModel` + `TicketDetailViewModel` | `tickets`, `ticket_messages` | ✅ | Phase 4.4. Customer + admin streams use the same SQLDelight cache. Thread bootstrap fetches `/tickets/:id` for sender attribution; subsequent realtime messages cache without name/email/role and fill in on next bootstrap. |
| `buy_for_me_orders` in publication | operator queue | `BuyForMeRepository.observeOperatorQueue` (in-memory, no cache) | `buy_for_me_orders` | ✅ | Phase 4.4. Bootstraps from `/buy-for-me/queue` then merges Realtime events; rows whose status leaves the `pending_quote/quoted/paid` set are dropped from the queue snapshot. |
| `aml_flags` in publication | admin queue | `AdminRepository.observeAmlFlags` (in-memory, admin-only via `is_thapsus_admin()` policy) | `aml_flags` | ✅ | Phase 4.4. |

### Email service

| Trigger | Function | iOS exposure | Action |
|---|---|---|---|
| Forgot password | `sendPasswordResetEmail` | `forgotPassword` flow → AuthRepository | ✅ |
| Admin password reset | `sendAdminPasswordResetEmail` | not yet wired in iOS | ✗ — see Admin Users action above. |
| Order created | `sendOrderCreatedEmail` (staged in `server-patches/routes/orders_create_email.patch.md`, **not applied**) | depends on Phase 4 priority #1 | apply patch alongside the orders-routing fix. |
| Order edited | `sendOrderUpdatedEmail` | server-side; iOS receives push event | ✅ |
| Ticket created | `sendTicketCreatedEmail` | server-side | ✅ |
| Ticket admin reply | `sendTicketReplyEmail` | server-side | ✅ |
| Tracking status update | `sendInAppNotification` | server-side | ✅ |
| Email diagnostics | `POST /api/admin/test-email` | `AdminRepository.testEmail` | ✅ |

### Insurance

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `POST /api/insurance/quote` | `Insurance.jsx` slider | `InsuranceRepository.quote` → `InsuranceViewModel` → `InsuranceView` | none | ✅ | None. |
| `POST /api/insurance/policies` | Buy button | `InsuranceRepository.issue` | `insurance_policies`, `orders` | ✅ | None. |
| `GET /api/insurance/policies` | Policies table | `InsuranceRepository.listPolicies` | `insurance_policies` JOIN `orders` | ✅ | None. |
| `POST /api/insurance/policies/:id/claim` | Claim button | `InsuranceRepository.claim` | `insurance_policies` | ✅ | None. |

### Buy-For-Me (concierge shopping)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `POST /api/buy-for-me` | `BuyForMe.jsx` form | `BuyForMeRepository.create` → `BuyForMeViewModel` → `BuyForMeView` | `buy_for_me_orders` | ✅ | None. |
| `GET /api/buy-for-me` | `BuyForMe.jsx` list | `BuyForMeRepository.list` | `buy_for_me_orders` | ✅ | None. |
| `GET /api/buy-for-me/queue` (operator) | (no React page found in audit) | ✗ — iOS has no operator queue view either | `buy_for_me_orders` | ✗web→iOS | Optional. |
| `GET /api/buy-for-me/:id` | detail row click | `BuyForMeRepository.detail` | `buy_for_me_orders` | ✅ | None. |
| `POST /api/buy-for-me/:id/pay` | Pay button | `BuyForMeRepository.pay` | `buy_for_me_orders`, `wallet` (FOR UPDATE), `transactions`, `exchange_rates` (read) | ✅ | None. |
| `POST /api/buy-for-me/:id/cancel` | Cancel button | `BuyForMeRepository.cancel` | `buy_for_me_orders` | ✅ | Confirm iOS wires the cancel button. |
| `PATCH /api/buy-for-me/:id` (operator) | (operator quote flow) | ✗ — no iOS operator surface | `buy_for_me_orders` | ✗ | Optional. |

### Consolidation (customer view + operator console)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/consolidation` | `Consolidation.jsx` packages-waiting | `ConsolidationRepository.customerSummary` (different shape — verify) → `CustomerConsolidationViewModel` → `CustomerConsolidationView` | `packages` JOIN `orders` | ⚠ | Verify the `total_weight_kg` field is loose-decoded. |
| `GET /api/consolidation/requests` | requests history | ✗ — iOS shows a single consolidation, not the history list | `packages` GROUP BY consolidated_with | ✗web→iOS by design | **Decision (2026-04-29):** consolidation initiation/management stays operator+admin only on iOS. No customer surface. |
| `POST /api/consolidation/request` | "Request Consolidation" | ✗ — iOS does not allow customer-initiated consolidation | `packages` | ✗web→iOS by design | **Decision (2026-04-29):** customer-initiated consolidation removed from iOS scope. Operators consolidate via `OpsConsolidations.jsx` / iOS `ConsolidationListView`. |
| `GET /api/consolidation/:id` | (drill-down) | `ConsolidationRepository.customerSummary(id)` → `CustomerConsolidationView` | `packages` JOIN `orders` | ✅ | None. |
| `GET /api/consolidations/current` (public) | `Home.jsx` cutoff banner, `Dashboard.jsx` | ✗ | `consolidations` | ✗ | Add a cutoff banner to iOS `CustomerHomeView` / `CustomerDashboardView`. |
| `GET /api/consolidations/customer/:id` | customer view via auth | covered by `customerSummary` | `consolidations`, `packages` | ✅ | None. |
| `GET /api/consolidations` (operator list) | `OpsConsolidations.jsx` | `ConsolidationRepository.fetchOperatorList` (inferred) → `ConsolidationListView`, `OperatorTodayView` | `consolidations` | ✅ | Confirm. |
| `POST /api/consolidations` (operator) | "+ New consolidation" | ✗ — iOS list has no create | `consolidations` | ✗ | Optional add. |
| `GET /api/consolidations/:id` (operator detail) | OpsConsolidations detail | `ConsolidationRepository.fetchDetail(id)` → `ConsolidationDetailView` | `consolidations`, `orders`, `pallets`, `customs_entries` | ✅ | Verify all four arrays are decoded. |
| `PATCH /api/consolidations/:id` | status / AWB edit | `ConsolidationRepository.lock` is a PostgREST direct write — repoint here | `consolidations` | ☠ | Per F2. |
| `POST /api/consolidations/:id/assign-parcel` | drag-add | ✗ | `orders`, `consolidations` | ✗ | Optional. |
| `POST /api/consolidations/:id/remove-parcel` | drag-remove | ✗ | `orders`, `consolidations` | ✗ | Optional. |
| `POST /api/consolidations/:id/pallets` | "Add pallet" | ✗ | `pallets` | ✗ | Optional. |
| `POST /api/consolidations/:id/manifest` | "Generate manifest" | ✗ | `orders`, `users` (computed) | ✗ | Optional. |

### DSAR

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `POST /api/dsar` | `DsarRequest.jsx` | `DsarRepository.create` → `DsarViewModel` → `DsarView` | `dsar_requests` | ✅ | None (table-existence handled with 503). |
| `GET /api/dsar/me` | DSAR list | `DsarRepository.listMine` | `dsar_requests` | ✅ | None. |
| `GET /api/dsar/queue` (admin) | (no React page found) | ✗ | `dsar_requests` JOIN `users` | ✗ | Optional admin add. |
| `PATCH /api/dsar/:id` (admin) | (admin queue) | ✗ | `dsar_requests` | ✗ | Optional. |
| `POST /api/dsar/:id/export` (admin) | (admin queue) | ✗ | `users`, `orders`, `transactions`, `tickets` | ✗ | Optional. |

### KPI / Stats (admin)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/kpi` | `KpiDashboard.jsx` | `AdminRepository.statsFull` reuses `/admin/stats`, not `/kpi`. The `/kpi` endpoint has no iOS caller. | `orders`, `transactions`, `nps_responses`, `wallet`, `insurance_policies` | ✗ | Add `KpiRepository.summary()` wired into `KPIDashboardViewModel` so the founder KPIs (kg/week, on-time %, NPS, complaints/100, insurance pool) appear. Wrap every field with `LooseDouble`/`LooseInt`. |
| `GET /api/kpi/marketing` | KpiDashboard secondary tiles | ✗ | `marketing_attributions`, `users`, `orders` | ✗ | Add `KpiRepository.marketing()` if the iOS founder dash shows attribution. |

### NPS

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `POST /api/nps` | post-delivery survey (presumably linked from notifications) | ✗ | `nps_responses` | ✗ | Add a one-tap survey modal triggered by a `delivered` push, posting to this endpoint. |
| `GET /api/nps/summary` | KpiDashboard NPS tile | depends on KpiRepository above | `nps_responses` | ✗ | Same as KPI above; this is the data source for the NPS tile. |

### Referral

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/referral` | `Referral.jsx` | `ReferralsRepository.summary` → `ReferralViewModel` → `ReferralView` | `users`, `referrals`, `orders` | ⚠ | `statistics` aggregates → `LooseInt`/`LooseDouble`. |
| `GET /api/referral/history` | history panel | `ReferralsRepository.history` | `referrals` JOIN `users`/`orders` | ⚠ | `pagination.total` → `LooseInt`. |
| `POST /api/referral/validate` (public) | Register form live-validate | wired in iOS `SignInView` register flow? Verify. | `users` | ✅ | Confirm. |

### Prohibited items

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/prohibited/check` | `ProhibitedItems.jsx` form | covered by `ProhibitedRepository.check` (which calls `/search`) | none / utils | ⚠ | iOS search uses `/search`; `/check` (single-item allow/deny with category + risk_level) is unused. Either point at `/check` for the search box or keep `/search` and document. |
| `GET /api/prohibited/categories` | accordion | `ProhibitedRepository.categories` | none / utils | ✅ | None. |
| `GET /api/prohibited/categories/:name` | accordion expand | `ProhibitedRepository.categoryDetail` | none / utils | ✅ | None. |
| `GET /api/prohibited/search` | (admin search) | `ProhibitedRepository.check` (sic) | `prohibited_items` | ✅ | Method naming inconsistent — `check` should be `search`. Optional rename. |
| `POST /api/prohibited` (admin) | AdminDashboard? | `ProhibitedRepository.create` | `prohibited_items` | ✅ | Confirm admin UI surfaces this. |
| `PATCH /api/prohibited/:id` | admin edit | ✗ — iOS only has create+delete | `prohibited_items` | ✗ | Optional add. |
| `DELETE /api/prohibited/:id` | admin delete | `ProhibitedRepository.delete` | `prohibited_items` | ✅ | None. |

### Warehouse

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/warehouse/addresses` | `Dashboard.jsx`, `ShipInstructions.jsx` | `WarehouseRepository.addresses` → `WarehouseViewModel` → `WarehouseAddressView` | none (env-driven) | ✅ | None. |

### Exchange

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/admin/exchange-rates` + `PUT /api/admin/exchange-rates` | `ExchangeRate.jsx` (public), `OpsSettings.jsx` (admin) | `AdminRepository.rates` / `updateRate` / `updateRates(map)` → `OpsSettingsView` ratesSection | `exchange_rates` | ✅ | Phase 4.5: per-pair editor for USD/GBP/EUR/CNY → KES with both per-row Save and "Save all" bulk flow; resolves audit F4 alongside migration 008 (RLS policies live). |
| `POST /api/exchange/convert` | live-converter | ✗ | `exchange_rates` | ✗ | Optional. |

### Backups

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `POST /api/admin/backups` | (admin maintenance) | ✗ | `backups`, `admin_logs` | ✗ | Optional. |
| `GET /api/admin/backups` | (admin maintenance) | ✗ | `backups` JOIN `users` | ✗ | Optional. |

### Agent Invoices (clearing-agent + admin)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/agent-invoices/mine` | AgentPortal | `AgentInvoicesRepository.listMine` → `AgentInvoicesViewModel` → `AgentInvoicesView` | `agent_invoices` | ✅ | None. |
| `POST /api/agent-invoices` | submit form | `AgentInvoicesRepository.submit` | `agent_invoices` | ✅ | None. |
| `GET /api/agent-invoices` (admin) | (admin queue?) | `AgentInvoicesRepository.listAll` | `agent_invoices` JOIN `users` | ✅ | Confirm admin UI surfaces this. |
| `PATCH /api/agent-invoices/:id` (admin) | approve/reject | `AgentInvoicesRepository.updateStatus` | `agent_invoices` | ✅ | None. |

### AML Flags (admin)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/admin/aml-flags` | (admin queue) | `AdminRepository.amlFlags` | `aml_flags` JOIN `users` | ✅ | DTO already typed; confirm the `status` filter dropdown is wired. |
| `POST /api/admin/aml-flags` | manual add | ✗ | `aml_flags` | ✗ | Optional. |
| `PATCH /api/admin/aml-flags/:id` | resolve/escalate | `AdminRepository.resolveAmlFlag` | `aml_flags` | ✅ | Add to `supabase_realtime` (Phase 4 priority #4) so the queue updates live. |

### Payment (public M-Pesa confirmation)

| Webapp endpoint | Webapp UI | iOS repo+vm+view | DB tables | Status | Action |
|---|---|---|---|---|---|
| `GET /api/payment/:orderId` (public) | `PublicPayment.jsx` | covered by `PublicPaymentView` | `orders` (subset) | ✅ | Confirm path used by Universal Link. |
| `POST /api/payment/:orderId/confirm` (public) | submit M-Pesa proof | `PublicPaymentView` calls (verify) | `transactions`, `admin_logs`, `notifications` | ✅ | Confirm. |

### Sitemap / robots (web only)

Mounted at `/`, no API prefix. No iOS counterpart needed.

---

## Action priorities (mapped to spec phases)

### Phase 2 — DTO hardening (driven by F1)

Wrap loose aggregates in the DTOs corresponding to the endpoints listed in F1. Add a regression test for each DTO that decodes string-encoded `"0"` and `null`. Specifically file-by-file:

- `data/dto/AdminDtos.kt` — already done.
- `data/dto/AdminExtraDto.kt` — already done.
- `data/dto/ReferralDto.kt` — `ReferralStatistics` int/double fields, `ReferredUser.orders_count`, `pagination.total`.
- `data/dto/NotificationDto.kt` — `unread`.
- `data/dto/WalletDto.kt` — `balance`, `recent_transactions[].amount`.
- `data/dto/Invoices.kt` and `data/dto/AdminDtos.kt` (transactions pagination).
- `data/dto/PackageDto.kt` — `pagination.total` on the tracking endpoint, plus loose decode on declared/weight columns.
- `data/dto/ConsolidationDto.kt` — `total_weight_kg`, `requests[].packageCount`.
- `data/dto/AdminDtos.kt` — admin order pagination.
- New `data/dto/KpiDto.kt` — every kpi field, plus marketing retention.
- New `data/dto/NpsSummaryDto.kt` — every field.
- New `data/dto/OpsTodayDto.kt` — every field.
- New `data/dto/AgentInvoiceDto.kt` paginations if exposed.

Tests: `shared/src/commonTest/kotlin/.../dto/LooseNumericRegressionTest.kt` (or one file per DTO). Run `./gradlew :shared:iosSimulatorArm64Test`.

### Phase 3 — Schema / API alignment (driven by F3, F4, and missing endpoints)

New migrations under `server-patches/database/migrations/`:

- `007_realtime_publication_extras.sql` — `ALTER PUBLICATION supabase_realtime ADD TABLE notifications, tickets, ticket_messages, buy_for_me_orders, aml_flags;` Idempotent guarded with `pg_publication_tables` check.
- `008_exchange_rates_rls.sql` — either `ALTER TABLE exchange_rates DISABLE ROW LEVEL SECURITY;` or add `CREATE POLICY exchange_rates_select_authenticated ON exchange_rates FOR SELECT USING (auth.role() IN ('authenticated','anon'));` plus an UPDATE policy gated to admin claims. Decision: **option A (disable RLS)** is simpler since all writes go through Express + service-role; recommend it.
- `009_realtime_rls_select.sql` (optional, post-007) — once tables are in the publication, enable RLS + add SELECT policies scoped to `user_id = auth.uid()` (admins/operators bypass via separate policy). This closes F8 for the streaming surfaces.

Server endpoint gaps (no migration; staged route patches):
- `POST /api/last-mile/rider/runs/:runId/pod` and `/fail` — to align with React PWA + iOS outbox flush. Add to `routes/lastMile.js`.
- `PUT /api/exchange/rates/:pair` — admin-writable so iOS OpsSettings can edit all four pairs.
- (Apply the existing) `server-patches/routes/orders_create_email.patch.md`.

### Phase 4 — Implementation order (per spec)

1. **Customer order routing fix** (memory: `orders_routing_followup.md`). Same scope as the original plan — drop `PackageRepository.upsert` from `ParcelPreRegViewModel`, post via `OrdersRepository.create`, then `packages.refreshForUser`. Apply `orders_create_email.patch.md`. Verify admin Orders + KPI counts move and email arrives.
2. **Admin Orders detail + cancel/edit parity** with `client/src/pages/AdminDashboard.jsx` Orders tab. Edit sheet must include `electronics_item`. List filter (status + market + date). Bulk update. Cost-breakdown card on detail. iOS already has most pieces; missing pieces are detail filters and the electronics field.
3. **Admin Payments approve/reject end-to-end** — proof image rendering, reject reason input, approve banner. Wire into `AdminPaymentsView`.
4. **Realtime publications** — apply migration 007. Add SQLDelight cache rows for `notifications`, `tickets`, `ticket_messages` (the others are admin-only and don't need a customer-side cache yet). `RealtimeSync.kt` header documents the plan.
5. **Ops Settings parity** — exchange rates for all four pairs (depends on `PUT /api/exchange/rates/:pair`), full fee list with toggle, pricing tier editor (depends on `POST /api/pricing-tiers/tiers`).
6. **Notifications banners** — once 007 lands, surface in-app banners by subscribing to the `notifications` table and rendering a `CrystalCard`-styled banner from `RoleHomeViews`. The SSE channel in `routes/events.js` remains webapp-only; iOS uses the Realtime stream.

### Phase 5 — Verification (per change)

Per the spec — simulator exercise + DB confirm via Supabase MCP + server log/curl confirm. I'll attach a checklist per item when shipping.

---

## Open questions for the user

These came up during the audit. None blocks Phase 2; please review when convenient.

1. **`POST /api/wallet/topup` does not exist**, but iOS `WalletRepository.topUp` posts to `/api/wallet/topup`. The current `/pay` handler is order-payment, not wallet top-up. Add a server endpoint, or rename iOS to `/api/wallet/mpesa-confirm`?
2. **Express POD endpoints don't exist**, but `pages/partner/RiderPwa.jsx` calls `/api/last-mile/rider/runs/:runId/pod` and `/fail`. Add server routes (recommended) so the React PWA isn't half-broken and iOS can move off the direct PostgREST write path.
3. **`exchange_rates` RLS** — disable RLS (recommended) or add policies?
4. **Pricing tier creation** — should iOS OpsSettings allow `POST /api/pricing-tiers/tiers` (full create) or only `PATCH` existing rows? React supports create.
5. ~~**Customer-initiated consolidation**~~ — **Resolved 2026-04-29: removed from iOS scope.** Consolidation lifecycle stays operator+admin-only.
6. **iOS NPS survey** — should iOS show a post-delivery survey modal that posts to `/api/nps`? React doesn't have one either, so this would be iOS-leading.
7. **Cutoff banner on iOS Home** — `GET /api/consolidations/current` powers the React Home cutoff timer. Add to iOS `CustomerHomeView`?
8. **Tracking timeline on iOS** — `TrackingView.swift` exists but has no public-tracking call. Wire it up to `GET /api/tracking/:trackingNumber`?
9. ~~**AdminUserDetail**~~ — **Resolved 2026-04-29: already wired.** `ThapsusSdk.kt:200` exports the factory; `AdminUsersView.swift:112` has the drill-down NavigationLink. Phase 1 inventory missed it.

---

## Appendix — workspace artefacts retained

- `docs/_audit_workspace/db_schema_snapshot.md` — full Supabase schema dump (39 tables) + RLS + realtime + `_migrations`.
- `docs/_audit_workspace/webapp_routes.md` — per-route summary derived from the JS1 walk (slightly less detailed than the inventory above; the audit document is the source of truth from now on).

The workspace files are not part of the audit deliverable; they exist only so future sessions can rebuild the audit without re-running the agents. Safe to delete after Phase 4 lands if context becomes a maintenance burden.
