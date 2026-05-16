// CustomerDashboardView.swift
// Customer Home tab — liquid-glass redesign.
// Hi-greeting · cut-off banner · pending actions · quick actions · stat tiles.
// Warehouse-address terminal lives on the Pre-register (New order)
// screen now, not here — see claude/ios-warehouse-into-prereg.

import SwiftUI
import ThapsusShared

struct CustomerDashboardView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var dashVM: CustomerDashboardViewModel? = nil
    @State private var dashObs: StateFlowObserver<DashboardState>? = nil
    // Expanded by default — customers benefit from seeing the steps as soon as
    // they land on the home tab; they can still collapse it via the toggle
    // below.
    @State private var showHowItWorks: Bool = true
    @State private var showingNewOrder = false
    @State private var showingBuyForMe = false

    /// Customer-consolidation rows whose `invoice_status` is still
    /// outstanding — surfaced under the address card so a fresh invoice
    /// is impossible to miss. Same Supabase + Realtime path TrackingView
    /// uses, so a status flip from invoiced → paid causes the row to
    /// drop off this section automatically (server's pushToUser fan-out
    /// also fires the in-app notification banner overlay).
    @State private var customerConsolidations: [CustomerConsolidationDto] = []
    @State private var customerConsolidationsTask: Task<Void, Never>?
    @State private var payTarget: PayTarget?
    /// Pending buy-for-me payments — observed from the shared dashboard
    /// VM's `bfmPendingInvoices` StateFlow. Surfaced as a persistent card
    /// section so a customer who's accepted a quote always sees the
    /// unpaid invoice, not just when the rotating greeting lands on it.
    @State private var bfmPendingObs: StateFlowObserver<[PaymentDto]>? = nil
    /// Quoted buy-for-me orders — the pre-accept state. Same surface as
    /// `bfmPendingObs` but for `BuyForMeOrderDto.status == "quoted"`,
    /// which is what a customer sees the moment an operator finalises
    /// a price. Surfaced as a card so the customer doesn't have to dig
    /// into the Shop tab to find the quote.
    @State private var quotedBfmObs: StateFlowObserver<[BuyForMeOrderDto]>? = nil
    /// True when the home carousel taps a greeting whose destination is
    /// `HomeGreetingDestinationNpsSurvey`. Drives the `.sheet` modifier
    /// below — NPS surveys are a sheet, not a stack push.
    @State private var npsSheetPresented: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                topBar
                header
                CutoffBannerView()
                pendingActionsArea
                actionGrid
                statsTiles

                if showHowItWorks {
                    HowItWorksView()
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }

                Button {
                    withAnimation(LG.animation) { showHowItWorks.toggle() }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: showHowItWorks ? "chevron.up" : "questionmark.circle")
                        Text(showHowItWorks ? "Hide guide" : "How it works")
                    }
                }
                .buttonStyle(LGGlassButtonStyle())
                .padding(.top, 4)
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 100)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollContentBackground(.hidden)
        .background(LiquidGlassBackground())
        .toolbar(.hidden, for: .navigationBar)
        .overlay(alignment: .top) { NotificationBannerView() }
        // NPS post-delivery survey auto-prompt deliberately disabled
        // — customer feedback: the pop-up firing right after every
        // delivered parcel was intrusive. The `npsAutoPrompt`
        // modifier and `NpsSurveyView` themselves stay in the
        // codebase so this can be re-enabled with a one-line add
        // if/when we want a less aggressive trigger (once per N
        // deliveries, or only via the email link).
        .sheet(isPresented: $showingNewOrder) {
            NavigationStack { NewOrderView() }.glassSheet(detents: [.fraction(0.85), .large])
        }
        .sheet(isPresented: $showingBuyForMe) {
            NavigationStack { BuyForMeView() }.glassSheet(detents: [.fraction(0.85), .large])
        }
        .sheet(item: $payTarget) { target in
            PayInvoiceView(
                targetKind: target.kind,
                targetId: target.id,
                targetTitle: target.title,
                amountKesGross: target.amountKes
            )
        }
        .sheet(isPresented: $npsSheetPresented) {
            // Carousel-triggered NPS survey (general feedback, no parcel
            // context). The auto-prompt-on-delivery path stays disabled —
            // see comment on the parent `.overlay` above.
            NavigationStack {
                NpsSurveyView(parcelId: nil) {
                    npsSheetPresented = false
                }
            }
            .glassSheet(detents: [.fraction(0.85), .large])
        }
        .refreshable { dashVM?.refresh() }
        .task {
            bootstrap()
            // Customer-consolidation invoices: same Supabase + Realtime
            // pattern TrackingView uses — kept inline (rather than inside
            // the synchronous `bootstrap()`) so Swift 6 strict concurrency
            // sees both the initial fetch and the observe loop running in
            // the .task closure's main-actor isolation, avoiding the
            // non-Sendable Kotlin repo data-race flag.
            if customerConsolidationsTask == nil, let userID = env.currentUserID {
                let repo = ThapsusSdk.shared.customerConsolidations()
                customerConsolidations = (try? await repo.fetchForUser(userId: userID)) ?? []
                customerConsolidationsTask = Task { @MainActor in
                    for await updated in repo.observeForUser(userId: userID) {
                        if let idx = customerConsolidations.firstIndex(where: { $0.id == updated.id }) {
                            customerConsolidations[idx] = updated
                        } else {
                            customerConsolidations.insert(updated, at: 0)
                        }
                    }
                }
            }
        }
        .onDisappear {
            dashVM?.clear(); dashVM = nil; dashObs = nil
            bfmPendingObs = nil
            quotedBfmObs = nil
            customerConsolidationsTask?.cancel()
            customerConsolidationsTask = nil
        }
    }

    // MARK: - Top bar (logo + bell)

    private var topBar: some View {
        HStack(spacing: 0) {
            BrandWordmark(size: .medium)
            Spacer(minLength: 8)
            NavigationLink {
                NotificationInboxView()
            } label: {
                Image(systemName: "bell")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(LG.fg)
                    .frame(width: 40, height: 40)
                    .background(
                        ZStack {
                            Circle().fill(.ultraThinMaterial)
                            Circle().fill(LG.glassBgStrong)
                        }
                    )
                    .overlay(Circle().strokeBorder(LG.glassBorder, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Notifications")
        }
        .padding(.top, 8)
        .padding(.bottom, 4)
    }

    // MARK: - Header (rotating greeting carousel)

    private var header: some View {
        // Pull firstName from the env session — that's the reliably-
        // populated source (env.session reflects the Authenticated
        // profile by the time Home is on screen). The dashboard VM's
        // own auth.state snapshot occasionally lacks `profile` at first
        // emission on iOS, so reading the env directly keeps the
        // "Good morning, Brian." prefix from regressing to "Hi.".
        let auth = env.session as? AuthSessionAuthenticated
        let first = (auth?.profile?.fullName?
            .split(separator: " ")
            .first
            .map(String.init)) ?? ""
        return HomeGreetingCarousel(
            vm: dashVM,
            firstName: first,
            onNpsTap: { npsSheetPresented = true }
        )
    }

    // MARK: - Action grid (Buy-for-me hero + pre-register secondary)
    //
    // Mirrors the web Dashboard hierarchy after the BFM-primary pivot:
    // Buy-for-me leads with a full-width prominent hero card; pre-
    // register sits below as a co-equal secondary path. Visual treatment
    // (accent gradient + larger surface) makes the primary unambiguous.

    private var actionGrid: some View {
        VStack(spacing: 12) {
            bfmHeroCard
            preRegisterCard
        }
    }

    private var bfmHeroCard: some View {
        Button { showingBuyForMe = true } label: {
            HStack(alignment: .center, spacing: 14) {
                ZStack {
                    Circle().fill(Color.white.opacity(0.18))
                    Image(systemName: "wand.and.stars")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 48, height: 48)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Start a Buy-for-me request")
                        .font(.body(20, weight: .bold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.leading)
                    Text("Send us a link from any UK retailer — we buy on your behalf, ship to Kenya, deliver to your door.")
                        .font(.body(13, weight: .medium))
                        .foregroundStyle(Color.white.opacity(0.88))
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)

                Image(systemName: "arrow.right")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                    .fill(LG.accentGradient)
            )
            .overlay(
                RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.25), lineWidth: 1)
            )
            .shadow(color: LG.accent2.opacity(0.30), radius: 22, x: 0, y: 12)
        }
        .buttonStyle(.plain)
    }

    /// Mirrors `bfmHeroCard`'s horizontal layout (icon-left, title +
    /// subtitle right) so the two cards align visually. Dark ink fill
    /// matches the BFM hero's weight so the secondary card feels
    /// equally tappable — the orange-vs-ink fill (rather than glass-
    /// vs-accent) still carries the primary/secondary hierarchy.
    private var preRegisterCard: some View {
        Button { showingNewOrder = true } label: {
            HStack(alignment: .center, spacing: 14) {
                ZStack {
                    Circle().fill(Color.white.opacity(0.10))
                    Image(systemName: "plus.rectangle.on.rectangle")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(Brand.orange)
                }
                .frame(width: 48, height: 48)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Pre-register a parcel")
                        .font(.body(17, weight: .bold))
                        .foregroundStyle(Brand.cream)
                        .multilineTextAlignment(.leading)
                    Text("Already bought somewhere we don't cover? Tell us it's coming — your UK warehouse address is here too.")
                        .font(.body(13, weight: .medium))
                        .foregroundStyle(Brand.cream.opacity(0.7))
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)

                Image(systemName: "arrow.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Brand.cream.opacity(0.75))
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                    .fill(Brand.ink)
            )
            .overlay(
                RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.18), radius: 14, x: 0, y: 8)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Stats tiles

    private var statsTiles: some View {
        let s = dashObs?.value
        return VStack(alignment: .leading, spacing: 10) {
            LGEyebrow(text: "This month")
                .padding(.leading, 4)
                .padding(.top, 6)
            HStack(spacing: 10) {
                LGStatTile(label: "Parcels", value: "\(s?.totalParcels ?? 0)")
                LGStatTile(label: "In transit", value: "\(s?.inFlightParcels ?? 0)", accent: true)
                LGStatTile(label: "Out for delivery", value: "\(s?.outForDelivery ?? 0)")
            }
        }
    }

    // MARK: - Helpers

    private func bootstrap() {
        if dashVM == nil, let userID = env.currentUserID {
            let vm = ThapsusSdk.shared.customerDashboardViewModel(userId: userID)
            dashVM = vm
            dashObs = StateFlowObserver(initial: vm.state.value) { vm.state }
            bfmPendingObs = StateFlowObserver(initial: vm.bfmPendingInvoices.value) {
                vm.bfmPendingInvoices
            }
            quotedBfmObs = StateFlowObserver(initial: vm.quotedBfmOrders.value) {
                vm.quotedBfmOrders
            }
        }
    }

    // MARK: - Active invoices section

    /// Only `invoiced` rows surface here — `paid`/`shipped` belong on the
    /// Activity → Invoices archive (CustomerInvoicesView) and `pending`
    /// rows have no amount yet.
    private var activeInvoices: [CustomerConsolidationDto] {
        customerConsolidations
            .filter { $0.status == "invoiced" }
            .sorted { ($0.createdAt ?? "") > ($1.createdAt ?? "") }
    }

    @ViewBuilder
    private var activeInvoicesSection: some View {
        if !activeInvoices.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    LGEyebrow(text: activeInvoices.count == 1 ? "Invoice due" : "\(activeInvoices.count) invoices due")
                    Spacer()
                    LGPill(text: "Action", tone: .accent)
                }
                .padding(.leading, 4)

                ForEach(activeInvoices, id: \.id) { c in
                    CustomerInvoiceCard(consolidation: c) {
                        payTarget = PayTarget.fromConsolidation(c)
                    }
                }
            }
            .padding(.top, 4)
        }
    }

    // MARK: - Buy-for-me invoices (quoted + pending-payment)

    /// Pending payment rows from `bfmPendingInvoices` — post-accept,
    /// mid-payment state.
    private var bfmPendingInvoices: [PaymentDto] {
        bfmPendingObs?.value ?? []
    }

    /// Quoted BFM orders — pre-accept state, the customer was billed but
    /// hasn't approved yet.
    private var quotedBfmOrders: [BuyForMeOrderDto] {
        quotedBfmObs?.value ?? []
    }

    // MARK: - Pending actions area (collapses to a summary when > 1)

    /// Total unpaid items across all three invoice surfaces.
    private var pendingActionsTotal: Int {
        quotedBfmOrders.count + bfmPendingInvoices.count + activeInvoices.count
    }

    /// Renders either the existing inline BFM + consolidation sections
    /// (when there's at most one item across both kinds) or a single
    /// summary card with a Resolve CTA → `PendingActionsView` (when
    /// the customer has multiple things to settle).
    @ViewBuilder
    private var pendingActionsArea: some View {
        if pendingActionsTotal > 1 {
            pendingActionsSummaryCard
        } else {
            bfmPendingInvoicesSection
            activeInvoicesSection
        }
    }

    private var pendingActionsSummaryCard: some View {
        NavigationLink {
            PendingActionsView(
                consolidations: activeInvoices,
                bfmQuoted: quotedBfmOrders,
                bfmPending: bfmPendingInvoices,
                onPickConsolidation: { c in payTarget = PayTarget.fromConsolidation(c) },
                onPickBfmQuote: { o in payTarget = PayTarget.fromBfm(o) },
                onPickBfmPending: { p in payTarget = PayTarget.fromBfmPayment(p) }
            )
        } label: {
            InkCard {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Label("Pending actions", systemImage: "tray.full.fill")
                            .font(.caption.weight(.heavy)).tracking(2)
                            .textCase(.uppercase)
                            .foregroundStyle(Brand.cream.opacity(0.85))
                        Spacer()
                        LGPill(text: "Action", tone: .accent)
                    }
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text("\(pendingActionsTotal)")
                            .font(.system(size: 36, weight: .heavy))
                            .foregroundStyle(Brand.orange)
                        Text("invoice\(pendingActionsTotal == 1 ? "" : "s")")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(Brand.cream)
                    }
                    Text("Buy-for-me requests and shipping invoices waiting on your action.")
                        .font(.subheadline)
                        .foregroundStyle(Brand.cream.opacity(0.7))
                    HStack(spacing: 6) {
                        Text("Resolve pending actions")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Brand.orange)
                        Image(systemName: "arrow.right")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Brand.orange)
                    }
                }
            }
        }
        .buttonStyle(.plain)
        .padding(.top, 4)
    }

    /// Persistent invoice-due section that covers both BFM states. Sits
    /// above the consolidation invoices section so concierge orders read
    /// with prominence. Styled identically to `CustomerInvoiceCard`.
    @ViewBuilder
    private var bfmPendingInvoicesSection: some View {
        let total = quotedBfmOrders.count + bfmPendingInvoices.count
        if total > 0 {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    LGEyebrow(text: total == 1
                        ? "Buy-for-me invoice due"
                        : "\(total) buy-for-me invoices due")
                    Spacer()
                    LGPill(text: "Action", tone: .accent)
                }
                .padding(.leading, 4)

                ForEach(quotedBfmOrders, id: \.id) { order in
                    BfmQuotedInvoiceCard(order: order) {
                        payTarget = PayTarget.fromBfm(order)
                    }
                }
                ForEach(bfmPendingInvoices, id: \.id) { p in
                    BfmPendingPaymentCard(payment: p) {
                        payTarget = PayTarget.fromBfmPayment(p)
                    }
                }
            }
            .padding(.top, 4)
        }
    }

}
