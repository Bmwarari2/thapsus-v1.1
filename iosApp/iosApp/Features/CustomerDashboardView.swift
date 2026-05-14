// CustomerDashboardView.swift
// Customer Home tab — liquid-glass redesign.
// Hi-greeting · cut-off banner · warehouse terminal · quick actions · stat tiles.

import SwiftUI
import ThapsusShared

struct CustomerDashboardView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var warehouseVM: WarehouseViewModel? = nil
    @State private var warehouseObs: StateFlowObserver<WarehouseViewModelUiState>? = nil
    @State private var dashVM: CustomerDashboardViewModel? = nil
    @State private var dashObs: StateFlowObserver<DashboardState>? = nil

    @State private var copied: Bool = false
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

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                topBar
                header
                CutoffBannerView()
                warehouseCard
                activeInvoicesSection
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
            NavigationStack { NewOrderView() }.glassSheet(detents: [.large, .medium])
        }
        .sheet(isPresented: $showingBuyForMe) {
            NavigationStack { BuyForMeView() }.glassSheet(detents: [.large, .medium])
        }
        .sheet(item: $payTarget) { target in
            PayInvoiceView(
                targetKind: target.kind,
                targetId: target.id,
                targetTitle: target.title,
                amountKesGross: target.amountKes
            )
        }
        .refreshable { dashVM?.refresh(); warehouseVM?.load() }
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
            warehouseVM = nil; warehouseObs = nil
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
        HomeGreetingCarousel(vm: dashVM)
    }

    // MARK: - Warehouse address card (matches mockup)

    @ViewBuilder
    private var warehouseCard: some View {
        let auth = env.session as? AuthSessionAuthenticated
        let userName = (auth?.profile?.fullName?.isEmpty == false) ? auth!.profile!.fullName! : "Customer"
        let warehouseCode: String = {
            if let id = auth?.profile?.warehouseId, !id.isEmpty { return id }
            return "THP-XXXXXX"
        }()
        let lines: [String] = {
            if case let loaded as WarehouseViewModelUiStateLoaded = warehouseObs?.value,
               let uk = loaded.addresses["UK"]?.lines, !uk.isEmpty {
                return uk
            }
            return ["Unit 12, Pinewood Court", "Stockport, SK6 1AA, UK"]
        }()

        GlassPanel(corner: LG.Radius.xl, padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    LGEyebrow(text: "Your routing reference")
                    Spacer()
                    LGPill(text: "Active", tone: .ok)
                }
                Text(warehouseCode)
                    .font(.mono(22, weight: .bold))
                    .foregroundStyle(LG.fg)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Thapsus Cargo · \(warehouseCode)")
                        .font(.body(13, weight: .semibold))
                        .foregroundStyle(LG.fg2)
                    ForEach(lines, id: \.self) { line in
                        Text(line)
                            .font(.body(13, weight: .medium))
                            .foregroundStyle(LG.fg3)
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: LG.Radius.md, style: .continuous)
                        .fill(LG.line)
                )

                Button {
                    UIPasteboard.general.string = "\(userName)\n\(warehouseCode)\n" + lines.joined(separator: "\n")
                    copied = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { copied = false }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: copied ? "checkmark.circle.fill" : "doc.on.doc")
                        Text(copied ? "Copied!" : "Copy address")
                    }
                }
                .buttonStyle(LGGlassButtonStyle(compact: true))
            }
        }
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
    /// subtitle right) so the two cards align visually. Tonal contrast
    /// (glass vs accent gradient) carries the primary-vs-secondary
    /// hierarchy without needing labels like "PRIMARY".
    private var preRegisterCard: some View {
        Button { showingNewOrder = true } label: {
            HStack(alignment: .center, spacing: 14) {
                ZStack {
                    Circle().fill(LG.glassBgStrong)
                    Image(systemName: "plus.rectangle.on.rectangle")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(LG.accent2)
                }
                .frame(width: 48, height: 48)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Pre-register a parcel")
                        .font(.body(17, weight: .bold))
                        .foregroundStyle(LG.fg)
                        .multilineTextAlignment(.leading)
                    Text("Already bought somewhere we don't cover? Tell us it's coming.")
                        .font(.body(13, weight: .medium))
                        .foregroundStyle(LG.fg3)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)

                Image(systemName: "arrow.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(LG.fg3)
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                ZStack {
                    RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                        .fill(.ultraThinMaterial)
                    RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                        .fill(LG.glassBg)
                }
            )
            .overlay(
                RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                    .strokeBorder(LG.glassBorder, lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.08), radius: 14, x: 0, y: 8)
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
        }
        if warehouseVM == nil {
            let vm = ThapsusSdk.shared.warehouseViewModel()
            warehouseVM = vm
            vm.load()
            warehouseObs = StateFlowObserver(initial: vm.state.value) { vm.state }
        }
    }

    // MARK: - Active invoices section (under the address card)

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

}
