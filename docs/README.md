# Thapsus Cargo — documentation index

Reference docs for the iOS + Android mobile platform. The repo-level
[`README.md`](../README.md) and [`ARCHITECTURE.md`](../ARCHITECTURE.md)
cover the day-to-day; the files here are deeper dives, audits, and
planning artifacts.

## Cross-platform audits

- [`system_audit_2026_05_09.md`](./system_audit_2026_05_09.md) —
  Cross-platform technical audit (iOS / web / Supabase). 3 High / 6
  Medium / 4 Low findings; most are now remediated (W4–W6 workstreams).
  Companion: see `audit_2026_04_30_progress.md` for the corrections
  appendix.
- [`audit_2026_04_30_progress.md`](./audit_2026_04_30_progress.md) —
  Audit follow-up tracker; rolling status of high/medium/low remediation.
- [`parity_audit.md`](./parity_audit.md) — Canonical
  webapp ↔ mobile feature-parity matrix. The source of truth when
  asking "does the iOS / Android app do X yet?"

## Feature deep-dives

- [`parcel_payment_audit_2026_05_03.md`](./parcel_payment_audit_2026_05_03.md) —
  Deep-dive on the parcel↔payment lifecycle: when a parcel arrives at
  the warehouse, who quotes it, how invoices flow, where the customer
  sees state.
- [`clearing_agent_debug_plan.md`](./clearing_agent_debug_plan.md) —
  Clearing-agent feature debug plan (KE customs handover, agent invoice
  uploads).
- [`localization_audit.md`](./localization_audit.md) — Strings
  inventory and localisation posture across both apps.

## Reference catalogues

- [`ios_screens.md`](./ios_screens.md) — Screen-by-screen catalogue of
  the iOS app, with the SwiftUI view names and their KMP view-model
  counterparts.
- [`_audit_workspace/db_schema_snapshot.md`](./_audit_workspace/db_schema_snapshot.md) —
  Database schema snapshot as of the audit. The live source of truth
  is `Bmwarari2/swiftcargo-main/database/migrations/`.
- [`_audit_workspace/webapp_routes.md`](./_audit_workspace/webapp_routes.md) —
  Enumeration of webapp routes for parity checks.

## Test plans

- [`e2e_manual_test_plan_2026_05_04.md`](./e2e_manual_test_plan_2026_05_04.md) —
  End-to-end manual test plan covering customer + operator + rider +
  admin journeys. Use before release tagging.

## User-facing

- [`guide/Thapsus_Cargo_User_Guide.pdf`](./guide/Thapsus_Cargo_User_Guide.pdf)
  · [`guide/user_guide.html`](./guide/user_guide.html) — Customer-facing
  user guide (Buy-for-me, parcel tracking, payments, account, support).

---

Missing something? The PR history on `main` is the authoritative log of
what shipped when. The audit-driven workstreams (W4–W6, F-6 through
F-12, H-4) have been the backbone of compliance, accessibility, and CI
improvements through 2026-05.
