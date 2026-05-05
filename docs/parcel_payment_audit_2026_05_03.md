# Parcel + Payment + UI Audit — 2026-05-03

Scope: end-to-end review of `Swiftcargo-main` (Express server + React webapp)
and `thapsus-mobile` (iOS SwiftUI + Android Compose, KMP shared layer) covering:

1. Parcel lifecycle from preregistration → delivery
2. Buy-for-me concierge flow
3. Payment system + amount calculation, link to orders/consolidations
4. UI surfaces per role (customer, admin, operator, clearing agent, rider)

All findings come from a fresh read of the code; nothing relies on memory.
File:line references throughout.

---

## 1. Parcel lifecycle (the happy path)

**Customer preregister**
- `POST /api/orders` (`routes/orders.js:61`) creates an `orders` row at
  `status='pending'` *and* a sibling `packages` row at `status='pre_registered'`
  in the same transaction. A tracking number is minted via
  `crypto.randomBytes` (good — formerly `Math.random`).
- Weight/dims are now optional at create time (D-day intake change). If
  weight is missing, `estimated_cost` is **set to 0**.

**Operator receive**
- `POST /api/ops/parcels/:id/receive` (`routes/ops.js:87`) flips
  `orders.status → received_at_warehouse`, computes
  `volumetric = L·W·H/6000`, `chargeable = max(actual, volumetric)`, stamps
  `customs_duty` (KES, optional), and mirrors `packages.status →
  received_at_warehouse`. Notifies the customer through
  `notifyParcelStatus` (in-app + email + SSE).
- `POST /api/ops/parcels/:id/screen` re-runs prohibited check; if `risk_level
  = high` it flips `orders.hold_reason` and forces `packages.status='pending'`
  (this is a v1 enum value — see ⚠️ in §6).
- Hold/release endpoints toggle `hold_reason` on the order.

**Customer-consolidation** (per-customer billing unit, migration 025)
- `POST /api/customer-consolidations` (`routes/customerConsolidations.js:222`)
  groups one user's parcels (must be `received_at_warehouse`, not already
  grouped). Sets `packages.customer_consolidation_id`.
- `GET /:id/suggested-invoice` recomputes shipping per parcel using
  `calculateShippingCost` (GBP), converts to KES, sums operator-stamped
  `customs_duty` per child, returns a prefill. **Admin still confirms**
  via `PATCH /:id/invoice` which flips `status → invoiced`.
- `POST /:id/mark-paid` is the off-platform settle path (status `invoiced →
  paid`). The on-platform path goes through `POST /api/payments` (§3).
- `POST /attach-to-shipping/:shippingId` batches paid customer-consolidations
  into the weekly flight unit (mirrors `packages.consolidation_id` and
  `is_consolidated=true`).

**Shipping consolidation** (weekly flight, migration 001)
- `POST /api/consolidations` (`routes/consolidationsV2.js:122`) opens a unit
  with `cutoff_at` and optional `departure_at`.
- `POST /:id/assign-parcels[s]` (single + batch, `routes/consolidationsV2.js:243`)
  flips `packages.status → manifested`, `orders.status → consolidating`.
  Reverse via `/remove-parcel` resets both back to `received_at_warehouse`.
- `POST /:id/pallets` and `POST /:id/manifest` produce per-pallet rows and
  a JSON manifest. PDF generation is deferred to a worker (not implemented —
  see §6).
- `PATCH /:id` lets the operator stamp `master_awb_no`, `departure_at`,
  `arrival_at`, and the `assigned_agent_id` (the clearing agent).

**Customs (clearing agent)**
- `GET /api/customs/agent/consolidations` (`routes/customs.js:18`) lists the
  unit assigned to me.
- `POST /api/customs/entries` (`routes/customs.js:81`) — agent posts IDF/Entry/
  CIF/Duty/VAT/IDF fee/RDL per parcel. Server flips
  `orders.status → customs`. Has a unique constraint per parcel (good).
- `PATCH /api/customs/entries/:id` lets agent revise. When `status='released'`,
  server cascades `orders.status → out_for_delivery` (⚠️ skips the
  `last_mile_run` plan — see §6.B).
- `POST /api/customs/agent-invoices` — agent uploads their own fee invoice
  (no doc URL UI on web — see §4).

**Last-mile dispatch (operator → rider)**
- `GET /api/last-mile/dispatch` (`routes/lastMile.js:274`) lists parcels at
  `customs` not on an active run.
- `POST /api/last-mile/runs` — operator creates a run (zone + run_date),
  attaches parcels via `last_mile_run_parcels` (migration 012). Validates
  parcels exist + not already on another active run.
- `PATCH /:id/parcels` — add/remove/reorder/override delivery address.
  Frozen once the run leaves `planned`.
- `PATCH /:id` — assigning a rider on a `planned` run **auto-starts** it
  (`status → in_progress`), which fires `activateRunDispatch`:
  - Flips every parcel's `orders.status` and `packages.status` to
    `out_for_delivery`.
  - Issues **one OTP per recipient** (grouped by user_id) with a 24h TTL,
    SHA-256 hashed in `pod_otps`, plain code in an in-app notification.
- Has proper state-machine guards (planned → in_progress → completed only,
  and `FOR UPDATE` lock).

**Rider POD**
- `POST /api/last-mile/rider/runs/:runId/pod` accepts an array of parcel_ids
  (multi-parcel POD per recipient). Validates: rider is assigned, parcels
  on this run, deliverable status, OTP matches, photo or photo_path present.
- Persists pod_events (one per parcel), flips `orders.status` and
  `packages.status` to `delivered`, increments `completed_stops` atomically,
  flips run to `completed` when reached, queues NPS invite per parcel.
- Failure path (`/fail`) writes a fail event; second fail on the same run
  flips `orders.hold_reason='held_at_nairobi_hub'` and bumps
  `completed_stops` so the run can finish.
- `POST /pod/upload-url` mints a 5-min signed-upload URL into the private
  `pods` bucket; `/pod/document-url` is the only sanctioned read path.
  Authorisation correctly scopes both to the assigned rider on an active run.
- `POST /reissue-otp` re-mints if 24h elapsed.

---

## 2. Buy-for-me lifecycle

**Customer create** — `POST /api/buy-for-me` (`routes/buyForMe.js:22`).
Status `pending_quote`. Either `retailer_id` (from picker, PR4) or
`retailer_url` is required.

**Operator queue** — `GET /api/buy-for-me/queue` orders by `paid →
pending_quote → rejected → quoted` so the operator sees urgency first.

**Operator quote** — `POST /:id/quote` (`routes/buyForMe.js:286`).
Atomic: sets `estimate_gbp`, `markup_pct` (default 10), flips status to
`quoted`, stamps `quoted_at`, sends `sendBuyForMeQuoteEmail`.

**Customer accept (pay)** — `/accept` and `/pay` are now `410 Gone` after
the wallet rip. Customer must call `POST /api/payments` with
`target_kind='buy_for_me'` (§3).

**Customer reject** — `POST /:id/reject` requires a reason ≥3 chars.
Status flips to `rejected`; the operator queue surfaces it for re-quote.

**Settle → tracking** — when the BFM payment is marked paid (Stripe webhook
or admin M-Pesa approval), `markPaymentPaid → flipTarget('buy_for_me')`
(`utils/markPaymentPaid.js:130`):
- Sets `buy_for_me_orders.status='paid'`, stamps `decided_at`.
- Calls `maybeCreatePreRegisteredParcelForBfm` which creates an `orders` +
  `packages` row at `status='pending' / 'pre_registered'` and writes the
  back-link (`buy_for_me_orders.parcel_id`). From this point the BFM joins
  the regular parcel lifecycle.

**Admin create-on-behalf** — `POST /api/buy-for-me/admin-create` (PR 76)
lets ops create the row plus an optional pre-quote in one shot.

---

## 3. Payment system — calculation, methods, link to targets

### 3.1 Schema (migration 028)

`payments` is a per-attempt row keyed by `(target_kind, target_id)` ∈
{`order`, `consolidation`, `buy_for_me`}. Columns split into:

| group | columns |
| --- | --- |
| money | `amount_gross_kes`, `amount_credit_kes`, `amount_due_kes`, `currency` (always KES) |
| method | `method` ∈ {`stripe`, `mpesa`}, `status` ∈ {`pending`, `awaiting_review`, `paid`, `failed`, `rejected`, `cancelled`} |
| Stripe | `stripe_payment_intent_id` (UNIQUE), `stripe_charge_id`, `stripe_amount_pence_gbp`, `stripe_fx_rate_kes_gbp` |
| M-Pesa | `mpesa_message_raw`, `mpesa_reference`, `mpesa_phone`, `mpesa_message_amount_kes` |
| review | `reviewed_by`, `reviewed_at`, `rejection_reason` |

Additional tables: `user_credits` (KES balance per user), `credit_ledger`
(append-only +/- entries), `stripe_events_seen` (webhook idempotency).
RLS is enabled+forced on every new table; `supabase_realtime` publishes
`payments` and `user_credits` for live UI updates.

### 3.2 Methods + kill switches

`resolvePaymentMethods()` (`routes/payments.js:41`) reads three env vars
(`PAYMENT_METHOD_STRIPE_ENABLED`, `PAYMENT_METHOD_MPESA_ENABLED`,
`APPLE_PAY_ENABLED`). Stripe auto-enables when `STRIPE_PUBLISHABLE_KEY` is
set. Disabled methods 409 on create + are hidden by `GET /methods`.

### 3.3 Money flow

`POST /api/payments` (`routes/payments.js:110`):

1. `loadTarget()` (`routes/payments.js:498`) computes `amount_kes` per
   target_kind:
   - `consolidation`: reads `customer_consolidations.invoice_amount`. KES.
   - `buy_for_me`: reads `estimate_gbp * (1 + markup_pct/100)`, multiplies by
     live `exchange_rates.GBP_KES` (fallback 165), rounds up to KES.
   - **`order`: reads `COALESCE(actual_cost, estimated_cost, 0)::bigint AS
     amount_kes`** — see ⚠️ in §6.A. (This branch is currently unreachable
     from any UI but is exposed by the public endpoint.)
2. Idempotency: an open `pending`/`awaiting_review` payment of the same
   method is rehydrated. Switching method cancels the prior attempt
   (cancels Stripe PI server-side, marks our row `cancelled`).
3. Credit auto-applied (`FOR UPDATE` to prevent double-spend).
4. Stripe path: `stripe.paymentIntents.create` in **GBP** (UK account),
   amount = `Math.ceil(amountDueKes / gbpToKes * 100)` pence with a £0.50
   floor; idempotency-key = our payment id; metadata carries
   `payment_id, user_id, target_kind, target_id`.
5. M-Pesa path: returns Till + `payment.id` as account ref; customer pastes
   the SMS into `POST /:id/mpesa-confirmation`. `parseMpesaMessage`
   (`utils/mpesaParser.js`) extracts reference + Ksh amount + phone via regex.

### 3.4 Settlement

Single state-machine in `utils/markPaymentPaid.js`:
- Stripe webhook (`stripeWebhookHandler`, `routes/payments.js:604`) verifies
  signature, dedupes via `stripe_events_seen`, and on
  `payment_intent.succeeded` calls `markPaymentPaid`.
- Admin M-Pesa approve (`routes/adminPayments.js:35`) calls the same
  function.
- `markPaymentPaid` (idempotent on already-paid):
  1. Locks payment row, deducts consumed credit, appends ledger entry.
  2. Updates payment `status='paid'`, stamps fields.
  3. `flipTarget` updates the underlying row:
     - `order` → `orders.status='paid'`
     - `consolidation` → `customer_consolidations.status='paid'`,
       `invoice_status='paid'`, `invoice_paid_at=NOW()`
     - `buy_for_me` → `buy_for_me_orders.status='paid'` + auto-creates the
       pre-registered parcel.
  4. Post-commit, fires `sendUnifiedPaymentReceiptEmail` (best-effort).

### 3.5 Webhook idempotency

`stripe_events_seen` blocks duplicate processing. `markPaymentPaid` itself
is also idempotent (early-returns on `status='paid'`). Belt-and-braces.

---

## 4. UI surfaces per role

### 4.1 Customer (web + iOS + Android)

| Action | Web | iOS | Android |
| --- | --- | --- | --- |
| Pre-register (new order) | `NewOrder.jsx` | `NewOrderView` | `NewOrderScreen` |
| Track | `TrackPackage.jsx`, `OrderDetail.jsx` | `TrackingView`, `ParcelDetailView` | `TrackingScreen`, `ParcelDetailScreen` |
| Buy-for-me request | `BuyForMe.jsx` | `BuyForMeView` | (TBD — not in Android UI list) |
| Pay invoice | `PayInvoiceModal.jsx` (in `BuyForMe`, `OrderDetail`) | `PayInvoiceView` (from `TrackingView`/BFM) | `WalletScreen` (legacy name) |
| Submit M-Pesa SMS | `PayInvoiceModal.jsx` | `MpesaSubmitSheet` | (TBD) |
| Transactions / credit | `Transactions.jsx`, `CreditCenter.jsx` | `TransactionsView`, `CreditCenterView` | `WalletScreen` |
| Notifications | `NotificationBanner` | `NotificationInboxView` | `NotificationInboxScreen` |
| Warehouse address | `WarehouseAddresses.jsx` | `WarehouseAddressView` | (TBD) |
| Profile edit | (none separate) | `ProfileEditView` | `ProfileEditScreen` |
| Referral | `Referral.jsx` | `ReferralView` | (TBD) |
| DSAR | `DsarRequest.jsx` | `DsarView` | (TBD) |
| Quote calculator | `PricingCalculator.jsx` | `QuoteCalculatorView` | `QuoteScreen` |

Gaps:
- **Android `WalletScreen` is misnamed** — should be `TransactionsScreen` /
  `CreditScreen` (wallet was ripped in migration 028).
- Android lacks BFM, Referral, DSAR, Warehouse Address screens (called out
  in `account_parity_gaps`).

### 4.2 Operator

| Action | Web | iOS | Android |
| --- | --- | --- | --- |
| Today summary | `OpsConsole.jsx` | `OperatorTodayView` | `OperatorTodayScreen` |
| Receive parcel | `OpsConsole.jsx` modal | `OperatorReceiveView`, `OperatorScannerSheet` | `OperatorReceiveScreen`, `SkuScannerScreen` |
| Screen / hold / release | `OpsConsole.jsx` row actions | `OperatorTodayView` | (in OperatorTodayScreen) |
| Consolidation list/detail | `OpsConsolidations.jsx` | `ConsolidationListView`, `ConsolidationDetailView` | `ConsolidationListScreen`, `ConsolidationDetailScreen` |
| Last-mile dispatch | `OpsDispatch.jsx` | `DispatchView` | `DispatchScreen` |
| BFM queue + quote | `OpsBuyForMe.jsx` | `OpsBuyForMeQueueView` | (TBD — no `OpsBuyForMeScreen`) |
| Settings (admin only) | `OpsSettings.jsx` | `OpsSettingsView` | `OpsSettingsScreen` |

Gaps:
- **Android operator BFM queue missing.**
- iOS / web do not expose the per-parcel **`/screen`** (re-screen) endpoint
  outside the OpsConsole row; mobile `OperatorTodayView` doesn't have it
  either — re-screen exists only on web.

### 4.3 Admin

| Action | Web | iOS | Android |
| --- | --- | --- | --- |
| Console hub | `AdminDashboard.jsx` | `AdminDashboardView`, `RoleHomeViews` | `AdminConsoleScreen` |
| Customer-consolidations + invoices | `AdminCustomerConsolidations.jsx` | `AdminCustomerConsolidationsView` | (TBD) |
| Standalone invoice | `AdminIssueInvoice.jsx` | `AdminIssueInvoiceView` | (TBD) |
| Create BFM on behalf | `AdminCreateBuyForMe.jsx` | `AdminCreateBuyForMeView` | (TBD) |
| M-Pesa review queue | `AdminDashboard.jsx` (Payments tab) | `AdminPaymentsView` | `AdminPaymentsScreen` |
| KPI dashboard | `KpiDashboard.jsx` | `KPIDashboardView` | `KPIDashboardScreen` |
| Users CRUD | `AdminDashboard.jsx` (Users tab) | `AdminUsersView` | `AdminUsersScreen` |
| DSAR queue | `AdminDashboard.jsx` | `AdminDsarQueueView` | (TBD) |
| Audit logs | `AdminDashboard.jsx` | `AdminAuditLogsView` | (TBD) |
| Error logs | `AdminDashboard.jsx` | `AdminErrorLogsView` | (TBD) |
| Revenue export | `AdminDashboard.jsx` | `AdminRevenueView` | (TBD) |

Gaps: Android lacks 5 admin screens — see ⚠️ §6.D.

### 4.4 Clearing agent

| Action | Web | iOS | Android |
| --- | --- | --- | --- |
| Assigned consolidations | `partner/AgentPortal.jsx` | `CustomsListView` | `CustomsListScreen` |
| Per-parcel customs entry | `AgentPortal.jsx` Field grid | `CustomsListView` rows | `CustomsListScreen` |
| Mark released | `AgentPortal.jsx` | iOS path | Android path |
| My agent invoices | `AgentInvoices` (under `partner`) | `AgentInvoicesView` | `AgentInvoicesScreen` |

Gaps:
- Web `AgentPortal.jsx` has **no upload field for `doc_url`** even though the
  server accepts and exposes it (migration 005, signed URLs). iOS does — via
  the agent invoice upload flow that uses a signed URL.
- Web has no view of the **document** for invoices already submitted (no
  `agent-invoices/:id/document-url` consumer).
- No re-quote / amendment workflow once an entry is `released` — `PATCH`
  is silently allowed but no UI exposes it post-release.

### 4.5 Rider

| Action | Web | iOS | Android |
| --- | --- | --- | --- |
| Today's runs | `partner/RiderPwa.jsx` | `RiderRunView` | `RiderScaffold.kt` (only scaffold) |
| Run stops | (inline in RiderPwa) | `RunStopListView` | (TBD) |
| POD capture | `RiderPwa.jsx` (text input for photo URL) | `PodCaptureView`, `SignaturePadView` | (TBD) |
| Reissue OTP | (no UI) | (iOS path?) | (TBD) |
| Failed delivery | `RiderPwa.jsx` `prompt()` | dedicated path | (TBD) |
| Outbox (offline) | (no UI) | `OutboxView` | (TBD) |

Gaps (severe):
- **Web RiderPwa has no signed-upload integration** — it offers a "Photo URL
  (optional)" text input. The server now requires `photo_url` *or*
  `photo_path` (rejection 422 if both empty). A rider on the web PWA can't
  legally complete a POD because they have no way to upload the JPEG.
- **Web RiderPwa hard-codes "Recipient was sent a 4-digit code via
  WhatsApp."** The server actually delivers OTPs via in-app + email
  (no WhatsApp fan-out exists in `parcelStatusNotify.js`).
- **No reissue-OTP button on web.** (Server endpoint exists.)
- **No signature pad on web.** iOS captures `signature_url`/`signature_path`;
  web rider can't.
- **Android rider UI is essentially missing** — only `RiderScaffold.kt`
  exists; no run list, no POD capture, no signed upload.

---

## 5. Data integrity / state-machine summary

The codebase carries **two PackageStatus enums** that have to stay
synchronised:

- `orders.status` (legacy v1): `pending`, `received_at_warehouse`,
  `consolidating`, `in_transit`, `customs`, `out_for_delivery`,
  `delivered`, `cancelled`, plus `paid` (added by migration 028's
  `flipTarget`).
- `packages.status` (v2 enum, migration 002): includes `pre_registered`,
  `manifested`, `held_at_nairobi_hub` etc.

The fan-out points (receive, screen, assign-parcel, remove-parcel,
activateRunDispatch, POD success, POD second-fail) all carefully update
**both** rows. This is good but fragile — see ⚠️ §6.B.

---

## 6. Findings — broken logic, data risks, improvement targets

Severity legend: **🔴 critical**, **🟠 high**, **🟡 medium**, **🔵 low/cleanup**.

### 6.A 🔴 Order-payment amount uses GBP value as KES

`routes/payments.js:498-507` — `loadTarget('order')` does:

```sql
SELECT COALESCE(actual_cost, estimated_cost, 0)::bigint AS amount_kes
```

But `orders.estimated_cost` is set by `routes/orders.js:104` from
`calculateShippingCost(...).total` — which `utils/pricing.js:202` returns in
**GBP** (`currency: 'GBP'`). `actual_cost` is stamped by admin via `PUT
/orders/:id/status` with no documented unit (`routes/orders.js:269`).

If anyone ever calls `POST /api/payments {target_kind:'order', target_id:…}`
the customer is billed in KES the GBP amount (≈165× under-charge). Currently
**no UI hits this branch** (iOS `PayInvoiceView` is only invoked with
`buy_for_me` and `consolidation` — `TrackingView.swift:839,849`), but the
endpoint is publicly exposed and authenticated — not a safe latent bug.

**Fix options:**
- Drop `target_kind='order'` from the CHECK constraint and from `loadTarget`,
  since the live payment flow only goes through customer-consolidation
  invoices.
- Or, if direct order-pay is wanted: convert at the live FX rate, mirroring
  the BFM branch (`amount_kes = round(estimated_cost_gbp * gbpToKes)`), and
  document `actual_cost` as KES.

### 6.B 🟠 Customs `released` skips the last-mile run

`routes/customs.js:158-168` — when an agent flips an entry to `released`,
the server does:

```js
UPDATE orders SET status = 'out_for_delivery'
```

But `out_for_delivery` is the state `activateRunDispatch` flips to **only**
once the operator assigns a rider to a planned run. By skipping straight
to it from customs, the parcel:
- bypasses `last_mile_run_parcels` insertion → it is invisible to the
  dispatch board (`/api/last-mile/dispatch` filters parcels at
  `status='customs'`).
- has no `pod_otps` row → the rider's POD attempt would 400 with
  `otp_not_issued`.
- shows up in the customer's tracking as out-for-delivery without an OTP
  notification.

**Fix:** the released cascade should leave `orders.status='customs'`
(it's already off the customs queue once the entry row is `released`)
and leave the dispatch board to pick it up. Or: add a separate
`customs_cleared` state, surface that on the dispatch board, and only
flip to `out_for_delivery` when `activateRunDispatch` runs.

### 6.C 🟠 M-Pesa SMS approval has no amount/reference matching enforcement

`routes/adminPayments.js:35` simply calls `markPaymentPaid` on /approve.
There is no server-side check that `payments.mpesa_message_amount_kes ===
payments.amount_due_kes`, no check that the reference is unique across
payments, and no check that the SMS sender phone matches a known customer.

The webapp `AdminDashboard.jsx:851` *visually* renders a red "mismatch"
chip but nothing prevents the admin from clicking Verify. A genuine misread
or a malicious paste of someone else's SMS settles the wrong invoice.

**Fix proposals (cheapest first):**
1. Reject `/approve` when `mpesa_message_amount_kes < amount_due_kes` unless
   the admin sends an `override_reason`.
2. Add UNIQUE on `payments(mpesa_reference) WHERE mpesa_reference IS NOT
   NULL` so the same SMS can't be reused.
3. Migrate to Daraja STK / C2B confirmation callback so the matching is
   automated and the admin queue becomes a fallback only.

### 6.D 🟠 Webapp Rider PWA is non-functional after the storage hardening

`client/src/pages/partner/RiderPwa.jsx` uses an `<input placeholder="Photo
URL (optional)">` for the POD photo. After migration 015 the bucket went
private and `routes/lastMile.js:962-967` rejects POD attempts with no
`photo_url` *or* `photo_path` (422 `photo_required`). A web-only rider has
no way to upload — they would either:
- Type a public URL pointing to a Supabase path that won't authorise on
  read, or
- Submit blank and get 422.

The page also tells riders "Recipient was sent a 4-digit code via WhatsApp"
which is incorrect (in-app + email).

**Fix:** port the iOS signed-upload pattern to web — `POST
/last-mile/pod/upload-url`, use `fetch(signed_url, {method:'PUT'})`, then
post `photo_path` plus signature canvas (`<canvas>`-to-PNG → same upload
flow). Remove the WhatsApp string.

### 6.E 🟠 Android rider + several admin screens absent

Android has full operator + customer + admin-payments + KPI parity but
lacks: rider run list / POD, admin customer-consolidations, admin
issue-invoice, admin create-BFM, admin DSAR / audit / error / revenue, BFM
operator queue, BFM customer screen, Referral, DSAR (customer), Warehouse
address. (`account_parity_gaps` covers the customer-side; the staff side
isn't in that doc.)

### 6.F 🟡 Order pre-registration creates a `packages` row before weight is known

`routes/orders.js:109` writes `packages(weight_kg=null, status='pre_registered')`.
`pre_registered` is only allowed under the v2 enum (migration 002). However
the legacy `orders.status` starts at `'pending'`, which a re-screen call
later forces `packages.status='pending'` (`routes/ops.js:186`) — that is
**not a valid v2 PackageStatus** value. A `screen→held` then `release` then
re-screen would leave `packages` in a broken state on databases that
upgraded the enum.

**Fix:** map screen→held to the v2 value (`held_at_origin_hub` or similar)
and stop writing `'pending'` to packages from `routes/ops.js:186`.

### 6.G 🟡 No FX rate snapshot on the consolidation invoice

`PATCH /customer-consolidations/:id/invoice` records `invoice_amount` (KES)
but not the GBP→KES rate that produced it. The `suggested-invoice` endpoint
*is* recompute-friendly, but if pricing tier rates change between the
suggestion and the issuance, the customer's invoice doesn't tie back to
auditable inputs. **Fix:** store `gbp_to_kes_rate`, `pricing_tier_version`,
and the per-parcel breakdown JSON on `customer_consolidations` for the
audit trail.

### 6.H 🟡 Stripe FX rate has a hard-coded fallback of 165

`routes/payments.js:240` and `:531` both `Number(rr[0]?.rate || 165)`. If the
`exchange_rates` row is missing (fresh DB, deleted by accident, an enum
mismatch), payments silently use 165 — a 6%+ deviation from current spot.
Combined with the £0.50 floor + ceil-to-pence, this materially miscalculates
and the customer has no way to dispute. **Fix:** raise an explicit 503 when
the FX row is absent, surface "Card payments temporarily unavailable" in
the client.

### 6.I 🟡 Order auto-create from BFM uses `orders.status='pending'` and `'general'` HS tier

`utils/markPaymentPaid.js:177` writes the parcel at status `pending` (legacy
enum) with `weight_kg=NULL`, `estimated_cost=0`, `hs_tier='general'`. The
operator has to inspect each BFM-spawned parcel to retag the HS tier, but
the iOS receive flow is the only place that lets you stamp the tier. There
is no admin "set HS tier" UI on the parcel detail.

Practical effect: every BFM parcel mis-quotes customs at receive. **Fix:**
add a tier picker on `OperatorReceiveView` / web `OpsConsole` modal (it's
already in the calculator, just not on receive).

### 6.J 🟡 Tracking-number generator is weak for BFM auto-create

`utils/markPaymentPaid.js:191` reuses the `crypto.randomBytes(4)` pattern
but does not enforce uniqueness against `orders.tracking_number`. Collisions
are astronomically unlikely (~4B/day) but the table has no UNIQUE constraint
on `tracking_number` (or none I could see). **Fix:** add UNIQUE +
ON CONFLICT-retry, mirroring the customer create.

### 6.K 🔵 `orders.status` enum drift

The `validStatuses` array in `routes/orders.js:272` does NOT include
`'paid'`, `'manifested'`, or any of the customs/last-mile transitional
states it actually accepts elsewhere. Admin attempting to fix a bad row via
`PUT /:id/status` cannot put it back into `paid` (rejected as 400) but the
DB obviously contains it. **Fix:** sync the validator with the actual enum.

### 6.L 🔵 Invoice "currency" is a free string

`customer_consolidations.invoice_currency` accepts any text (default `'KES'`).
There's nothing stopping an admin from setting it to `'USD'` while the rest
of the system assumes KES. **Fix:** CHECK constraint to `('KES','GBP')`,
and reject non-KES targets in `loadTarget`.

### 6.M 🔵 Documentation drift in `customer-consolidations` notify

`routes/customerConsolidations.js:55` template strings render
`customer_consolidations.invoice_amount.toLocaleString()` — Postgres
returns it as a `Number` but bigint shipping invoices may wrap. Currently
amounts < 2^53 are fine but worth a guard.

### 6.N 🔵 RiderPwa doesn't show recipient address overrides

The dispatch can override `delivery_address` per parcel (server stores in
`last_mile_run_parcels.delivery_address_override`). iOS RunStopListView
does read it; web `RiderPwa.jsx` only renders `p.delivery_address` (the
user's profile address). A rider on web sees the wrong drop point.

---

## 7. Recommended improvements (priority-ordered)

1. **🔴 Disable / fix the `target_kind='order'` payment branch** (6.A).
2. **🟠 Enforce M-Pesa amount/reference matching on `/approve`** (6.C) and
   add Daraja STK push to remove the manual paste path entirely.
3. **🟠 Stop the customs-released → out_for_delivery shortcut** (6.B). Add
   `status='customs_cleared'` state, surface on dispatch board.
4. **🟠 Rebuild the Webapp RiderPwa POD** with signed uploads + signature
   canvas + reissue OTP button (6.D), or deprecate it in favour of the iOS
   app.
5. **🟠 Ship Android rider + remaining admin screens** (6.E).
6. **🟡 Persist FX + pricing snapshot on consolidation invoices** (6.G) and
   harden the FX fallback (6.H).
7. **🟡 Map `screen→held` to v2 enum on packages** (6.F) and tighten the
   `validStatuses` validator (6.K).
8. **🟡 Add HS tier picker to operator receive** (6.I) so BFM-spawned
   parcels can be re-tagged at intake.
9. **🟡 UNIQUE on `orders.tracking_number` + retry-on-conflict** (6.J).
10. **🔵 CHECK on `invoice_currency`, address-override rendering on web
    rider, drift cleanups** (6.L–6.N).

---

## 8. Strengths worth preserving

- Single `markPaymentPaid` state-machine for both Stripe and M-Pesa — same
  side-effects, no per-method drift.
- Stripe webhook idempotency via `stripe_events_seen` + `markPaymentPaid`'s
  own already-paid early-return.
- Per-payment row keyed by `(target_kind, target_id)` — allows multi-attempt
  history, supersession, and the "earlier attempt" UX in `Transactions.jsx`
  / `TransactionsView`.
- Method kill-switch matrix surfaces via `/methods` so the client UI hides
  unsupported buttons rather than failing on submit.
- Last-mile state machine: `FOR UPDATE` lock, explicit transition table,
  one OTP per recipient (not per parcel).
- POD assets behind a **private** bucket with mandatory signed-URL
  download, scoped to the rider on an active run.
- RLS enabled+forced on every payments-era table; helpers read JWT instead
  of querying users (avoids recursion).
- Auto-creation of a pre-registered parcel when a BFM is paid — customer
  immediately sees a tracking row.

---

*Report generated 2026-05-03 by walking the codebase end to end. No
information from auto-memory was used; everything cited has a file:line
anchor.*
