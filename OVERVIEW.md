# Thapsus Cargo — iOS + Android apps (thapsus-v1.1)

The native mobile apps for the Thapsus Cargo parcel-forwarding and concierge-shopping platform. One codebase, two stores: iPhone via the App Store and Android via Google Play.

---

## What it is

These are the smartphone apps customers, warehouse operators, last-mile riders, customs clearing agents and admins install to run the Thapsus Cargo business on the go. The trick under the hood is "Kotlin Multiplatform" — about 80% of the code (business logic, API calls, offline cache, view-models) is written once and shared between iOS and Android. Only the visible UI is platform-native: SwiftUI for iPhone, Jetpack Compose for Android. They both talk to the same backend that lives in the `Swiftcargo-main` repo.

## What it does

### For customers
- Shop-and-ship from UK retailers (the primary "Buy-for-me" concierge flow)
- Pre-register parcels they've already bought (alternate flow)
- Track every parcel in real time, from UK warehouse all the way to Nairobi doorstep
- Pay invoices via card (Stripe) or M-Pesa STK push prompt on the same phone
- Get a live quote for any shipment (weight + dimensions → £ + KES)
- See referral credit balance, share their referral code, view payment + credit history
- Submit support tickets, request a GDPR data export, schedule account deletion (14-day cooldown)
- Push notifications for every status change

### For warehouse operators
- Scan incoming parcels with the camera (barcode → matched to a customer)
- Print SKU labels through AirPrint (iOS) or printing service (Android)
- Build consolidations (group several customers' parcels into one outgoing batch)
- Print the consolidation manifest for the courier
- Quote, accept or reject incoming Buy-for-me requests
- Assign last-mile delivery runs

### For riders
- See today's runs grouped by zone
- Capture proof-of-delivery: photo, recipient signature, OTP, contact name
- Works fully offline — POD captures queue locally and sync automatically when signal returns

### For clearing agents
- Pick up customs entries for a consolidation
- File KRA / IDF / duty / VAT figures, attach documents
- Submit invoices to Thapsus and track payment status

### For admins
- Full revenue + KPI dashboard with offline-capable caching
- Per-user drill-down, per-order drill-down with re-computed cost breakdown
- Approve / reject M-Pesa payments awaiting review
- Manage users, audit logs, error logs, AML risk queue
- Tweak pricing tiers, customs bands, HS codes, FX rates

## Tech stack & dependencies

- **Kotlin 2.1.10** — the language the shared core is written in
- **Kotlin Multiplatform (KMP)** — lets the same Kotlin code run on both iPhone and Android
- **SKIE 0.10.1** — translates the Kotlin code into something Swift can use naturally on iPhone
- **SwiftUI (iOS) + Jetpack Compose (Android)** — the native UI frameworks for each platform
- **Gradle 8** — build system that turns the source code into installable apps
- **Ktor 3** — handles all HTTP calls to the Thapsus backend
- **Supabase Kotlin SDK** — manages live order updates, file uploads, authentication
- **SQLDelight** — small on-device database that caches everything for offline use
- **Coroutines + Flow** — Kotlin's way of handling things-that-take-time (network calls, live updates)
- **Koin** — wires up the app's dependencies on boot
- **Stripe iOS/Android SDKs** — embed the official card payment sheet
- **CameraX + Google ML Kit (Android)** — barcode scanning at warehouse intake
- **Expo Push / APNs / FCM** — push notification delivery
- **Firebase Cloud Messaging** — Android push routing

## How users interact with it

Customers download the app from the iOS App Store or Google Play, sign in with email + password, and land on the Home tab (Buy-for-me hero + active parcels). Each user role sees a different bottom tab bar: Customers see Home / Shop / Activity / Quote / Account; Operators see BFM / Receive / Consols / Dispatch / Account; Riders see Today / Outbox / Account; etc. The app pulls live order updates from Supabase Realtime so a parcel status flip in the warehouse appears on the customer's phone within a second. Mutations (paying, accepting a quote, scanning a barcode) go through the Express API in the `Swiftcargo-main` repo.

## Notes

- **One codebase, two stores.** The shared module under `shared/` holds the business logic. `iosApp/` and `androidApp/` are thin native UI layers.
- **Backend lives elsewhere.** This app talks to the API in the `Swiftcargo-main` repo (production hosted on Railway). Any backend change must keep the mobile DTOs in sync.
- **Build pins are load-bearing.** Kotlin 2.1.10 + SKIE 0.10.1 + Gradle config-cache OFF must move together. Don't bump opportunistically — see `feedback_thapsus_gradle_pins` for the history.
- **iOS push limitation.** Personal Apple Developer accounts can't sign push entitlements; the entitlements file gets emptied after each `expo prebuild`.
- **Node 22 required for the Expo bridge.** Node 26 (Homebrew default) breaks `@expo/config-plugins`.
- **iOS + Android are at parity for customer / operator / rider** roles. Android admin payment-review is the one remaining gap.
- **Companion backend patches** that the apps depend on live in `server-patches/`. Apply them in lockstep with mobile releases.
