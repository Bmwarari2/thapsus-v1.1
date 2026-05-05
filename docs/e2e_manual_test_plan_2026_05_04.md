# End-to-End Manual Test Plan — Customer-to-Delivery Lifecycle

**Date:** 2026-05-04
**Scope:** Live system (Railway-hosted Express + Supabase project `zzdfxsfuhosuqvsugtfd`) immediately after the test-data purge of 2026-05-04. Users + retailers + pricing tiers are intact; everything transactional (orders, packages, payments, consolidations, runs) starts at zero.
**Goal:** Walk every persona through the full happy path so we know there is at least one round-trip per critical surface before we hand the app to real customers. Failures are filed as audit IDs; this plan does not double as a regression suite.

---

## Pre-flight

- [ ] **Storage buckets** — confirm `parcels`, `pods`, `agent-invoices` are private and have signed-URL upload working. (Check via `Studio → Storage → bucket → Settings`.)
- [ ] **Email pipeline** — Gmail OAuth still healthy. Hit `GET /api/admin/email-config` as an admin and look for `transporter: ok`.
- [ ] **Stripe test mode** — `STRIPE_SECRET_KEY` on Railway points at the `sk_test_…` key, not live. Cards used below are Stripe's standard 4242-series.
- [ ] **Webhooks** — `stripe webhook listen` (or the Railway CLI) tailing the live `/api/payments/stripe/webhook` so we can see PaymentIntent transitions in real time.
- [ ] **Devices** — one iPhone (customer + admin sign-in to test admin-on-iOS flows), one browser session per non-customer role (operator, clearing agent, rider, admin webapp).
- [ ] **Test accounts** — pre-seed/keep one of each: `customer@thapsus.test`, `operator@thapsus.test`, `agent@thapsus.test`, `rider@thapsus.test`, `admin@thapsus.test`. Confirm each can log in *before* you start, so you don't burn time mid-flow on a credentials issue.
- [ ] **Reset device state** — on iOS, sign out + delete the app + reinstall once before you start so SQLDelight cache is empty and Settings/Keychain are clean. (`shared/local-cache.db` is per-install.)

---

## Persona pass 1 — Customer (signup → quote → submit → pay)

> Use the **iOS app** for the entire customer flow. Re-run the order-create + payment steps in the **webapp** afterwards so we have parity coverage for the customer surface only — every other persona gets one platform each.

### 1. Account creation

1. [ ] Tap **Create an account** on `SignInView`.
2. [ ] Fill name / email / phone / password / country of residence (UK), submit.
3. [ ] **Expected:** Welcome / setup-account email arrives at the inbox; deep link bounces back into the iOS app and lands on `PasswordResetView` so the user can confirm their password.
4. [ ] Confirm the row in `users` (`SELECT id, email, role, created_at FROM users WHERE email='customer@thapsus.test';`) — role should be `customer`.
5. [ ] Sign out, sign back in, check the dashboard renders.

### 2. Profile + warehouse address

1. [ ] Account → **Edit profile** → change name + delivery address + language preference. Save.
2. [ ] Re-open Account, confirm fields persisted.
3. [ ] Check `users` row — `delivery_address`, `language_pref`, `name` updated; `updated_at` advanced.

### 3. Quote calculator

1. [ ] Open the calculator. Punch in 2.5 kg, 30×20×15 cm, HS tier *General*.
2. [ ] **Expected:** Subtotal renders in £ + KSh, with breakdown (per-kg + handling + insurance excluded).
3. [ ] Toggle HS tier to *Electronics* — total should jump (different `gbp_per_kg`).

### 4. Buy-for-me request

1. [ ] **New order → Buy for me**.
2. [ ] Pick a retailer from the catalog list (`retailers` table seeded). Add product URL, item description, customer note. Submit.
3. [ ] **Expected:** confirmation banner with `BFM-XXXXXXXX` reference; entry visible under **My orders → Buy for me** with status `quote_pending`.
4. [ ] Server should auto-create a `notifications` row + an email to admin@. Verify both.

### 5. Standard order (UK→Kenya parcel)

1. [ ] **New order → Send a parcel**. Use a test sender + the seeded recipient.
2. [ ] **Expected:** order appears under **My orders → Active**, status `pending`, with a `TC-YYYYMMDD-XXXXXXXX` tracking number. Server email "Order received" lands in inbox.
3. [ ] Note the order id — you will receive it at the warehouse in the next persona pass.

### 6. Payment (Stripe)

1. [ ] Hit **Pay now** on a pending invoice (whichever surface offers it first — likely after the parcel is weighed + invoiced; if no invoice yet, skip and come back after Persona 2).
2. [ ] Use card `4242 4242 4242 4242`, any future expiry, any CVC.
3. [ ] **Expected:** Payment succeeds, order detail flips to `paid`, receipt email arrives.
4. [ ] Verify in `payments` table: row with `target_kind='order'`, `target_id=<order.id>`, `status='succeeded'`, `currency='GBP'`. Single row, not duplicates.

### 7. Payment (M-Pesa, manual approval path)

1. [ ] Repeat step 6 on a different parcel using **Pay with M-Pesa** → "I have already paid" → paste a fake confirmation SMS body.
2. [ ] **Expected:** A `payments` row created with `status='pending_review'`, plus an admin notification.
3. [ ] As admin: approve in **Admin → Payments review**. Order should flip to `paid`.

### 8. Tracking + notifications

1. [ ] Open the public tracking link (`https://thapsus.uk/track/<TC-...>`) in a browser while signed-out.
2. [ ] **Expected:** Status shown read-only with timeline.
3. [ ] In the iOS app, confirm Inbox shows status-update notifications as the parcel progresses.

---

## Persona pass 2 — Operator (warehouse intake)

> Webapp + iOS both have intake — use the **iOS app** so the SKU scanner gets exercised. Webapp gets re-tested on the operator screen at the end.

### 1. Pre-registration → physical receive

1. [ ] Stick a printed STK label on a real parcel (matching the customer order from Persona pass 1, step 5).
2. [ ] Open **Operator → Scan** → camera scanner reads the STK code.
3. [ ] **Expected:** Routes to the **Receive** sheet because the package is in `pre_registered` state.
4. [ ] Fill weight (kg), dims (cm), HS tier, photo upload (POD-photo or condition photo). Submit.
5. [ ] **Expected:** Status flips to `received_at_warehouse`. Customer gets a "Parcel received" email + push.
6. [ ] Verify in `packages` table: weight/dims/`hs_tier` populated; photo path written to `parcels` storage bucket via signed URL (no base64 in DB).

### 2. Photographed → weighed → screened pipeline

1. [ ] Use **Operator console** webapp to walk the same package through `photographed → weighed → screened → manifested`. Each transition should fire `parcelStatusNotify` (in-app + email + SSE).

### 3. Consolidation build

1. [ ] **Operator → Consolidations → New**. Bundle 2–3 packages into one consolidation manifest.
2. [ ] Print manifest PDF. Confirm packages flip to status `consolidating` then `manifested`.

### 4. Customer-facing consolidation invoice (admin standalone)

1. [ ] **Admin (iOS or webapp) → Issue invoice** on a consolidation. Pick currency (GBP), enter amount, submit.
2. [ ] Customer receives invoice email + can pay it via the same `/api/payments` flow as a parcel invoice.
3. [ ] After payment, confirm `customer_consolidations.invoice_status='paid'` and the corresponding `payments` row exists (`target_kind='consolidation'`).

---

## Persona pass 3 — Clearing Agent (JKIA customs)

> Webapp.

1. [ ] Sign in as `agent@thapsus.test`.
2. [ ] **Customs queue** — see in-flight consolidations awaiting clearance.
3. [ ] Select one, upload agent invoice PDF (signed-URL upload — confirm bucket entry under `agent-invoices/<id>.pdf`).
4. [ ] Mark `awaiting_duty_payment`. Confirm parcels under that consolidation flip status accordingly + notify customer.
5. [ ] After customer pays duty (drive via Persona 1 step 6 again), agent marks `released`.

---

## Persona pass 4 — Rider (last-mile dispatch)

> **Rider PWA** at `/partner/rider`. (PR #84 added signed-URL POD upload; verify it's the merged path.)

1. [ ] Admin webapp → **Dispatch → Plan run**. Add 2 released parcels to a new run, assign rider. Mark run `in_progress`.
2. [ ] Rider opens PWA, sees today's run with stops in order.
3. [ ] At each stop:
   - [ ] Tap **Issue OTP** (customer SMS arrives with 6-digit code).
   - [ ] Enter OTP from customer, capture POD photo via camera, submit.
   - [ ] **Expected:** parcel flips to `delivered`; `pods` bucket has a new file; customer gets "Delivered" email + receipt.
4. [ ] Try the diagnostic-code paths intentionally:
   - Wrong OTP → toast "OTP invalid" (`otp_invalid`).
   - Missing photo → toast "Photo required" (`photo_required`).
5. [ ] Complete all stops → run flips to `completed`.

---

## Persona pass 5 — Admin (oversight + cleanup)

> Webapp primarily; iOS for any admin screens already shipped (admin BFM creation, issue-invoice flow already covered above).

1. [ ] **Admin → Users**: confirm the new customer is listed; can open detail and see their orders / payments.
2. [ ] **Admin → Orders**: filters work (status, date, customer). Open the journey-completed parcel; status timeline shows every transition with timestamps.
3. [ ] **Admin → Payments**: shows the Stripe + M-Pesa rows from Persona 1 step 6/7. Refund test on the Stripe one (Stripe test-mode refund) — confirm payment row flips to `refunded` and customer gets refund email.
4. [ ] **Admin → Pricing tiers**: open editor, change one tier's `gbp_per_kg`, save. Verify next quote (run Persona 1 step 3 again) reflects the new rate.
5. [ ] **Admin → Error logs**: confirm zero `severity='critical'` rows from this run; investigate any that appear before signing off.
6. [ ] **NPS auto-prompt** — once a parcel hits `delivered`, the iOS NpsAutoPromptModifier should surface the survey on the customer's next dashboard visit. Tap **Submit** with score 9 → confirm `nps_responses` row + `nps_invitations.responded_at` filled.

---

## Cross-cutting checks (run continuously, not as a discrete step)

- [ ] **Realtime** — operator console + customer dashboard both update without refresh when a status flips. (`supabase_realtime` publication needs the affected table.)
- [ ] **Email deliverability** — sample 3 emails in Gmail, look for SPF/DKIM pass + no spam-folder routing.
- [ ] **Webhook retries** — Stripe dashboard shows the `/api/payments/stripe/webhook` returning 200 on every event; no orange retries.
- [ ] **Storage hygiene** — every photo / invoice you uploaded above should be in the right private bucket with a UUID filename (no leaked PII in path).

---

## Sign-off

| Persona | Tested by | Date | Notes |
|---|---|---|---|
| Customer | | | |
| Operator | | | |
| Clearing agent | | | |
| Rider | | | |
| Admin | | | |

If everything above is green, the system is ready for the first cohort of real customers. Anything red gets filed under `docs/parcel_payment_audit_2026_05_04.md` (or follow-up audit) before we open the doors.
