# Clearing-agent function debug — plan

Audit performed 2026-04-29. Surfaces reviewed: iOS `AgentInvoicesView`, Android `AgentScaffold` + `AgentInvoicesScreen`, shared `CustomsAgentViewModel` / `AgentInvoicesViewModel` / `AgentInvoicesRepository` / `AgentInvoiceDto`, webapp `routes/agentInvoices.js` + `routes/customs.js`.

Server-side data path is RLS-independent: every clearing-agent flow goes through the Express server's `req.db` pool (service_role, bypasses RLS). RLS migration 019 will not change clearing-agent behaviour by itself.

## Bugs (in priority order)

### B1 — Android `AgentInvoicesScreen` is duplicated; the version with the submission form is dead code
- `androidApp/.../ui/agent/AgentScaffold.kt:232` defines a `private fun AgentInvoicesScreen()` (read-only list).
- `androidApp/.../ui/agent/AgentInvoicesScreen.kt:40` defines a public `fun AgentInvoicesScreen(agentId: String)` that contains the actual submission form.
- The NavHost at `AgentScaffold.kt:110` calls `AgentInvoicesScreen()` (no args) — Kotlin resolves to the private inline copy, so the standalone file is never reached.
- **Effect:** Android clearing-agents cannot submit invoices from the Invoices tab.
- **Fix:** delete the private inline `AgentInvoicesScreen` in `AgentScaffold.kt` (lines 231–285) and route the NavHost to the public top-level one (passing `session.userId` since the public signature takes `agentId: String`).

### B2 — `AgentInvoicesRepository.updateStatus` uses PUT but the server route is PATCH
- `shared/.../data/repository/AgentInvoicesRepository.kt:47` — `api.put<GenericAck, PatchAgentInvoiceRequest>(...)`.
- `Swiftcargo-main/routes/agentInvoices.js:77` — `router.patch('/:id', ...)`.
- **Effect:** admin approve/reject/mark-paid via this client returns 404 (or 405 depending on Express config) — the request never reaches the handler.
- **Fix:** change `api.put` to `api.patch` in `AgentInvoicesRepository.updateStatus`. (Confirm `ThapsusApiClient` exposes `patch`; if not, add it.)

### B3 — `CustomsAgentViewModel.assignedConsolidations` returns all consolidations, not "assigned" ones
- `shared/.../presentation/CustomsAgentViewModel.kt:27` — `consolidations.observeAll()`.
- The `agentId` constructor param is only used in `submitEntry` (line 79); never used for filtering.
- **Effect:** every clearing-agent sees every consolidation. Probably acceptable for a single-agent shop, but the field name is misleading and won't scale.
- **Fix options:**
  - (a) Accept current behaviour — rename field to `allConsolidations` and drop `agentId` from VM constructor (it's only needed at submit-time, can be passed in at the call-site).
  - (b) If we want true assignment, add a server endpoint `GET /api/agent/consolidations` filtering by assigned agent, then a `ConsolidationRepository.refreshForAgent(agentId)` path and an `observeForAgent(agentId)` flow. Requires a webapp PR + DB column on consolidations.

### B4 — No UI to file a customs entry (IDF/duty/VAT) anywhere
- `CustomsAgentViewModel.submitEntry(...)` exists at `CustomsAgentViewModel.kt:54` and is wired to `customs.submitEntry(...)`.
- Android `CustomsScreen` (`AgentScaffold.kt:117–162`) shows entries but has no form to create one.
- iOS has no customs surface at all — only `AgentInvoicesView` in `iosApp/iosApp/Features/`.
- **Effect:** the core "post IDF + KRA entry" flow described in the spec docstring (`CustomsAgentViewModel.kt:18-19`) is not reachable from any UI.
- **Fix:** add a "File customs entry" sheet/form on Android `CustomsScreen`, plus an iOS `CustomsAgentView` mirror. Call `vm.submitEntry(parcelId, idfNo, entryNo, cifKesCents, dutyKesCents, vatKesCents, idfKesCents, rdlKesCents)`. KES values entered in major units in the UI; convert to cents (`Long`) when calling the VM.

### B5 — Money unit drift across DTOs
- `AgentInvoiceDto.amountKes: Double` (major units) — `AgentInvoiceDto.kt:12`.
- `CustomsEntryDto.cifKesCents/dutyKesCents/vatKesCents/idfKesCents/rdlKesCents: Long` (cents).
- **Effect:** rendering code mixes both styles (e.g. Android `AgentInvoicesScreen.kt:138` formats `amountKes` directly; `AgentScaffold.kt:209-213` divides cents by 100). One off-by-100 typo here = invoice claims 100× the duty.
- **Fix:** pick one. Recommend cents-as-`Long` everywhere financial; helper `Long.kesString()` in shared. Migrate `agent_invoices.amount_kes` to `amount_kes_cents BIGINT` in a new migration; update server route + DTO + UI together.

### B6 — iOS status badge falls through to "submitted" colouring on unknown server values
- `AgentInvoicesView.swift:114-127`.
- Server validates the four-value enum on PATCH (`agentInvoices.js:81`), but nothing prevents future statuses or DB-direct edits from breaking the UI silently.
- **Fix:** centralise the status enum in `AgentInvoiceDto` as a sealed type or a top-level `enum class AgentInvoiceStatus { Submitted, Approved, Paid, Rejected, Unknown }`; render `Unknown` distinctly (e.g. grey "?").

### B7 — `submitEntry` always sets `status = ENTRY_FILED` and never advances
- `CustomsAgentViewModel.kt:78` — hardcoded.
- No path in the VM/Repository/UI to flip a customs entry to `DUTY_PAID` / `RELEASED` / `REJECTED`.
- **Fix:** add `customs.updateStatus(entryId, status)` repository method + VM method + UI affordances on the entry row. Server side already has the matching route in `routes/customs.js` (verify the verb + payload there).

## Ordering recommendation

1. **B1 + B2** first — these are showstoppers blocking the agent and admin flows. Both are tiny diffs.
2. **B6** next — defensive UI hardening, no server change.
3. **B5** next — a coordinated webapp + shared + UI migration; do as a single atomic PR pair.
4. **B4 + B7** as a unit — building the customs entry workflow end-to-end so clearing agents can actually do their job in-app. Probably the largest piece.
5. **B3** last — only worth doing once there are multiple clearing agents in production.

## Files that will need touching (summary)

| Bug | iOS | Android | shared/KMP | webapp |
|-----|-----|---------|------------|--------|
| B1 | — | `AgentScaffold.kt`, `AgentInvoicesScreen.kt` | — | — |
| B2 | — | — | `AgentInvoicesRepository.kt`, possibly `ThapsusApiClient` | — |
| B3 | — | `AgentScaffold.kt` (rename) | `CustomsAgentViewModel.kt` | optional new endpoint |
| B4 | new `CustomsAgentView.swift` | `AgentScaffold.kt` (CustomsScreen form) | possibly extend `CustomsRepository` | verify `routes/customs.js` POST |
| B5 | `AgentInvoicesView.swift` | both Android Agent screens | `AgentInvoiceDto.kt`, repo, VM | `routes/agentInvoices.js`, new migration `020_agent_invoices_cents.sql` |
| B6 | `AgentInvoicesView.swift` | `AgentInvoicesScreen.kt` | `AgentInvoiceDto.kt` | — |
| B7 | new entry-row affordance | `AgentScaffold.kt` (CustomsEntryRow) | `CustomsRepository.kt`, `CustomsAgentViewModel.kt` | confirm route exists |
