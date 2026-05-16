# App Store submission readiness plan — 2026-05-16

> **Status:** Pre-submission. Thapsus Cargo iOS has **never been
> submitted to the App Store**; the Apple Developer Program enrolment
> (paid account) is still pending. This plan captures everything we need
> to land **before** the first App Store Connect upload, organised so
> nothing blocks us the day the developer account activates.

The work below was derived from the in-repo audit run on 2026-05-16
using the `app-store-submission-auditor` skill against the Native Swift
target at `iosApp/`. Findings: **1 P0 · 5 P1 · 2 P2**.

## Gating: paid Apple Developer account

These items cannot be touched until the paid Apple Developer Program
enrolment completes. They are **not** blocked on code:

- Real `TEAM_ID` (Apple Team prefix) for the build
- App record in App Store Connect (bundle id `uk.thapsus.cargo`)
- Encryption Documentation upload / ERN year-letter (annual
  self-classification with U.S. BIS, then file in ASC)
- App Privacy nutrition label form
- Age-rating questionnaire (Jan 2026 tier set)
- Apple-app-site-association update on `thapsus.uk/.well-known/` to
  carry the real `<TeamID>.uk.thapsus.cargo` appID prefix
- Reviewer test account creation (customer + admin) in production
  Supabase
- Screenshots, description, support / privacy / terms URLs filed in
  the App Store listing

Track these on the ASC side once the account is live. The rest of this
plan is what we own in this repository.

---

## P0 — must fix before first archive upload

### 1. Add `PrivacyInfo.xcprivacy` privacy manifest

**Why:** Apple rejects uploads at the App Store Connect upload step (not
later in review) when the manifest is missing or doesn't declare every
required-reason API the binary touches. We hit `UserDefaults.standard`
in two places:

- `iosApp/iosApp/DesignSystem/LiquidGlass.swift:46,51` (theme persistence)
- `iosApp/iosApp/Features/NpsAutoPromptModifier.swift:71,75` (NPS
  idempotency)

**Action:**
1. Create `iosApp/iosApp/PrivacyInfo.xcprivacy` declaring
   `NSPrivacyAccessedAPICategoryUserDefaults` with reason `CA92.1`
   ("Access info from same app, per documentation").
2. Add the file to the `iosApp` target's **Copy Bundle Resources** build
   phase in `iosApp.xcodeproj`.
3. Set `NSPrivacyTracking=false` and leave `NSPrivacyTrackingDomains`
   empty — no tracking SDKs are linked.
4. Leave `NSPrivacyCollectedDataTypes` empty in the binary manifest;
   server-side collection (name, email, phone, location, photos,
   payment info) goes in the App Privacy nutrition label in ASC, not
   the in-binary manifest.
5. Re-run the audit checklist after any KMP shared-module bump in case
   `iosMain` starts touching file timestamps, disk space, or system
   boot time (each is a separate required-reason API entry).

**Verification:** Archive the app, run `xcrun altool --validate-app` (or
"Validate App" in Organizer) against a TestFlight build — historically
the privacy-manifest error surfaces here. Until the paid account is
live, validate by hand-inspecting that the `.xcprivacy` is inside the
built `.app` bundle.

---

## P1 — must resolve before submitting for review

### 2. Remove or justify `NSLocalNetworkUsageDescription`

**Why:** Declared in `iosApp/iosApp/Info.plist:111-112` for "wireless
label printer discovery", but the only printing path is AirPrint via
`UIPrintInteractionController` in
`iosApp/iosApp/Hardware/LabelPrinter.swift:64-69`. AirPrint discovery is
system-mediated — apps do not normally trigger the Local Network
prompt for it. Apple flags over-declared permissions.

**Action:**
1. Build, install on a real device, exercise the operator → print label
   flow.
2. If no Local Network prompt appears (expected for pure AirPrint),
   remove the `NSLocalNetworkUsageDescription` key.
3. If the prompt does appear, leave the key but tighten the copy to
   reflect AirPrint specifically.
4. Document the decision in the commit message so the next audit doesn't
   re-flag it.

### 3. Resolve `TEAM_ID` placeholder

**Why:** `iosApp/Configuration/Config.xcconfig:5` still says
`TEAM_ID = ABCDE12345`. Per project convention this gets overridden in
the gitignored `Config.local.xcconfig`, but the placeholder needs to
not look real once the account is live, and our `.xcconfig.example`
should make the override obvious.

**Action:**
1. Once the paid Apple Developer account is active, populate
   `iosApp/Configuration/Config.local.xcconfig` (gitignored) with the
   real Apple Team ID.
2. Verify the AASA file served at
   `https://thapsus.uk/.well-known/apple-app-site-association` carries
   the real `<TeamID>.uk.thapsus.cargo` appID prefix and validates at
   <https://branch.io/resources/aasa-validator/>.
3. Smoke-test Universal Links on a real device for each path:
   `/pay/<id>`, `/track/<id>`, `/orders/<id>`.

### 4. Confirm encryption export compliance posture

**Why:** `Info.plist:91-94` declares
`ITSAppUsesNonExemptEncryption=YES` with
`ITSEncryptionExportComplianceCode` deliberately blank — the year-letter
is meant to live in App Store Connect, not the binary. Audit comment
T27 captures the rationale.

**Action:**
1. Confirm we actually need `YES` — the use cases listed in the
   `Info.plist` comment (Supabase HTTPS via BoringSSL, JWT signature
   validation, SHA-256 over reset-tokens / OTPs) are arguably all
   covered by the "uses only Apple-provided / standard encryption"
   exemption. If a re-read of the BIS guidance says we qualify, switch
   to `NO` and avoid the annual filing entirely.
2. If we stay on `YES`: file the annual self-classification with the
   U.S. BIS (CCATS / ERN), then upload the year-letter via App Store
   Connect → App Information → Encryption Documentation, **for this
   build's version**.
3. Make the call **before** the first archive upload — easier to keep
   the simpler exemption from day one than to drop it later.

### 5. Land the customer Shop readability pass

**Why:** Shop / Buy-for-me is the customer's default landing surface
after the BFM pivot, so the App Store reviewer's first impression is
this screen. The README roadmap already lists "Fix UI readability in
the customer Shop section" — that work needs to land before submission,
not after.

**Action (separate PR(s), scoped to the Shop tab):**
1. `Features/BuyForMeView.swift` — audit contrast on status chips
   (orange-on-cream loses AA contrast at small sizes).
2. Verify Dynamic Type up to `accessibility5` (`XXXLarge`) on the
   `RequestCard` heading + retailer-strip without truncation.
3. Add a meaningful empty state for first-run reviewer accounts (no
   requests yet) with a one-tap "How it works" link.
4. Re-test light + dark themes side-by-side.
5. Capture before/after screenshots in the PR description so the
   submission record shows the polish landed.

### 6. Provision permanent reviewer accounts

**Why:** Reviewers need a working credential pair, including a way to
exercise both the customer journey (Shop / Activity / Pay) and an
admin-only flow if they go looking. We don't ship credentials in code
(correct), so they live in our notes.

**Action:**
1. In production Supabase, create:
   - `appreview-customer@thapsus.uk` — customer role, pre-verified
     email, seeded with one delivered parcel and one in-flight BFM
     request so Activity isn't empty.
   - `appreview-admin@thapsus.uk` — admin role, optional fallback if
     Apple specifically tests an admin surface.
2. Document the credentials in our 1Password vault under
   "App Store reviewer accounts" (not in the repo).
3. Paste both into App Store Connect → App Review → Notes when the
   listing goes live, alongside the draft Reviewer Notes from the
   2026-05-16 audit (section 5 of the audit output).
4. Confirm the customer account's WhatsApp support button has a
   `https://wa.me/<number>` fallback for reviewers without WhatsApp
   installed.

---

## P2 — clean up before first archive (low effort)

### 7. Remove debug `print(...)` statements

`iosApp/iosApp/Features/TrackingView.swift:252,266` log realtime
subscription failures with `print(...)`. Replace with
`os.Logger(subsystem: "uk.thapsus.cargo", category: "TrackingView")` at
`.error` level so failures show up in Console.app / TestFlight crash
context without polluting stdout.

### 8. Bake the right `CFBundleVersion` / `CFBundleShortVersionString`

`Info.plist:19-22` currently ships `1.0.0` / `1`. Fine for the first
submission. Add a release checklist note: bump `CFBundleVersion` (build
number) on every archive upload, even when the marketing version
doesn't change — App Store Connect rejects re-uploads with the same
build number.

---

## Suggested execution order

Each numbered item below is one PR. They can be parallelised, but the
ordering captures dependencies.

1. **PR A — Privacy manifest** (P0 #1). Tiny, mechanical, unblocks
   archive validation.
2. **PR B — Local Network permission decision** (P1 #2). Needs device
   testing; can be done in parallel with PR A.
3. **PR C — Debug print cleanup + os.Logger swap** (P2 #7). Trivial,
   bundle with B if convenient.
4. **PR D — Shop readability pass** (P1 #5). Larger; may split into
   sub-PRs (contrast / Dynamic Type / empty state).
5. **PR E — Encryption posture decision** (P1 #4). Code change is
   trivial (one plist key flip) — the work is the BIS-guidance read.
   Lands once the call is made.
6. **Account-activation steps** — TEAM_ID resolution (P1 #3), reviewer
   account provisioning (P1 #6), App Store Connect setup, AASA team-id
   prefix. Cannot start until the paid Apple Developer Program
   enrolment completes.

## Done definition

The first archive is ready to upload when:

- [ ] `PrivacyInfo.xcprivacy` is in the `.app` bundle and validates
- [ ] No code-only audit issues remain open (P0 cleared, P1 #2/#4/#5/#7
      resolved or deliberately deferred with a written justification)
- [ ] Real `TEAM_ID` baked into `Config.local.xcconfig`; AASA validates
- [ ] Encryption posture chosen (`NO` exemption **or** ERN year-letter
      filed and uploaded to ASC)
- [ ] Reviewer accounts exist in production Supabase and credentials
      are in 1Password
- [ ] App Store Connect listing complete: App Privacy label, age
      rating, screenshots showing the Shop-first build, EULA / Privacy
      / Terms URLs live
- [ ] One full device pass on iPhone (release build) + one on iPad
      portrait (we allow all 4 orientations on iPad — confirm no
      layout breakage)
- [ ] IPv6-only network smoke test (Apple's review network is
      IPv6-only) — verify Stripe payment sheet + Supabase realtime +
      Railway API all respond
- [ ] Reviewer Notes pasted into ASC → App Review (the draft generated
      in the 2026-05-16 audit output, with the test-account block
      filled in)
