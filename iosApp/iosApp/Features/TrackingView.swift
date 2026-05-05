// TrackingView.swift
// Live tracking screen — input a tracking number and see the matching parcel,
// plus a list of all in-flight (active) shipments. Wired to the same offline
// PackageRepository the dashboard uses, so updates land live as RealtimeSync
// writes through.

import SwiftUI
import ThapsusShared

struct TrackingView: View {
    @Environment(AppEnvironment.self) private var env

    var prefilledTrackingNumber: String? = nil

    @State private var query: String = ""
    @State private var parcels: [PackageDto] = []
    @State private var observerTask: Task<Void, Never>?
    /// Hot Supabase realtime channel for the customer's `packages` rows.
    /// Each emission upserts the row into the SQLDelight cache, so the
    /// `observerTask` above lights up automatically — no separate UI
    /// state to thread through. Cancelled in `.onDisappear` so leaving
    /// the tab tears down the websocket.
    @State private var realtimeTask: Task<Void, Never>?

    @State private var publicVm: PublicTrackingViewModel?
    @State private var publicObserver: StateFlowObserver<PublicTrackingViewModelState>?
    @State private var showingNewOrder: Bool = false
    @State private var showingBuyForMe: Bool = false
    /// Per-user customer-consolidations with an active invoice (Phase 2).
    /// Loaded on appear via Supabase PostgREST under the customer's RLS
    /// policy, then refreshed live by the realtime channel below.
    @State private var customerConsolidations: [CustomerConsolidationDto] = []
    @State private var customerConsolidationsTask: Task<Void, Never>?
    /// Customer's open Buy-for-me requests — pending_quote / quoted live
    /// here so they don't get lost on the BuyForMe tab. Driven by the same
    /// `BuyForMeViewModel` the dedicated screen uses; we re-issue load()
    /// after Accept/Reject so the row updates without a manual refresh.
    @State private var bfmVm: BuyForMeViewModel?
    @State private var bfmStateObs: StateFlowObserver<BuyForMeViewModelUiState>?
    @State private var bfmActionObs: StateFlowObserver<BuyForMeViewModelActionState>?
    @State private var rejectingBfm: BuyForMeOrderDto?
    /// Pay-invoice sheet target (BFM or customer-consolidation). Set when
    /// the customer taps Accept & buy / Pay invoice → launches PayInvoiceView.
    @State private var payTarget: PayTarget?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EditorialHeader(
                    eyebrow: "Your orders",
                    title: "Orders &\ntracking",
                    subtitle: "Create a new order or track ones already in flight."
                )

                HStack(spacing: 10) {
                    Button(action: { showingNewOrder = true }) {
                        Label("New order", systemImage: "plus.circle.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                    Button(action: { showingBuyForMe = true }) {
                        Label("Buy for me", systemImage: "wand.and.stars")
                            .frame(maxWidth: .infinity)
                    }
                    // Glass fills render very light against the editorial
                    // backdrop, so a white foreground vanished. Orange text
                    // mirrors the brand without re-using the same orange-fill
                    // style as the New order primary CTA.
                    .buttonStyle(GlassSheenButtonStyle(fill: Brand.cream, foreground: Brand.orange))
                }

                if !invoiceConsolidations.isEmpty {
                    SectionHeader(
                        title: "Invoices",
                        subtitle: "Pay one invoice per consolidation to ship your batch."
                    )
                    ForEach(invoiceConsolidations, id: \.id) { cc in
                        CustomerInvoiceCard(consolidation: cc) {
                            payTarget = PayTarget.fromConsolidation(cc)
                        }
                    }
                }

                trackInputCard

                if let match = matched {
                    SectionHeader(title: "Match")
                    NavigationLink {
                        ParcelDetailView(parcelID: match.id)
                    } label: {
                        ActiveShipmentRow(pkg: match)
                    }
                    .buttonStyle(.plain)
                } else if let publicMatch = publicMatch {
                    SectionHeader(title: "Tracking result")
                    PublicTrackingCard(tracking: publicMatch)
                } else if let err = publicError {
                    CalloutBanner(
                        tint: Color.orange.opacity(0.12),
                        icon: "questionmark.circle.fill",
                        title: "Couldn't find that one",
                        message: err
                    )
                } else if isPublicLoading {
                    SoftCard {
                        HStack { ProgressView().tint(Brand.ink); Text("Searching…").foregroundStyle(Brand.ink) }
                    }
                }

                if !openBfmOrders.isEmpty {
                    SectionHeader(
                        title: "Buy-for-me quotes",
                        subtitle: "Active requests — accept to fund, reject if it's a no."
                    )
                    // Surface BFM accept/reject errors here. Without this,
                    // a 402 "Insufficient wallet balance" (or any other
                    // server failure) silently flips the action state to
                    // Error and the user assumes the button is broken.
                    bfmActionBanner
                    ForEach(openBfmOrders, id: \.id) { order in
                        TrackingBfmQuoteCard(
                            order: order,
                            onAccept: {
                                // Wallet path is dead — route through the new
                                // payments flow (Stripe or M-Pesa) via PayInvoiceView.
                                payTarget = PayTarget.fromBfm(order)
                            },
                            onReject: { rejectingBfm = order }
                        )
                    }
                }

                SectionHeader(
                    title: active.isEmpty ? "No active shipments" : "Active shipments",
                    subtitle: active.isEmpty ? nil : "Tap a parcel for the full timeline."
                )

                if active.isEmpty {
                    SoftCard {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Nothing in flight").font(.headline).foregroundStyle(Brand.ink)
                            Text("Once a parcel is received at Stockport it'll appear here.")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                } else {
                    ForEach(active, id: \.id) { p in
                        NavigationLink {
                            ParcelDetailView(parcelID: p.id)
                        } label: {
                            ActiveShipmentRow(pkg: p)
                        }
                        .buttonStyle(.plain)
                    }
                }

                // Recent deliveries — top 3 of the past-deliveries
                // groups, surfaced inline so a customer who delivered
                // something yesterday doesn't have to scroll. A "group"
                // is parcels delivered together by the same rider POD
                // capture, so a 3-parcel bundle shows as ONE row not
                // three (audit followup: "Group orders from the same
                // delivery together").
                if !recentDeliveryGroups.isEmpty {
                    SectionHeader(
                        title: "Recent deliveries",
                        subtitle: "Tap a delivery to see the proof of delivery."
                    )
                    ForEach(recentDeliveryGroups, id: \.id) { g in
                        deliveryGroupRow(g)
                    }
                }

                // Full archive of every delivered parcel for this user,
                // most-recent first, capped at the last 30 GROUPS (so
                // you actually see ~30 deliveries, not 30 individual
                // parcels of which 25 were the same drop).
                if !pastDeliveryGroups.isEmpty {
                    SectionHeader(
                        title: "Past deliveries",
                        subtitle: "All deliveries we've completed (or closed). Last 30."
                    )
                    ForEach(pastDeliveryGroups, id: \.id) { g in
                        deliveryGroupRow(g)
                    }
                }

                Color.clear.frame(height: 24)
            }
            .padding(20)
        }
        .navigationTitle("Orders")
        .glassNavigationBar()
        .sheet(isPresented: $showingNewOrder) {
            NavigationStack {
                NewOrderView()
            }
            .glassSheet(detents: [.large, .medium])
        }
        .sheet(isPresented: $showingBuyForMe, onDismiss: {
            // BuyForMeView creates its own VM instance (factory in ThapsusSdk),
            // so a request submitted inside the sheet never reaches this
            // screen's `bfmVm`. Re-issue load() on dismiss so the new
            // pending_quote row surfaces in the Concierge section.
            bfmVm?.load()
        }) {
            NavigationStack {
                BuyForMeView()
            }
            .glassSheet(detents: [.large, .medium])
        }
        .sheet(isPresented: Binding(
            get: { rejectingBfm != nil },
            set: { if !$0 { rejectingBfm = nil } }
        )) {
            if let order = rejectingBfm {
                BfmRejectQuoteSheet(order: order) { reason in
                    bfmVm?.reject(id: order.id, reason: reason)
                    rejectingBfm = nil
                }
            }
        }
        // Pay flow (PR B). Triggered from BFM Accept & buy and from
        // CustomerInvoiceCard's Pay invoice CTA. Sheet hosts the
        // PayInvoiceView that owns Stripe + M-Pesa method selection.
        // onDismiss reloads bfmVm so a successful pay (BFM → status=paid)
        // immediately drops the row from the openBfmOrders filter.
        .sheet(item: $payTarget, onDismiss: { bfmVm?.load() }) { target in
            PayInvoiceView(
                targetKind: target.kind,
                targetId: target.id,
                targetTitle: target.title,
                amountKesGross: target.amountKes
            )
        }
        .scrollContentBackground(.hidden)
        .appBackground()
        .task {
            if let prefilled = prefilledTrackingNumber, query.isEmpty {
                query = prefilled
            }
            if publicVm == nil {
                let model = ThapsusSdk.shared.publicTrackingViewModel()
                publicVm = model
                publicObserver = StateFlowObserver(initial: model.state.value) { model.state }
                if let prefilled = prefilledTrackingNumber, !prefilled.isEmpty {
                    model.search(trackingNumber: prefilled)
                }
            }
            guard observerTask == nil, let userID = env.currentUserID else { return }
            observerTask = Task { @MainActor in
                // SKIE bridges Kotlin Flows as throwing AsyncSequences in
                // Swift. An unhandled throw inside `for await` propagates
                // up to the SwiftUI Task and surfaces as
                // Kotlin_processUnhandledException → app freeze on the
                // Orders tab. Wrap defensively so a transient decoder /
                // realtime hiccup doesn't kill the screen.
                do {
                    let repo = ThapsusSdk.shared.packages()
                    for try await rows in repo.observeForUser(userId: userID) {
                        self.parcels = rows
                    }
                } catch {
                    print("[TrackingView] observeForUser failed: \(error)")
                }
            }
            if realtimeTask == nil {
                realtimeTask = Task {
                    do {
                        let repo = ThapsusSdk.shared.packages()
                        // The realtime Flow upserts into SQLDelight on each
                        // Insert/Update; we don't need the emitted PackageDto
                        // here — `observerTask` already mirrors the cache to
                        // `parcels`. Just keep the channel open for the
                        // task's lifetime.
                        for try await _ in repo.observeRealtimeForUser(userId: userID) { }
                    } catch {
                        print("[TrackingView] observeRealtimeForUser failed: \(error)")
                    }
                }
            }
            _ = try? await ThapsusSdk.shared.packages().refreshForUser(userId: userID)
            // Customer-consolidation invoices: one-shot fetch + a hot
            // realtime channel so an admin issuing a fresh invoice flips
            // the card on this screen without a refresh.
            if bfmVm == nil {
                let model = ThapsusSdk.shared.buyForMeViewModel()
                bfmVm = model
                model.load()
                bfmStateObs  = StateFlowObserver(initial: model.state.value)  { model.state }
                bfmActionObs = StateFlowObserver(initial: model.action.value) { model.action }
            }
            if customerConsolidationsTask == nil {
                let repo = ThapsusSdk.shared.customerConsolidations()
                customerConsolidations = (try? await repo.fetchForUser(userId: userID)) ?? []
                customerConsolidationsTask = Task { @MainActor in
                    do {
                        for try await updated in repo.observeForUser(userId: userID) {
                            if let idx = customerConsolidations.firstIndex(where: { $0.id == updated.id }) {
                                customerConsolidations[idx] = updated
                            } else {
                                customerConsolidations.insert(updated, at: 0)
                            }
                        }
                    } catch {
                        print("[TrackingView] customerConsolidations.observeForUser failed: \(error)")
                    }
                }
            }
        }
        .onDisappear {
            observerTask?.cancel()
            observerTask = nil
            realtimeTask?.cancel()
            realtimeTask = nil
            customerConsolidationsTask?.cancel()
            customerConsolidationsTask = nil
            publicVm?.clear()
            publicVm = nil
            publicObserver = nil
            // Per feedback_swiftui_kmp_lifecycle: don't aggressively nil
            // bfmVm here — onDisappear fires on transient redraws too.
            // The @State release on real teardown is enough.
        }
    }

    /// Only show invoiced or paid consolidations — pending ones don't
    /// have an amount yet, and shipped ones are no longer actionable.
    private var invoiceConsolidations: [CustomerConsolidationDto] {
        customerConsolidations.filter { $0.status == "invoiced" || $0.status == "paid" }
    }

    /// Pending-quote and quoted Buy-for-me orders. We deliberately drop
    /// `paid` and downstream statuses — once the customer accepts, the
    /// concierge order shows up as a normal parcel under Active
    /// shipments, so leaving it in this section would double-count.
    private var openBfmOrders: [BuyForMeOrderDto] {
        guard let loaded = bfmStateObs?.value as? BuyForMeViewModelUiStateLoaded else {
            return []
        }
        return loaded.orders.filter { $0.status == "pending_quote" || $0.status == "quoted" }
    }

    /// Renders the BFM accept/reject result. Done states are transient
    /// (the cart removes itself once status flips to `paid`/`rejected`),
    /// but Error states must persist long enough for the user to read
    /// — most commonly "Insufficient wallet balance".
    @ViewBuilder
    private var bfmActionBanner: some View {
        switch bfmActionObs?.value {
        case let err as BuyForMeViewModelActionStateError:
            ErrorBanner(title: "Couldn't complete", message: err.message)
        case let done as BuyForMeViewModelActionStateDone:
            CalloutBanner(
                icon: "checkmark.circle.fill",
                title: "Done",
                message: done.message
            )
        case is BuyForMeViewModelActionStateInFlight:
            ProgressView().frame(maxWidth: .infinity)
        default:
            EmptyView()
        }
    }

    private var publicMatch: TrackingDto? {
        (publicObserver?.value as? PublicTrackingViewModelStateFound)?.tracking
    }

    private var publicError: String? {
        (publicObserver?.value as? PublicTrackingViewModelStateError)?.message
    }

    private var isPublicLoading: Bool {
        publicObserver?.value is PublicTrackingViewModelStateLoading
    }

    @ViewBuilder
    private var trackInputCard: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Tracking number").font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("e.g. THP-3F8C2A", text: $query)
                        .textFieldStyle(.plain)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.characters)
                }
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Brand.cream.opacity(0.6))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
                )

                Button {
                    dismissKeyboard()
                    let q = query.trimmingCharacters(in: .whitespaces)
                    guard !q.isEmpty else { return }
                    if matched == nil {
                        publicVm?.search(trackingNumber: q)
                    }
                } label: {
                    Text("Track")
                }
                .buttonStyle(InkButtonStyle())
                .disabled(query.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
    }

    /// Primary "Match" section requires an exact tracking-number match (case
    /// insensitive). Substring matching here turned a casual digit-typing
    /// session into a high-confidence "we found your parcel" state for what
    /// was really just a partial overlap with a different shipment (audit T24).
    /// id and barcode equality also count so an operator scanning an STK
    /// barcode lands on the right row.
    private var matched: PackageDto? {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return nil }
        return parcels.first { p in
            p.id.lowercased() == q ||
            p.trackingNumber?.lowercased() == q ||
            p.barcode?.lowercased() == q
        }
    }

    /// The active-shipments list still uses substring filtering on the query
    /// — it's a "narrow my list" affordance, not an authoritative match.
    private var active: [PackageDto] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        let inFlight = parcels.filter { isActive($0.status) }
        guard !q.isEmpty else { return inFlight }
        return inFlight.filter { p in
            p.id.lowercased().contains(q) ||
            (p.trackingNumber?.lowercased().contains(q) ?? false) ||
            (p.barcode?.lowercased().contains(q) ?? false) ||
            (p.description_?.lowercased().contains(q) ?? false)
        }
    }

    private func isActive(_ s: PackageStatus) -> Bool {
        switch s {
        case .delivered, .abandoned: return false
        default: return true
        }
    }

    /// Every delivered (or closed) parcel for the customer, sorted
    /// most-recent first. Used as the input to the grouping below.
    private var pastDeliveries: [PackageDto] {
        parcels
            .filter { !isActive($0.status) }
            .sorted { lhs, rhs in
                let l = lhs.updatedAt ?? lhs.createdAt ?? ""
                let r = rhs.updatedAt ?? rhs.createdAt ?? ""
                return l > r
            }
    }

    /// Past deliveries collapsed by shared POD capture. The rider POD
    /// path bulk-INSERTs every parcel in a user-group with the same
    /// `updated_at` (single transaction), so the second-precision
    /// timestamp is the natural shared key. Cap at 30 GROUPS so you
    /// see ~30 deliveries rather than 30 individual parcels (a single
    /// 5-parcel drop would otherwise eat the whole archive).
    private var pastDeliveryGroups: [DeliveryGroup] {
        groupDeliveries(pastDeliveries).prefix(30).map { $0 }
    }

    /// Top-3 hot list of recent deliveries — surfaced above the full
    /// archive so the customer sees yesterday's drop without scrolling.
    private var recentDeliveryGroups: [DeliveryGroup] {
        Array(pastDeliveryGroups.prefix(3))
    }

    /// Bucket parcels by shared delivery time. Two parcels with the
    /// same `updated_at` (down to the second) and either the same
    /// consolidation_id or both null fall into one group. Order
    /// preserved so the leader of each group is the first parcel as
    /// emitted by the cache (which is most-recent-first).
    private func groupDeliveries(_ rows: [PackageDto]) -> [DeliveryGroup] {
        var seen: [String: Int] = [:]
        var groups: [DeliveryGroup] = []
        for p in rows {
            let stamp = (p.updatedAt ?? p.createdAt ?? "").prefix(19)  // YYYY-MM-DDTHH:MM:SS
            let consol = p.consolidationId ?? "_solo"
            let key = "\(stamp)|\(consol)"
            if let idx = seen[key] {
                groups[idx].members.append(p)
            } else {
                seen[key] = groups.count
                groups.append(DeliveryGroup(key: key, members: [p]))
            }
        }
        return groups
    }

    @ViewBuilder
    private func deliveryGroupRow(_ g: DeliveryGroup) -> some View {
        // Singletons keep their existing card; multi-parcel groups
        // render a "N parcels delivered together" summary that opens
        // the leader parcel's detail (POD covers the whole group).
        if g.members.count == 1, let only = g.members.first {
            NavigationLink {
                ParcelDetailView(parcelID: only.id)
            } label: {
                DeliveredShipmentRow(pkg: only)
            }
            .buttonStyle(.plain)
        } else if let leader = g.members.first {
            NavigationLink {
                ParcelDetailView(parcelID: leader.id)
            } label: {
                GroupedDeliveryRow(group: g)
            }
            .buttonStyle(.plain)
        }
    }

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
        )
    }
}

private struct ActiveShipmentRow: View {
    let pkg: PackageDto

    var body: some View {
        SoftCard {
            HStack(alignment: .top, spacing: 12) {
                VStack(spacing: 4) {
                    Image(systemName: icon(for: pkg.status))
                        .font(.title2)
                        .foregroundStyle(Brand.cream)
                        .frame(width: 44, height: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Brand.ink)
                        )
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(pkg.description_ ?? pkg.retailer ?? "Parcel")
                        .font(.headline)
                        .foregroundStyle(Brand.ink)
                        .lineLimit(1)
                    Text(statusLabel(pkg.status))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    HStack(spacing: 8) {
                        if let t = pkg.trackingNumber {
                            Text(t).font(.caption.monospaced()).foregroundStyle(Brand.orange)
                        }
                        if let kg = pkg.chargeableKg?.doubleValue {
                            Text(String(format: "%.1f kg", kg))
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(.tertiary)
                        }
                    }
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }
        }
    }

    private func icon(for s: PackageStatus) -> String {
        switch s {
        case .preRegistered: return "doc.badge.plus"
        case .receivedAtWarehouse, .photographed, .weighed, .screened: return "shippingbox.fill"
        case .manifested, .inTransit: return "airplane"
        case .jkiaArrived, .awaitingDutyPayment, .released: return "checkmark.shield"
        case .outForDelivery: return "scooter"
        case .delivered: return "checkmark.circle.fill"
        case .held, .heldAtNairobiHub: return "exclamationmark.triangle.fill"
        default: return "shippingbox"
        }
    }

    private func statusLabel(_ s: PackageStatus) -> String {
        switch s {
        case .preRegistered: return "Pre-registered"
        case .receivedAtWarehouse: return "At Stockport hub"
        case .photographed: return "Photographed"
        case .weighed: return "Weighed"
        case .screened: return "Screened"
        case .manifested: return "On manifest"
        case .inTransit: return "In transit ✈︎"
        case .jkiaArrived: return "Arrived JKIA"
        case .awaitingDutyPayment: return "Awaiting duty"
        case .released: return "Customs cleared"
        case .outForDelivery: return "Out for delivery"
        case .delivered: return "Delivered"
        case .held: return "Held — action needed"
        case .heldAtNairobiHub: return "At Nairobi hub"
        case .abandoned: return "Abandoned"
        @unknown default: return "—"
        }
    }
}

/// A bundle of parcels that were delivered together by a single rider
/// POD capture. Identified by shared (updated_at second, consolidation
/// id). Singletons render via DeliveredShipmentRow; multi-parcel groups
/// render via GroupedDeliveryRow.
struct DeliveryGroup: Identifiable {
    let key: String
    var members: [PackageDto]
    var id: String { key }
}

/// Summary card for a multi-parcel POD capture. Shows the parcel count,
/// joined description preview, and the shared delivered-on date. Tap
/// opens the leader parcel's ParcelDetailView, which surfaces the
/// shared POD photo + signature for the whole bundle.
private struct GroupedDeliveryRow: View {
    let group: DeliveryGroup

    var body: some View {
        SoftCard {
            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.green)
                        .frame(width: 44, height: 44)
                    VStack(spacing: 0) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.headline).foregroundStyle(.white)
                        Text("\(group.members.count)×")
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundStyle(.white)
                    }
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(headline)
                        .font(.headline).foregroundStyle(Brand.ink)
                        .lineLimit(2)
                    HStack(spacing: 8) {
                        Text("\(group.members.count) parcels delivered together")
                            .font(.subheadline).foregroundStyle(.secondary)
                        if let when = friendlyDate(group.members.first?.updatedAt
                                                ?? group.members.first?.createdAt) {
                            Text("· \(when)").font(.caption).foregroundStyle(.tertiary)
                        }
                    }
                    HStack(spacing: 6) {
                        ForEach(Array(group.members.prefix(4).enumerated()), id: \.offset) { _, p in
                            if let t = p.trackingNumber {
                                Text(t)
                                    .font(.caption2.monospaced())
                                    .padding(.horizontal, 6).padding(.vertical, 2)
                                    .background(
                                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                                            .fill(Brand.cream.opacity(0.8))
                                    )
                                    .foregroundStyle(Brand.orange)
                            }
                        }
                        if group.members.count > 4 {
                            Text("+\(group.members.count - 4)")
                                .font(.caption2.monospaced())
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                Spacer()
                VStack(spacing: 2) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.caption2).foregroundStyle(.tertiary)
                    Text("POD").font(.system(size: 9, weight: .heavy)).tracking(1.5)
                        .foregroundStyle(.tertiary)
                }
            }
        }
    }

    /// "Play Dough, Airforce 1 Max + 1 more" / drops null/empty.
    private var headline: String {
        let names = group.members
            .compactMap { $0.description_ ?? $0.retailer }
            .filter { !$0.isEmpty }
        if names.isEmpty { return "\(group.members.count) parcels" }
        let head = names.prefix(2).joined(separator: ", ")
        let extra = names.count > 2 ? " + \(names.count - 2) more" : ""
        return head + extra
    }

    private func friendlyDate(_ raw: String?) -> String? {
        guard let raw, raw.count >= 10 else { return raw }
        let isoF = ISO8601DateFormatter()
        isoF.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = isoF.date(from: raw)
            ?? ISO8601DateFormatter().date(from: raw)
            ?? DateFormatter().date(from: String(raw.prefix(10)))
        guard let d = date else { return String(raw.prefix(10)) }
        let f = DateFormatter()
        f.dateFormat = "MMM d, yyyy"
        return f.string(from: d)
    }
}

/// Compact row for a parcel that's already off the customer's
/// in-flight list (delivered or abandoned). Shares ActiveShipmentRow's
/// shape but drops the lifecycle icon for a tinted "checked" badge so
/// the past-deliveries list reads at a glance, plus surfaces the
/// delivered-on date when present.
private struct DeliveredShipmentRow: View {
    let pkg: PackageDto

    var body: some View {
        SoftCard {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: pkg.status == .delivered ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(pkg.status == .delivered ? Color.green : Color.gray)
                    )
                VStack(alignment: .leading, spacing: 4) {
                    Text(pkg.description_ ?? pkg.retailer ?? "Parcel")
                        .font(.headline).foregroundStyle(Brand.ink)
                        .lineLimit(1)
                    HStack(spacing: 8) {
                        Text(pkg.status == .delivered ? "Delivered" : "Abandoned")
                            .font(.subheadline).foregroundStyle(.secondary)
                        if let when = friendlyDate(pkg.updatedAt ?? pkg.createdAt) {
                            Text("· \(when)").font(.caption).foregroundStyle(.tertiary)
                        }
                    }
                    HStack(spacing: 8) {
                        if let t = pkg.trackingNumber {
                            Text(t).font(.caption.monospaced()).foregroundStyle(Brand.orange)
                        }
                        if let kg = pkg.chargeableKg?.doubleValue {
                            Text(String(format: "%.1f kg", kg))
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(.tertiary)
                        }
                    }
                }
                Spacer()
                // The chevron implies "tap for proof of delivery" —
                // the destination ParcelDetailView renders the POD
                // photo + signature on a delivered parcel.
                VStack(spacing: 2) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.caption2).foregroundStyle(.tertiary)
                    Text("POD").font(.system(size: 9, weight: .heavy)).tracking(1.5)
                        .foregroundStyle(.tertiary)
                }
            }
        }
    }

    /// Trim ISO timestamps down to a "Mar 4" shape — past-deliveries
    /// rows want the date at-a-glance, not the whole `2026-03-04T00:00…`
    /// blob the server returns.
    private func friendlyDate(_ raw: String?) -> String? {
        guard let raw, raw.count >= 10 else { return raw }
        let isoF = ISO8601DateFormatter()
        isoF.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = isoF.date(from: raw)
            ?? ISO8601DateFormatter().date(from: raw)
            ?? DateFormatter().date(from: String(raw.prefix(10)))
        guard let d = date else { return String(raw.prefix(10)) }
        let f = DateFormatter()
        f.dateFormat = "MMM d, yyyy"
        return f.string(from: d)
    }
}

private struct PublicTrackingCard: View {
    let tracking: TrackingDto

    private static let steps: [(String, String)] = [
        ("pending", "Pending"),
        ("received_at_warehouse", "Received"),
        ("consolidating", "Consolidating"),
        ("in_transit", "In transit"),
        ("customs", "Customs"),
        ("out_for_delivery", "Out for delivery"),
        ("delivered", "Delivered"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SoftCard {
                VStack(alignment: .leading, spacing: 6) {
                    Text(tracking.trackingNumber)
                        .font(.headline.monospaced())
                        .foregroundStyle(Brand.orange)
                    if let r = tracking.retailer {
                        Text(r).font(.subheadline).foregroundStyle(Brand.ink)
                    }
                    if let d = tracking.description_ {
                        Text(d).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            if let hold = tracking.holdReason, !hold.isEmpty,
               (tracking.holdResolvedAt ?? "").isEmpty {
                heldBanner(hold: hold)
            }
            timeline
            if !tracking.packages.isEmpty {
                SectionHeader(title: "Packages")
                ForEach(tracking.packages, id: \.id) { p in
                    SoftCard {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(p.description_ ?? "Package").font(.subheadline.weight(.semibold))
                            HStack(spacing: 12) {
                                if let kg = p.weightKg?.doubleValue {
                                    Text(String(format: "%.1f kg", kg)).font(.caption.monospacedDigit())
                                }
                                if let s = p.status { Text(s).font(.caption) }
                            }
                            .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    private func heldBanner(hold: String) -> some View {
        let copy: String = {
            switch hold {
            case "held_at_nairobi_hub":
                return "Held at hub. Two delivery attempts failed — please contact support to arrange collection or redelivery."
            default:
                return "On hold: \(hold.replacingOccurrences(of: "_", with: " "))"
            }
        }()
        return SoftCard {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color.orange)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Action needed").font(.subheadline.weight(.semibold))
                    Text(copy).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private var timeline: some View {
        let currentIdx = Self.steps.firstIndex { $0.0 == (tracking.status ?? "") } ?? 0
        return VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(Self.steps.enumerated()), id: \.offset) { idx, step in
                HStack(spacing: 10) {
                    Image(systemName: idx <= currentIdx ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(idx <= currentIdx ? Color.green : Color.secondary.opacity(0.6))
                    Text(step.1)
                        .font(.subheadline)
                        .foregroundStyle(idx == currentIdx ? Brand.ink : .secondary)
                        .fontWeight(idx == currentIdx ? .semibold : .regular)
                }
            }
        }
    }
}

/// Per-user invoice card displayed at the top of the customer Orders tab
/// when an admin has stamped a customer-consolidation. Status badge flips
/// from "Pay now" (invoiced) to "Paid" (paid). Tap-through to a payment
/// flow is intentionally deferred — Phase 2 surfaces the invoice; Phase
/// 2.1 will wire wallet/M-Pesa payment.
private struct CustomerInvoiceCard: View {
    let consolidation: CustomerConsolidationDto
    /// Tap → launch PayInvoiceView. Wired in PR B (server PR #61, migration 028)
    /// — wallet path is dead, payments now go through Stripe or M-Pesa.
    let onPay: () -> Void

    var body: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Label("Shipping invoice", systemImage: "doc.text.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Brand.ink)
                    Spacer()
                    statusBadge
                }
                if let amount = consolidation.invoiceAmount {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(consolidation.invoiceCurrency)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        // KotlinDouble? → Swift Double via .doubleValue.
                        Text(formatAmount(amount.doubleValue))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(Brand.ink)
                    }
                }
                if let boxedCount = consolidation.parcelCount {
                    let count = Int(truncating: boxedCount)
                    if count > 0 {
                        Text("Covers \(count) parcel\(count == 1 ? "" : "s")")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                if consolidation.status == "invoiced" {
                    Text("Pay this invoice to clear your batch for the next outgoing shipment.")
                        .font(.caption).foregroundStyle(.secondary)
                    Button(action: onPay) {
                        Label("Pay invoice", systemImage: "creditcard.fill")
                            .frame(maxWidth: .infinity)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                } else if consolidation.status == "paid" {
                    Text("Payment received — your parcels are queued for shipping.")
                        .font(.caption).foregroundStyle(.green)
                }
            }
        }
    }

    @ViewBuilder
    private var statusBadge: some View {
        let (label, color): (String, Color) = {
            switch consolidation.status {
            case "invoiced": return ("PAY NOW", .orange)
            case "paid":     return ("PAID", .green)
            default:         return (consolidation.status.uppercased(), .gray)
            }
        }()
        Text(label)
            .font(.system(size: 9, weight: .heavy)).tracking(2)
            .foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Capsule().fill(color.opacity(0.16)))
    }

    private func formatAmount(_ value: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal
        return f.string(from: NSNumber(value: value)) ?? "\(Int(value))"
    }
}


// MARK: - Buy-for-me quote card on the Orders page

/// Compact card mirroring CustomerInvoiceCard's visual weight. Surfaces
/// the customer's open Buy-for-me requests so a quoted concierge order
/// doesn't get lost on the secondary BuyForMe screen — accept/reject
/// inline so the rider/operator gets the answer immediately.
private struct TrackingBfmQuoteCard: View {
    let order: BuyForMeOrderDto
    let onAccept: () -> Void
    let onReject: () -> Void

    var body: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Label("Buy-for-me quote", systemImage: "wand.and.stars")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Brand.ink)
                    Spacer()
                    statusBadge
                }

                Text(order.itemName)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Brand.ink)

                retailerLink

                if let g = order.estimateGbp?.doubleValue {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text("£")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text(formatAmount(g))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(Brand.ink)
                        Text("+ \(Int(order.markupPct))% service")
                            .font(.caption).foregroundStyle(.secondary)
                            .padding(.leading, 4)
                    }
                }

                if order.status == "quoted" {
                    // Both buttons use GlassSheenButtonStyle so they're the
                    // same height (the previous `.bordered` Reject was ~22pt
                    // shorter than the orange Accept). Wrapping each in an
                    // explicit `.contentShape(Rectangle())` ensures the
                    // entire pill is tappable — the original Buttons were
                    // ignoring taps on iOS 26 inside a SoftCard's
                    // .regularMaterial background.
                    HStack(spacing: 10) {
                        Button(action: onAccept) {
                            Label("Accept & buy", systemImage: "checkmark.circle.fill")
                                .frame(maxWidth: .infinity)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

                        Button(action: onReject) {
                            Label("Reject", systemImage: "xmark.circle")
                                .frame(maxWidth: .infinity)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(GlassSheenButtonStyle(
                            fill: Color.red.opacity(0.14),
                            foreground: .red
                        ))
                    }
                } else {
                    Text("We're putting together a quote — you'll get an email within 24 hours.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    /// Tappable retailer URL — same pattern as the operator queue.
    /// Opens in Safari via SwiftUI Link; falls back to plain text for
    /// malformed/empty strings so the row layout never breaks.
    @ViewBuilder
    private var retailerLink: some View {
        if let url = URL(string: order.retailerUrl), url.scheme?.hasPrefix("http") == true {
            Link(destination: url) {
                HStack(spacing: 4) {
                    Image(systemName: "link").font(.caption2)
                    Text(order.retailerUrl)
                        .font(.caption.monospaced())
                        .lineLimit(1).truncationMode(.middle)
                }
                .foregroundStyle(Brand.orange)
            }
        } else {
            Text(order.retailerUrl)
                .font(.caption.monospaced()).foregroundStyle(.secondary)
                .lineLimit(1).truncationMode(.middle)
        }
    }

    private func formatAmount(_ value: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        return f.string(from: NSNumber(value: value)) ?? String(format: "%.2f", value)
    }

    @ViewBuilder
    private var statusBadge: some View {
        let (label, color): (String, Color) = {
            switch order.status {
            case "pending_quote": return ("AWAITING QUOTE", .orange)
            case "quoted":        return ("QUOTE READY",    .blue)
            default:              return (order.status.uppercased(), .gray)
            }
        }()
        Text(label)
            .font(.system(size: 9, weight: .heavy)).tracking(2)
            .foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Capsule().fill(color.opacity(0.16)))
    }
}

/// Mirrors the BuyForMeView reject sheet. Inlined here so the Orders
/// page can stay self-contained — the BFM page's copy is private.
private struct BfmRejectQuoteSheet: View {
    @Environment(\.dismiss) private var dismiss
    let order: BuyForMeOrderDto
    let onSubmit: (String) -> Void
    @State private var reason: String = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(order.itemName).font(.headline)
                    if let g = order.estimateGbp?.doubleValue {
                        Text("Quoted: £\(String(format: "%.2f", g)) + \(Int(order.markupPct))% service")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                Section("Why are you rejecting?") {
                    TextEditor(text: $reason).frame(minHeight: 120)
                }
                Section {
                    Text("We'll get back with another option if we can.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Reject quote")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Reject") {
                        onSubmit(reason.trimmingCharacters(in: .whitespacesAndNewlines))
                    }
                    .disabled(reason.trimmingCharacters(in: .whitespacesAndNewlines).count < 3)
                }
            }
        }
    }
}

/// Pay-flow target — feeds PayInvoiceView from BFM Accept & buy and from
/// CustomerInvoiceCard's Pay invoice CTA. Wallet path is dead post
/// migration 028 / server PR #61.
struct PayTarget: Identifiable, Hashable {
    let kind: String
    let id: String
    let title: String
    let amountKes: Int64

    static func fromBfm(_ order: BuyForMeOrderDto) -> PayTarget {
        let estimate = order.estimateGbp?.doubleValue ?? 0
        let markup   = Double(order.markupPct) / 100.0
        let totalGbp = estimate * (1 + markup)
        // Approximate KES at 165 — server's PaymentDto.amount_due_kes is
        // authoritative once the create POST returns. This value only
        // primes the summary card before the server responds.
        let approxKes = Int64((totalGbp * 165).rounded(.up))
        return PayTarget(
            kind: "buy_for_me",
            id: order.id,
            title: "Buy-for-me · \(order.itemName)",
            amountKes: max(approxKes, 0)
        )
    }

    static func fromConsolidation(_ c: CustomerConsolidationDto) -> PayTarget {
        let amount = c.invoiceAmount?.doubleValue ?? 0
        return PayTarget(
            kind: "consolidation",
            id: c.id,
            title: "Shipping invoice",
            amountKes: Int64(amount.rounded())
        )
    }
}
