# Localization audit (S2-2)

Sweep of customer-facing iOS surface, completed 2026-04-30 as part of the
post-Sprint-1 hardening pass. Pairs with `iosApp/iosApp/DesignSystem/Localization.swift`.

## Scope

The audit only covers customer-facing surfaces. Operator, agent, admin and
rider screens stay in English by design — those audiences are internal and a
shared vocabulary is more important than localisation. Customer screens cover:

- `RootView`, `SignInView`, `SignUpView`
- `CustomerDashboardView`, `WarehouseAddressView`
- `NewOrderView`, `OrderListView`, `ParcelDetailView`
- `WalletView`, `PublicPaymentView`
- `TicketsView` (customer side), `BuyForMeView`, `DsarView`
- `ReferralView`, `ProhibitedSearchView`, `TrackingView`
- `NpsSurveyView`, `NotificationInboxView` (customer subset)

## Today's status (2026-04-30)

The lookup infrastructure (`Localization.swift` + `T()` helper +
`LocalizationStore.shared.apply(languagePref:)` hook in `AppEnvironment`)
**is in place and wired to the user profile**. Only one view actually
calls `T()` (one usage as of this audit). The rest still hold raw English
literals.

This PR expands the `Strings.en` / `Strings.sw` dictionaries from ~30 to
~75 keys covering the highest-traffic customer screens, but does NOT
migrate the views themselves. The string literals stay in place until
the translator delivers the Swahili pack — at which point a follow-up
PR runs the migrate-a-view recipe (in `Localization.swift`'s header
comment) on each customer view.

This is the "surface review only — full translation pack is post-translator"
deliverable from the audit memo.

## Key conventions

- `<surface>.<element>` (dot-separated, lower-camelCase after the dot).
- `surface` matches the view name minus `View`: `dashboard.*`, `wallet.*`,
  `auth.*`, `neworder.*`, `tickets.*`, `buyForMe.*`, `dsar.*`, `nps.*`,
  `tracking.*`, `prohibited.*`, `referrals.*`, `warehouse.*`.
- `common.*` for cross-screen primitives (`common.cancel`, `common.save`,
  `common.loading`).
- Auth flows under `auth.*`.

Keys are stable identifiers — once added, only their value changes. Don't
rename keys; deprecate and add a replacement if the meaning shifts.

## Migrate-a-view recipe

1. Open the customer view.
2. For every literal in `Text("...")`, `.navigationTitle("...")`, button
   labels, alert titles/messages, list section headers, etc.:
   - Look up the matching key in `Strings.en`. If there isn't one yet,
     add it under the right surface prefix in **both** `en` and `sw`
     (EN string in the SW slot is fine until the translator fills SW).
   - Replace the literal with `T("the.key")`.
3. Don't migrate operator / agent / admin / rider views — keep them EN.
4. Don't migrate strings the user can't see (e.g. accessibility labels for
   developer-only contexts, error log noise) — focus on rendered text.
5. After migrating, smoke-test the view by toggling the user's language
   pref to `sw` in `ProfileEditView` and confirming the expected EN
   fallback (or actual Swahili once the translator pack lands).

## Per-view sweep status

| View | Status | Keys defined? | Notes |
|---|---|---|---|
| `SignInView` | EN literal | Yes (`auth.*`) | 11 strings — sign-in CTA, error states |
| `SignUpView` | EN literal | Yes (`auth.*`) | Mirror of sign-in + name/phone fields |
| `CustomerDashboardView` | EN literal | **Yes (full)** | 12 strings — eyebrow, welcome, copy/copied, action rows |
| `WarehouseAddressView` | EN literal | Yes (`warehouse.*`) | Already partially keyed |
| `NewOrderView` | EN literal | Yes (`neworder.*`) | 11 strings — fields, placeholder, error title |
| `WalletView` | EN literal | Yes (`wallet.*`) | 10 strings — balance, top-up, paybill |
| `TicketsView` (customer) | EN literal | Yes (`tickets.*`) | 7 strings — list + new-ticket form |
| `BuyForMeView` | EN literal | Yes (`buyForMe.*`) | 7 strings — request-a-quote form |
| `TrackingView` | EN literal | Yes (`tracking.*`) | 2 strings — status display |
| `NpsSurveyView` | EN literal | Yes (`nps.*`) | 5 strings — prompt, score, comment, submit, thanks |
| `DsarView` | EN literal | Yes (`dsar.*`) | 3 strings — export, delete |
| `ReferralView` | EN literal | Yes (`referrals.*`) | 3 strings — title, code, share |
| `ProhibitedSearchView` | EN literal | Yes (`prohibited.*`) | 2 strings — title, search placeholder |
| `RootView` (intro / how-it-works) | EN literal | Yes (`home.*`) | 8 strings — already keyed |

Total: ~75 keys defined, 0 views migrated. The follow-up PR after the
Swahili translator delivers will run the recipe on each view above.

## Out of scope

- `OperatorReceiveView`, `OpsSettingsView`, all admin views, customs/agent
  views, rider views — internal, EN-only.
- M-Pesa paybill copy — already server-driven via `/api/wallet/mpesa-info`,
  so the operator can deliver Swahili there without a code change.
- Brand strings (e.g. "Thapsus Cargo", "STK-XX", trademarks) — never localised.
- Date/number formatting — already handled by Foundation locales.
