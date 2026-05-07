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
    @State private var showHowItWorks: Bool = false
    @State private var showingNewOrder = false
    @State private var showingBuyForMe = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                topBar
                header
                CutoffBannerView()
                warehouseCard
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
        .refreshable { dashVM?.refresh(); warehouseVM?.load() }
        .task { bootstrap() }
        .onDisappear {
            dashVM?.clear(); dashVM = nil; dashObs = nil
            warehouseVM = nil; warehouseObs = nil
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
                    .font(.system(size: 17, weight: .semibold))
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
        }
        .padding(.top, 8)
        .padding(.bottom, 4)
    }

    // MARK: - Header (greeting)

    private var header: some View {
        let parcels = dashObs?.value.totalParcels ?? 0
        let inFlight = dashObs?.value.inFlightParcels ?? 0
        let summary: String = {
            if inFlight > 0 { return "\(inFlight) parcel\(inFlight == 1 ? "" : "s") in transit" }
            if parcels > 0 { return "\(parcels) parcel\(parcels == 1 ? "" : "s") on file" }
            return "Ready when you are"
        }()
        return VStack(alignment: .leading, spacing: 4) {
            Text("Hi \(firstName(env.session)) 👋")
                .font(.display(28, weight: .heavy))
                .foregroundStyle(LG.fg)
            Text(summary)
                .font(.body(14.5, weight: .medium))
                .foregroundStyle(LG.fg3)
        }
        .padding(.top, 8)
        .padding(.bottom, 4)
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

    // MARK: - Action grid (New order / Buy for me)

    private var actionGrid: some View {
        HStack(spacing: 12) {
            LGActionCard(
                title: "New order",
                subtitle: "Pre-register parcel",
                systemImage: "plus",
                tone: .accent,
                action: { showingNewOrder = true }
            )
            LGActionCard(
                title: "Buy for me",
                subtitle: "Concierge purchase",
                systemImage: "gift.fill",
                action: { showingBuyForMe = true }
            )
        }
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

    private func firstName(_ session: any AuthSession) -> String {
        if let auth = session as? AuthSessionAuthenticated {
            if let name = auth.profile?.fullName, !name.isEmpty {
                return name.split(separator: " ").first.map(String.init) ?? name
            }
            return auth.email?.split(separator: "@").first.map(String.init) ?? "there"
        }
        return "there"
    }
}
