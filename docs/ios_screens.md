# iOS App — Screen Inventory

A complete catalogue of every SwiftUI screen that ships in the Thapsus Cargo
iOS app, grouped by audience. Source of truth: `iosApp/iosApp/**` on
`claude/document-ios-screens-DeGNu`.

Each entry lists:
- the SwiftUI view struct
- file path (relative to repo root)
- audience / role gate
- what the user does there
- how the screen is reached (tab bar, deep link, NavigationLink, sheet…)
- notable sub-views / sheets declared in the same file

---

## 1. Navigation overview

The app boots into `RootView` (`iosApp/iosApp/RootView.swift`), which decides
between three top-level surfaces based on the Kotlin `AuthSession` flow:

| Auth state            | Surface shown                |
|-----------------------|------------------------------|
| `Initializing`        | `SplashView` (gradient + wordmark) |
| Unauthenticated       | `SignInView`                 |
| Authenticated         | `RootTabView(role:)`         |

`RootTabView` is **role-aware** — it builds a different `TabView` for each
`UserRole`. Every tab wraps its root in a `NavigationStack` so drill-down
screens stack within the tab.

### 1.1 Tab bar by role

| Role           | Tab 1                         | Tab 2                       | Tab 3                            | Tab 4                       | Tab 5                       |
|----------------|-------------------------------|-----------------------------|----------------------------------|-----------------------------|-----------------------------|
| Customer       | Home → `CustomerDashboardView`| Orders → `TrackingView`     | Activity → `CustomerActivityHubView` | Quote → `QuoteCalculatorView` | Account → `CustomerHomeView` |
| Operator       | Today → `OperatorTodayView`   | Receive → `OperatorReceiveView` | Consols → `ConsolidationListView` | Dispatch → `DispatchView`   | Account → `OperatorHomeView` |
| Clearing agent | Customs → `CustomsListView`   | Invoices → `AgentInvoicesView` | Account → `AgentHomeView`        | —                           | —                           |
| Rider          | Today → `RiderRunView`        | Outbox → `OutboxView`       | Account → `RiderHomeView`        | —                           | —                           |
| Admin          | Console → `AdminDashboardView`| KPI → `KPIDashboardView`    | Customer → `AdminCustomerConsolidationsView` | Shipping → `ConsolidationListView` | Account → `AdminHomeView` |

### 1.2 Universal links & custom URL schemes

`RootView` also routes inbound deep links — these surface as modal sheets
regardless of which tab is active:

| Pattern                                              | Opens                                  |
|------------------------------------------------------|----------------------------------------|
| `thapsus://pay/<orderId>`                            | `PublicPaymentView`                    |
| `https://thapsus.uk/pay/<orderId>`                   | `PublicPaymentView`                    |
| `https://thapsus.uk/track/<id>`                      | `TrackingView` (prefilled)             |
| `https://thapsus.uk/orders/<id>`                     | `ParcelDetailView`                     |
| `https://thapsus.uk/reset-password?token=<hex64>`    | `PasswordResetView`                    |

`<orderId>` accepts UUIDs and `TC-YYYYMMDD-<hex>` tracking numbers; tokens
must be the 64-char hex string minted server-side.

---

## 2. Public / unauthenticated screens

### `SignInView`
- **File:** `iosApp/iosApp/Features/SignInView.swift`
- **Audience:** Public
- **Purpose:** Real Supabase Auth entry. Sign in with email + password or
  create a new account. Phase 3 removed the dev role-pick dialog, so this is
  the single front door.
- **Entry points:** `RootView` fallback when `AuthSession` is unauthenticated.
- **Notable sub-views:** Form fields driven by `AuthViewModel`.

### `PasswordResetView`
- **File:** `iosApp/iosApp/Features/PasswordResetView.swift`
- **Audience:** Public
- **Purpose:** Universal-link landing for both password-reset and welcome /
  setup-account emails. Captures a new password and POSTs to
  `/api/auth/reset-password`.
- **Entry points:** Sheet presented from `RootView` when
  `https://thapsus.uk/reset-password?token=…` is opened.

### `PublicPaymentView`
- **File:** `iosApp/iosApp/Features/PublicPaymentView.swift`
- **Audience:** Public (anyone holding the link)
- **Purpose:** Pay a duty/customs invoice or order quote without signing in.
  Hands off to Safari to complete payment on `thapsus.uk`.
- **Entry points:** Sheet presented from `RootView` for `thapsus://pay/<id>`
  or `https://thapsus.uk/pay/<id>`. Also linked from `ParcelDetailView` when
  status is `awaitingDutyPayment`.

---

## 3. Customer screens

### `CustomerDashboardView`
- **File:** `iosApp/iosApp/Features/CustomerDashboardView.swift`
- **Audience:** Customer
- **Purpose:** Home tab. Welcome message, warehouse-address terminal with
  the customer's routing reference, quick stats, FAB-style quick actions
  ("New order" / "Buy for me"), and a collapsible 4-step "How it works"
  guide.
- **Entry points:** Customer tab #1 ("Home").
- **Notable sub-views:** `CutoffBannerView` (next-flight countdown), action
  menu overlay, `NotificationBannerView` overlay.

### `TrackingView`
- **File:** `iosApp/iosApp/Features/TrackingView.swift`
- **Audience:** Customer
- **Purpose:** Orders & tracking. Live tracking-number lookup, list of
  in-flight shipments backed by the SQLDelight cache + Realtime updates,
  Phase 2 invoice consolidations, and Buy-for-me requests in
  `pending_quote` / `quoted` state.
- **Entry points:** Customer tab #2 ("Orders"); deep link
  `https://thapsus.uk/track/<id>` (sheet from `RootView`).
- **Notable sub-views:** "New order" / "Buy for me" FAB menu, `PayInvoiceView`
  sheet (BFM accept / consolidation invoice), `RejectQuoteSheet`, nested
  `NewOrderView` and `BuyForMeView` flows.

### `CustomerActivityHubView`
- **File:** `iosApp/iosApp/Features/CustomerActivityHubView.swift`
- **Audience:** Customer
- **Purpose:** Activity tab. Hub that surfaces the three financial activity
  surfaces — Invoices, Transactions, Buy-for-me — that were previously
  buried inside other screens.
- **Entry points:** Customer tab #3 ("Activity").
- **Notable sub-views:** `HubCard` rows linking to `TrackingView`,
  `TransactionsView`, `BuyForMeView`.

### `QuoteCalculatorView`
- **File:** `iosApp/iosApp/Features/QuoteCalculatorView.swift`
- **Audience:** Customer
- **Purpose:** Customer-facing shipping calculator. Calls the same
  `QuoteEngine` the server uses, so the number quoted matches the final
  charge. Toggles for phone (£75) and laptop (£65) surcharges.
- **Entry points:** Customer tab #4 ("Quote").

### `CustomerHomeView`
- **File:** `iosApp/iosApp/Features/CustomerHomeView.swift`
- **Audience:** Customer
- **Purpose:** Account / "More" tab. Discoverable list linking every Phase 1
  customer feature: warehouse, new order, buy-for-me, prohibited items,
  notifications, support, referrals, profile edit, data rights.
- **Entry points:** Customer tab #5 ("Account").
- **Notable sub-views:** `CutoffBannerView`, `NotificationBannerView`
  overlay, `WhatsAppSupportButton`, sign-out button.

### `NewOrderView`
- **File:** `iosApp/iosApp/Features/NewOrderView.swift`
- **Audience:** Customer
- **Purpose:** Multi-step parcel creation wizard — market picker, retailer,
  description, declared value, dimensions, HS category, weight. Mirrors the
  webapp flow but as a vertical iOS form with a progress bar.
- **Entry points:** "New order" buttons on `CustomerDashboardView`,
  `TrackingView`, and `CustomerHomeView`.
- **Notable sub-views:** `ProcessStepsCard`, status banner on submit.

### `BuyForMeView`
- **File:** `iosApp/iosApp/Features/BuyForMeView.swift`
- **Audience:** Customer
- **Purpose:** Concierge purchase flow. Submit a retailer link, get a quote,
  optionally reject with reason, then accept and pay. Backed by
  `BuyForMeViewModel` observing order status.
- **Entry points:** "Buy for me" buttons on `CustomerDashboardView`,
  `TrackingView`, `CustomerActivityHubView`.
- **Notable sub-views:** `CreateBuyForMeSheet`, `RejectQuoteSheet`.

### `ParcelDetailView`
- **File:** `iosApp/iosApp/Features/ParcelDetailView.swift`
- **Audience:** Customer
- **Purpose:** Drill-down for a single parcel. Status timeline, volumetric-
  weight breakdown, POD details (photo + signature) for delivered parcels,
  consolidation linkage, duty-payment banner if awaiting payment.
- **Entry points:** NavigationLink from dashboard list / `TrackingView`;
  deep link `https://thapsus.uk/orders/<id>` (sheet from `RootView`).
- **Notable sub-views:** Link to `CustomerConsolidationView` when
  `consolidation_id` is set; link to `PublicPaymentView` when
  `awaitingDutyPayment`; POD card with images.

### `CustomerConsolidationView`
- **File:** `iosApp/iosApp/Features/CustomerConsolidationView.swift`
- **Audience:** Customer
- **Purpose:** Read-only weekly-flight summary for parcels assigned to a
  consolidation: status, week start, cut-off, departure, parcel count,
  total kg.
- **Entry points:** NavigationLink from `ParcelDetailView`.

### `ClientTerminalView`
- **File:** `iosApp/iosApp/Features/ClientTerminalView.swift`
- **Audience:** Customer
- **Purpose:** Editorial card showing the Stockport warehouse address with
  the customer's routing reference (`THP-XXXXXX`) for copy-to-clipboard,
  plus a how-to-ship guide.
- **Entry points:** Linked from `CustomerHomeView` "Warehouse".

### `WarehouseAddressView`
- **File:** `iosApp/iosApp/Features/WarehouseAddressView.swift`
- **Audience:** Customer
- **Purpose:** UK warehouse address card + how-to-ship guide. Rolls the
  webapp dashboard's warehouse-address terminal and the standalone
  ShipInstructions page into one scrollable view, with the 4-step
  "How it works" guide nested.
- **Entry points:** NavigationLink from `CustomerHomeView` "Warehouse".
- **Notable sub-views:** Nested `HowItWorksView`.

### `HowItWorksView`
- **File:** `iosApp/iosApp/Features/HowItWorksView.swift`
- **Audience:** Customer (shared component)
- **Purpose:** 4-step "How it works" workflow card stack mirroring the
  webapp Home page explainer.
- **Entry points:** Nested in `CustomerDashboardView` (collapsible) and
  `WarehouseAddressView`. Not navigated to directly.

### `ProhibitedSearchView`
- **File:** `iosApp/iosApp/Features/ProhibitedSearchView.swift`
- **Audience:** Customer
- **Purpose:** Category list of restricted / dangerous goods served from
  `/api/prohibited/categories`. Tap to expand items in a sheet; supports
  free-text search.
- **Entry points:** NavigationLink from `CustomerHomeView` "Prohibited
  items".
- **Notable sub-views:** `CategoryDetailSheet`.

### `TransactionsView`
- **File:** `iosApp/iosApp/Features/TransactionsView.swift`
- **Audience:** Customer
- **Purpose:** Transaction history with two tabs: **Payments** (paginated
  card / M-Pesa payments) and **Credit** (referral-credit ledger).
- **Entry points:** NavigationLink from `CustomerActivityHubView`,
  `CreditCenterView`, or any payment-related screen.

### `CreditCenterView`
- **File:** `iosApp/iosApp/Features/CreditCenterView.swift`
- **Audience:** Customer
- **Purpose:** Running KES credit balance from referrals, an explainer of
  how it auto-deducts on the next payment, and a link into
  `TransactionsView` for the full ledger.
- **Entry points:** NavigationLink from the customer profile / credit
  section.

### `PayInvoiceView`
- **File:** `iosApp/iosApp/Features/PayInvoiceView.swift`
- **Audience:** Customer
- **Purpose:** Customer payment flow for shipping invoices, consolidations,
  and Buy-for-me orders. Shows gross, applied credit, net due (KES). User
  picks Card (Stripe Payment Sheet) or M-Pesa (Till + reference + paste-SMS
  flow).
- **Entry points:** Sheet from `TrackingView` (BFM accept / consolidation
  invoice), `NewOrderView`, deep-linked invoice screens.
- **Notable sub-views:** `MpesaSubmitSheet`.

### `MpesaSubmitSheet`
- **File:** `iosApp/iosApp/Features/MpesaSubmitSheet.swift`
- **Audience:** Customer
- **Purpose:** Customer pastes their M-Pesa confirmation SMS after paying.
  Server parses reference + amount + sender phone and flips payment to
  `awaiting_review`. Also displays Till, reference, amount for copy.
- **Entry points:** Sheet presented from `PayInvoiceView`.

### `ReferralView`
- **File:** `iosApp/iosApp/Features/ReferralView.swift`
- **Audience:** Customer
- **Purpose:** Referral hub. User's code, total earnings, pending vs.
  completed counts, and a feed of referees with status.
- **Entry points:** NavigationLink from `CustomerHomeView` "Referrals".

### `NotificationInboxView`
- **File:** `iosApp/iosApp/Features/NotificationInboxView.swift`
- **Audience:** Customer (shared with all roles)
- **Purpose:** Notifications feed for the signed-in user. Mirrors the
  webapp's NotificationBanner inbox with mark-read and mark-all-read
  actions.
- **Entry points:** NavigationLink from "Notifications" in every role-home
  view.

### `NotificationBannerView`
- **File:** `iosApp/iosApp/Features/NotificationBannerView.swift`
- **Audience:** Customer (shared component)
- **Purpose:** Transient banner that observes the live notification stream
  and pops the latest unseen row over the host screen for ~5 seconds.
  Tapping marks it read.
- **Entry points:** Mounted as overlay on `CustomerDashboardView` and
  `CustomerHomeView`.

### `CutoffBannerView`
- **File:** `iosApp/iosApp/Features/CutoffBannerView.swift`
- **Audience:** Customer (shared component)
- **Purpose:** Glass banner with live countdown to the upcoming flight
  cut-off. Hides itself when no consolidation is open.
- **Entry points:** Mounted on `CustomerDashboardView` and
  `CustomerHomeView`.

### `NpsSurveyView`
- **File:** `iosApp/iosApp/Features/NpsSurveyView.swift`
- **Audience:** Customer
- **Purpose:** One-tap post-delivery survey (0–10 NPS score + optional
  comment).
- **Entry points:** Auto-presented as a sheet by `NpsAutoPromptModifier`.

### `NpsAutoPromptModifier`
- **File:** `iosApp/iosApp/Features/NpsAutoPromptModifier.swift`
- **Audience:** Customer
- **Purpose:** View modifier (`.npsAutoPrompt()`) that watches the package
  stream; when a parcel transitions to "delivered" and `/nps/pending`
  reports an open survey, it presents `NpsSurveyView`.
- **Entry points:** Applied as a modifier on customer-facing screens such
  as `CustomerHomeView`.

### `ProfileEditView`
- **File:** `iosApp/iosApp/Features/ProfileEditView.swift`
- **Audience:** Customer (and all other authenticated roles via
  `RoleHomeViews`)
- **Purpose:** Edit profile (name, phone, language, delivery address) and
  change password. Also exposes a "Forgot password" link.
- **Entry points:** "Edit profile" link in every role-home view.

### `DsarView`
- **File:** `iosApp/iosApp/Features/DsarView.swift`
- **Audience:** Customer
- **Purpose:** GDPR data-subject access. Request a data export or account
  erasure with optional notes; lists prior requests with status.
- **Entry points:** NavigationLink from `CustomerHomeView` "Data rights".

### `TicketsListView` (defined in `TicketsView.swift`)
- **File:** `iosApp/iosApp/Features/TicketsView.swift`
- **Audience:** Customer (also Admin via `asAdmin: true`)
- **Purpose:** Support inbox. Lists tickets, opens one, posts replies.
  Includes a "Chat us on WhatsApp" button. Admin mode shows the full
  system queue.
- **Entry points:** "Support tickets" links in each role-home view; admin
  variant from `AdminHomeView`.
- **Notable sub-views:** `CreateTicketSheet`, `TicketDetailView`.

---

## 4. Operator screens

### `OperatorTodayView`
- **File:** `iosApp/iosApp/Features/OperatorTodayView.swift`
- **Audience:** Operator
- **Purpose:** Spec §3.3 `/ops/today`. Live stats grid plus sections for
  parcels expected today, late, ready to consolidate, in transit, and held;
  surfaces the BFM queue inline.
- **Entry points:** Operator tab #1 ("Today").
- **Notable sub-views:** Inline link to `OpsBuyForMeQueueView`; status-
  coloured parcel rows.

### `OperatorReceiveView`
- **File:** `iosApp/iosApp/Features/OperatorReceiveView.swift`
- **Audience:** Operator
- **Purpose:** Phase C intake. Pick a pre-registered parcel from the
  warehouse intake queue, mint a Thapsus warehouse SKU, AirPrint the label,
  mark received. Backed by `IntakeViewModel`.
- **Entry points:** Operator tab #2 ("Receive").
- **Notable sub-views:** `OperatorScannerSheet`, `ReceiveLabelSheet`.

### `OperatorScannerSheet`
- **File:** `iosApp/iosApp/Features/OperatorScannerSheet.swift`
- **Audience:** Operator
- **Purpose:** Hosts the camera scanner and routes the lookup result —
  `pre_registered` opens Receive, anything else opens a compact detail
  panel, "not found" surfaces a red banner.
- **Entry points:** Presented full-screen from `OperatorReceiveView`.
- **Notable sub-views:** `SkuScannerView` (camera), `ScannedParcelDetailView`.

### `ConsolidationListView`
- **File:** `iosApp/iosApp/Features/ConsolidationListView.swift`
- **Audience:** Operator (also surfaced as Admin tab "Shipping")
- **Purpose:** Spec §3.3 `/ops/consolidations`. Lists every flight unit;
  tap to open the manifest builder. Operators can open new consolidations
  on demand — cut-off is at their discretion.
- **Entry points:** Operator tab #3 ("Consols"); Admin tab #4 ("Shipping").
- **Notable sub-views:** `CreateConsolidationSheet`,
  `ConsolidationDetailView`.

### `ConsolidationDetailView`
- **File:** `iosApp/iosApp/Features/ConsolidationDetailView.swift`
- **Audience:** Operator
- **Purpose:** Spec §4.4 manifest builder. Add ready-to-consolidate parcels
  to the manifest, capture AWB, enter Tudor invoice, optionally assign a
  clearing agent. Live summary card with status, parcel count, weight.
- **Entry points:** NavigationLink from `ConsolidationListView`.
- **Notable sub-views:** `AddParcelsSheet`, `AssignAgentSheet`, lifecycle /
  AWB / Tudor cards.

### `DispatchView`
- **File:** `iosApp/iosApp/Features/DispatchView.swift`
- **Audience:** Operator
- **Purpose:** Spec §3.3 `/ops/dispatch`. Cluster customs-cleared parcels
  by Nairobi zone and create rider runs.
- **Entry points:** Operator tab #4 ("Dispatch").
- **Notable sub-views:** `NewRunSheet` (zone + rider + parcel multi-select),
  `RunParcelsSheet` (drill-down for a run, reassign rider).

### `OpsBuyForMeQueueView`
- **File:** `iosApp/iosApp/Features/OpsBuyForMeQueueView.swift`
- **Audience:** Operator (also Admin)
- **Purpose:** Operator-facing queue for Buy-for-me concierge requests.
  Mirrors `/ops/buy-for-me`: pending_quote / quoted / paid / rejected.
  Operator opens a Send-quote sheet hitting
  `POST /api/buy-for-me/:id/quote`.
- **Entry points:** "Buy-for-me queue" link from `OperatorTodayView`,
  `OperatorHomeView`, and `AdminHomeView`.
- **Notable sub-views:** Quote sheet (estimate + markup + notes).

### `OpsSettingsView`
- **File:** `iosApp/iosApp/Features/OpsSettingsView.swift`
- **Audience:** Admin (linked under Admin home — manages ops config)
- **Purpose:** Edit exchange rates, shipping fees, promotions, prohibited
  dictionary, pricing tiers.
- **Entry points:** "Ops settings" link from `AdminHomeView`.
- **Notable sub-views:** `AddProhibitedSheet`, `AddPricingTierSheet`.

### `OperatorHomeView` (in `RoleHomeViews.swift`)
- **File:** `iosApp/iosApp/Features/RoleHomeViews.swift`
- **Audience:** Operator
- **Purpose:** Operator Account hub. Links to Buy-for-me queue,
  Notifications, Support tickets, Edit profile.
- **Entry points:** Operator tab #5 ("Account").

---

## 5. Clearing-agent screens

### `CustomsListView`
- **File:** `iosApp/iosApp/Features/CustomsListView.swift`
- **Audience:** Clearing agent (also linked from Admin / Operator)
- **Purpose:** Spec §3.4 & §4.5. Clearing-agent landing screen. Lists
  consolidations assigned to the agent; tap to open the customs-entry form
  with consolidation picker + parcel list.
- **Entry points:** Clearing-agent tab #1 ("Customs"); link from
  `AgentHomeView` and `OperatorHomeView`.
- **Notable sub-views:** `NewCustomsEntrySheet` (IDF + entry number +
  CIF / duty / VAT / IDF fees + doc URL).

### `AgentInvoicesView`
- **File:** `iosApp/iosApp/Features/AgentInvoicesView.swift`
- **Audience:** Clearing agent
- **Purpose:** Submit and track clearing-agent invoices for consolidations
  they cleared.
- **Entry points:** Clearing-agent tab #2 ("Invoices").
- **Notable sub-views:** `CreateAgentInvoiceSheet`.

### `AgentHomeView` (in `RoleHomeViews.swift`)
- **File:** `iosApp/iosApp/Features/RoleHomeViews.swift`
- **Audience:** Clearing agent
- **Purpose:** Clearing-agent Account hub. Links to Customs, My invoices,
  Notifications, Support tickets, Edit profile.
- **Entry points:** Clearing-agent tab #3 ("Account").

---

## 6. Rider screens

### `RiderRunView`
- **File:** `iosApp/iosApp/Features/RiderRunView.swift`
- **Audience:** Rider
- **Purpose:** Spec §4.6. Today's runs by zone with the next stop. Hosts
  navigation into per-run drill-down.
- **Entry points:** Rider tab #1 ("Today").
- **Notable sub-views:** NavigationLinks to `RunStopListView` per run id.

### `RunStopListView`
- **File:** `iosApp/iosApp/Features/RunStopListView.swift`
- **Audience:** Rider
- **Purpose:** Drill-down from `RiderRunView`. Lists parcels on a single
  run (Phase 3 groups them by recipient so one stop covers multiple
  parcels). Tap to open POD capture; surfaces POD sync failures inline.
- **Entry points:** NavigationLink from `RiderRunView`.
- **Notable sub-views:** `PodCaptureView`, POD-sync failure banner.

### `PodCaptureView`
- **File:** `iosApp/iosApp/Features/PodCaptureView.swift`
- **Audience:** Rider
- **Purpose:** Capture proof-of-delivery: photo + recipient OTP +
  signature. Photo is taken via `CameraPickerView` and uploaded to the
  private `pods` bucket via signed URL. Supports single parcel or
  multi-parcel bundles.
- **Entry points:** Sheet from `RunStopListView`.
- **Notable sub-views:** `CameraPickerView`, `SignaturePadView`,
  `ContactPickerView`.

### `OutboxView`
- **File:** `iosApp/iosApp/Features/OutboxView.swift`
- **Audience:** Rider
- **Purpose:** Counts queued offline mutations and offers a manual flush
  button. Critical when a rider regains coverage after a dead zone. Shows
  last sync error + last flush result.
- **Entry points:** Rider tab #2 ("Outbox").

### `RiderHomeView` (in `RoleHomeViews.swift`)
- **File:** `iosApp/iosApp/Features/RoleHomeViews.swift`
- **Audience:** Rider
- **Purpose:** Rider Account hub. Links to Notifications, Support tickets,
  Edit profile.
- **Entry points:** Rider tab #3 ("Account").

---

## 7. Admin screens

### `AdminDashboardView`
- **File:** `iosApp/iosApp/Features/AdminDashboardView.swift`
- **Audience:** Admin
- **Purpose:** Top-level admin overview — stats, AML / risk-flag queue,
  recent users, BFM queue card, "Create BFM" card, "Issue invoice" card,
  email config status. Mirrors the webapp `AdminDashboard.jsx`.
- **Entry points:** Admin tab #1 ("Console"); also linked from
  `AdminHomeView`.

### `KPIDashboardView`
- **File:** `iosApp/iosApp/Features/KPIDashboardView.swift`
- **Audience:** Admin
- **Purpose:** Spec §4.12 founder dashboard. Computes from the SQLDelight
  cache (works offline) and updates reactively when `RealtimeSync` writes
  through. KPI tiles (chargeable kg, parcels, delivered, in transit, held,
  on-time %), founder metrics, revenue card, status breakdown, daily
  orders, error stats.
- **Entry points:** Admin tab #2 ("KPI").

### `AdminCustomerConsolidationsView`
- **File:** `iosApp/iosApp/Features/AdminCustomerConsolidationsView.swift`
- **Audience:** Admin
- **Purpose:** Phase 2 two-tier consolidation lifecycle. Lists every
  customer-consolidation (filterable by status) and surfaces the next
  admin action inline: pending → "Set invoice", invoiced → "Mark paid",
  paid → "Attach to shipping", shipped → no action.
- **Entry points:** Admin tab #3 ("Customer").
- **Notable sub-views:** `AmountInputSheet`, `AttachShippingSheet`.

### `AdminOrdersView`
- **File:** `iosApp/iosApp/Features/AdminOrdersView.swift`
- **Audience:** Admin
- **Purpose:** Admin order management. List, bulk update, edit, cancel,
  request payment. Filter by status / market / date range. Create order
  on behalf of a client.
- **Entry points:** Linked from `AdminHomeView` "Orders".
- **Notable sub-views:** Create-order form, edit / payment / reminder /
  cancel action sheets.

### `AdminOrderDetailView`
- **File:** `iosApp/iosApp/Features/AdminOrderDetailView.swift`
- **Audience:** Admin
- **Purpose:** Drill-down for an admin tapping an order. Reads
  `GET /api/orders/:id` (admin bypass) and renders the live cost breakdown
  recomputed from current pricing.
- **Entry points:** NavigationLink from `AdminOrdersView`.

### `AdminPaymentsView`
- **File:** `iosApp/iosApp/Features/AdminPaymentsView.swift`
- **Audience:** Admin
- **Purpose:** M-Pesa payments review queue. Verify customer-submitted
  M-Pesa SMS proofs. Approve (with optional override reason if claimed
  amount < due) or reject with reason. Stripe payments don't appear here
  — they auto-flip via webhook.
- **Entry points:** Linked from `AdminHomeView` "Pending payments".
- **Notable sub-views:** `RejectPaymentSheet`, `OverridePaymentSheet`.

### `AdminRevenueView`
- **File:** `iosApp/iosApp/Features/AdminRevenueView.swift`
- **Audience:** Admin
- **Purpose:** Line-item daily revenue breakdown + per-method summary
  card. Date filter, export button. Mirrors `GET /api/admin/revenue`.
- **Entry points:** Linked from `AdminHomeView` "Revenue".

### `AdminUsersView`
- **File:** `iosApp/iosApp/Features/AdminUsersView.swift`
- **Audience:** Admin
- **Purpose:** Admin → Users equivalent. List + search. Provision a new
  account, send a password-reset link, deactivate, drill into per-user
  detail (orders + emails sent).
- **Entry points:** Linked from `AdminHomeView` "Users".
- **Notable sub-views:** `ProvisionUserSheet`; reset / delete confirmation
  alerts.

### `AdminAuditLogsView`
- **File:** `iosApp/iosApp/Features/AdminAuditLogsView.swift`
- **Audience:** Admin
- **Purpose:** Paginated feed of privileged actions (provision user, reset
  password, edit pricing). Mirrors `GET /api/admin/logs`.
- **Entry points:** Linked from `AdminHomeView` "Audit logs".

### `AdminErrorLogsView`
- **File:** `iosApp/iosApp/Features/AdminErrorLogsView.swift`
- **Audience:** Admin
- **Purpose:** Server error log feed with level filter (all / error /
  warn / info) + search; clear-all confirmation alert.
- **Entry points:** Linked from `AdminHomeView` "Error logs".

### `AdminDsarQueueView`
- **File:** `iosApp/iosApp/Features/AdminDsarQueueView.swift`
- **Audience:** Admin
- **Purpose:** DSAR queue. List every open data-subject-access request,
  mark fulfilled / rejected, trigger the data export.
- **Entry points:** Linked from `AdminHomeView` "DSAR queue".

### `AdminCreateBuyForMeView`
- **File:** `iosApp/iosApp/Features/AdminCreateBuyForMeView.swift`
- **Audience:** Admin
- **Purpose:** Create a Buy-for-me request on behalf of a customer who
  placed it off-platform (WhatsApp, phone, in person). Optionally
  pre-quotes so the customer can pay immediately.
- **Entry points:** "Create BFM" card on `AdminDashboardView`.

### `AdminIssueInvoiceView`
- **File:** `iosApp/iosApp/Features/AdminIssueInvoiceView.swift`
- **Audience:** Admin
- **Purpose:** Issue a one-off "standalone" invoice. Pick customer, set
  KES amount + description; customer receives email and pays via the
  standard `PayInvoiceView` flow.
- **Entry points:** "Issue invoice" card on `AdminDashboardView`.

### `AdminHomeView` (in `RoleHomeViews.swift`)
- **File:** `iosApp/iosApp/Features/RoleHomeViews.swift`
- **Audience:** Admin
- **Purpose:** Admin Account hub. Links to every admin section (Dashboard,
  Users, Orders, Pending payments, Tickets, Revenue, DSAR queue, Audit
  logs, Error logs, BFM queue, Ops settings, Notifications) plus profile.
- **Entry points:** Admin tab #5 ("Account").

---

## 8. Hardware / shared utility components

These aren't standalone destinations but are full-screen surfaces or
modals presented inside other features.

### `SkuScannerView`
- **File:** `iosApp/iosApp/Hardware/SkuScannerView.swift`
- **Audience:** Operator (shared)
- **Purpose:** Camera scanner for `STK-XXXXXX` warehouse SKUs plus generic
  Code 128 / QR / EAN-13. Uses VisionKit's `DataScannerViewController` on
  iOS 16+ and falls back to AVFoundation on older devices.
- **Entry points:** Hosted by `OperatorScannerSheet`.

### `ContactPickerView`
- **File:** `iosApp/iosApp/Hardware/ContactPickerView.swift`
- **Audience:** Rider, operator (shared)
- **Purpose:** Wraps `CNContactPickerViewController` so the rider can grab
  a recipient's phone number without retyping; reused at parcel
  pre-registration.
- **Entry points:** Presented from `PodCaptureView`.

### `CameraPickerView`
- **File:** `iosApp/iosApp/Hardware/CameraPickerView.swift`
- **Audience:** Rider, operator (shared)
- **Purpose:** `UIImagePickerController` camera/library picker that returns
  JPEG-encoded `Data` ready to upload to Supabase Storage. Used for POD
  capture and parcel-condition photos at intake.
- **Entry points:** Presented from `PodCaptureView`.

### `SignaturePadView`
- **File:** `iosApp/iosApp/Features/SignaturePadView.swift`
- **Audience:** Rider (shared)
- **Purpose:** SwiftUI signature capture for proof-of-delivery. Captures
  touch strokes, renders to PNG, hands bytes back via `onSubmit`. Used
  when a signed receipt is needed instead of (or alongside) the OTP.
- **Entry points:** Presented as a sheet from `PodCaptureView`.

---

## 9. Cross-cutting overlays & modifiers

| Component                     | What it does                                                        | Mounted on                                  |
|-------------------------------|---------------------------------------------------------------------|---------------------------------------------|
| `NotificationBannerView`      | 5-second toast over the host screen for new realtime notifications | `CustomerDashboardView`, `CustomerHomeView` |
| `CutoffBannerView`            | Live countdown to next flight cut-off                              | `CustomerDashboardView`, `CustomerHomeView` |
| `NpsAutoPromptModifier`       | Auto-presents `NpsSurveyView` after delivery                        | `CustomerHomeView` (and any view that calls `.npsAutoPrompt()`) |
| `WhatsAppSupportButton`       | Quick-action "Chat us on WhatsApp" CTA                              | `CustomerHomeView`, `TicketsListView`       |

---

## 10. File index

```
iosApp/iosApp/
├── iOSApp.swift                               # @main entry; boots ThapsusSdk
├── RootView.swift                             # auth gate + deep-link router
├── Navigation/
│   ├── AppEnvironment.swift                   # @Observable bridge over AuthRepository
│   └── RootTabView.swift                      # role-aware TabView
├── Features/                                  # all SwiftUI screens (this doc)
│   ├── AdminAuditLogsView.swift
│   ├── AdminCreateBuyForMeView.swift
│   ├── AdminCustomerConsolidationsView.swift
│   ├── AdminDashboardView.swift
│   ├── AdminDsarQueueView.swift
│   ├── AdminErrorLogsView.swift
│   ├── AdminIssueInvoiceView.swift
│   ├── AdminOrderDetailView.swift
│   ├── AdminOrdersView.swift
│   ├── AdminPaymentsView.swift
│   ├── AdminRevenueView.swift
│   ├── AdminUsersView.swift
│   ├── AgentInvoicesView.swift
│   ├── BuyForMeView.swift
│   ├── ClientTerminalView.swift
│   ├── ConsolidationDetailView.swift
│   ├── ConsolidationListView.swift
│   ├── CreditCenterView.swift
│   ├── CustomerActivityHubView.swift
│   ├── CustomerConsolidationView.swift
│   ├── CustomerDashboardView.swift
│   ├── CustomerHomeView.swift
│   ├── CustomsListView.swift
│   ├── CutoffBannerView.swift
│   ├── DispatchView.swift
│   ├── DsarView.swift
│   ├── HowItWorksView.swift
│   ├── KPIDashboardView.swift
│   ├── MpesaSubmitSheet.swift
│   ├── NewOrderView.swift
│   ├── NotificationBannerView.swift
│   ├── NotificationInboxView.swift
│   ├── NpsAutoPromptModifier.swift
│   ├── NpsSurveyView.swift
│   ├── OperatorReceiveView.swift
│   ├── OperatorScannerSheet.swift
│   ├── OperatorTodayView.swift
│   ├── OpsBuyForMeQueueView.swift
│   ├── OpsSettingsView.swift
│   ├── OutboxView.swift
│   ├── ParcelDetailView.swift
│   ├── PasswordResetView.swift
│   ├── PayInvoiceView.swift
│   ├── PodCaptureView.swift
│   ├── ProfileEditView.swift
│   ├── ProhibitedSearchView.swift
│   ├── PublicPaymentView.swift
│   ├── QuoteCalculatorView.swift
│   ├── ReferralView.swift
│   ├── RiderRunView.swift
│   ├── RoleHomeViews.swift                    # OperatorHomeView, AgentHomeView, AdminHomeView, RiderHomeView
│   ├── RunStopListView.swift
│   ├── SignInView.swift
│   ├── SignaturePadView.swift
│   ├── TicketsView.swift                      # TicketsListView, CreateTicketSheet, TicketDetailView
│   ├── TrackingView.swift
│   ├── TransactionsView.swift
│   └── WarehouseAddressView.swift
└── Hardware/
    ├── CameraPickerView.swift
    ├── ContactPickerView.swift
    ├── LabelPrinter.swift                     # AirPrint label rendering (no UI)
    ├── ManifestPrinter.swift                  # AirPrint manifest rendering (no UI)
    └── SkuScannerView.swift
```
