// CustomerDashboardView.swift
// Customer Home tab — mirrors the webapp dashboard's "Welcome, X" + warehouse-
// address terminal as the primary surface. The dense stats grid + recent
// shipments now live behind the Track tab so the home stays editorial.

import SwiftUI
import ThapsusShared

struct CustomerDashboardView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var warehouseVM: WarehouseViewModel? = nil
    @State private var warehouseObs: StateFlowObserver<WarehouseViewModelUiState>? = nil
    @State private var dashVM: CustomerDashboardViewModel? = nil
    @State private var dashObs: StateFlowObserver<DashboardState>? = nil

    @State private var copied: Bool = false
    /// Mirrors the webapp Home — the "How it works" guide is always visible.
    @State private var showHowItWorks: Bool = true
    @State private var showingNewOrder = false
    @State private var showingBuyForMe = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Client Terminal", systemImage: "antenna.radiowaves.left.and.right", dotColor: .green)
                EditorialHeader(
                    title: "Welcome,\n\(firstName(env.session))",
                    subtitle: "Your global logistics overview and active shipments pipeline."
                )

                CutoffBannerView()

                warehouseCard

                quickStats

                quickActions

                Button(action: { showHowItWorks.toggle() }) {
                    HStack(spacing: 8) {
                        Image(systemName: showHowItWorks ? "chevron.up" : "questionmark.circle")
                        Text(showHowItWorks ? "Hide guide" : "How it works")
                    }
                }
                .buttonStyle(.bordered).tint(Brand.orange)

                if showHowItWorks {
                    HowItWorksView()
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            .padding(20)
            .padding(.bottom, 80)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .glassNavigationBar()
        .brandToolbar()
        .overlay(alignment: .top) {
            NotificationBannerView()
        }
        .npsAutoPrompt()
        .overlay(alignment: .bottomTrailing) {
            // No glassEffectID morph — the shared-element transition was
            // claiming the FAB's hit region during the sheet's dismissal
            // animation and swallowing the next tap. The button now just
            // toggles `showingNewOrder` reliably; the sheet still uses
            // glassSheet detents for the visual.
            // Plus FAB now offers two flows: ship something to UK
            // (NewOrderView) or ask us to buy something for them
            // (BuyForMeView). Menu rather than confirmation dialog so each
            // option can carry an icon and the choice surface is glass-y.
            Menu {
                Button {
                    showingNewOrder = true
                } label: {
                    Label("New order", systemImage: "shippingbox.fill")
                }
                Button {
                    showingBuyForMe = true
                } label: {
                    Label("Buy for me", systemImage: "wand.and.stars")
                }
            } label: {
                Image(systemName: "plus")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(width: Layout.fabSize, height: Layout.fabSize)
                    .contentShape(Circle())
            }
            .menuStyle(.button)
            .glassEffect(.regular.tint(Brand.orange).interactive(), in: Circle())
            .shadow(color: .black.opacity(0.18), radius: 18, x: 0, y: 10)
            .accessibilityLabel(Text("Create"))
            .padding(.trailing, 20)
            .padding(.bottom, 24)
        }
        .sheet(isPresented: $showingNewOrder) {
            NavigationStack {
                NewOrderView()
            }
            .glassSheet(detents: [.large, .medium])
        }
        .sheet(isPresented: $showingBuyForMe) {
            NavigationStack {
                BuyForMeView()
            }
            .glassSheet(detents: [.large, .medium])
        }
        .refreshable { dashVM?.refresh(); warehouseVM?.load() }
        .task { bootstrap() }
        .onDisappear {
            dashVM?.clear(); dashVM = nil; dashObs = nil
            warehouseVM = nil; warehouseObs = nil
        }
    }

    // MARK: - Warehouse address (the hero card)

    @ViewBuilder
    private var warehouseCard: some View {
        switch warehouseObs?.value {
        case let loaded as WarehouseViewModelUiStateLoaded:
            inkAddressCard(lines: loaded.addresses["UK"]?.lines ?? defaultLines)
        case is WarehouseViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.vertical, 24)
        default:
            inkAddressCard(lines: defaultLines)
        }
    }

    private var defaultLines: [String] {
        ["31 Collingwood Close", "Hazel Grove, Stockport", "SK7 4LB", "United Kingdom"]
    }

    private func inkAddressCard(lines: [String]) -> some View {
        let userName = userFullName(env.session) ?? "Customer"
        let warehouseCode = warehouseCodeFor(env.session) ?? "TC-XXXX"
        return InkFeatureCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 8) {
                    Image(systemName: "mappin.and.ellipse").foregroundStyle(Brand.orange)
                    Text("YOUR WAREHOUSE ADDRESS")
                        .font(.system(size: 13, weight: .heavy))
                        .tracking(2)
                        .foregroundStyle(Brand.cream)
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text(userName)
                        .font(.system(.title3, design: .monospaced).weight(.heavy))
                        .foregroundStyle(Brand.orange)
                    Text(warehouseCode)
                        .font(.system(.title3, design: .monospaced).weight(.heavy))
                        .foregroundStyle(Brand.orange)
                    ForEach(lines, id: \.self) { line in
                        Text(line)
                            .font(.system(.callout, design: .monospaced))
                            .foregroundStyle(Brand.cream.opacity(0.85))
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(.white.opacity(0.06))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(.white.opacity(0.12), lineWidth: 1)
                )

                Button(action: { copy(name: userName, code: warehouseCode, lines: lines) }) {
                    HStack(spacing: 8) {
                        Image(systemName: copied ? "checkmark.circle.fill" : "doc.on.doc")
                        Text(copied ? "Copied!" : "Copy address")
                    }
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
            }
        }
    }

    private func copy(name: String, code: String, lines: [String]) {
        UIPasteboard.general.string = ([name, code] + lines).joined(separator: "\n")
        copied = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { copied = false }
    }

    // MARK: - Quick stats (compact)

    @ViewBuilder
    private var quickStats: some View {
        let s = dashObs?.value
        HStack(spacing: 12) {
            BigStatTile(
                eyebrow: "Active orders",
                value: "\(s?.totalParcels ?? 0)",
                systemImage: "shippingbox.fill",
                accent: Brand.orange
            )
            BigStatTile(
                eyebrow: "In flight",
                value: "\(s?.inFlightParcels ?? 0)",
                systemImage: "airplane",
                accent: .blue
            )
        }
    }

    // MARK: - Quick actions rail

    @ViewBuilder
    private var quickActions: some View {
        GradientBorderCard {
            VStack(spacing: 10) {
                NavigationLink {
                    TrackingView()
                } label: {
                    actionRow(icon: "shippingbox.fill", title: "View my packages", tone: .ink)
                }
                .buttonStyle(.plain)
                NavigationLink { CreditCenterView() } label: {
                    actionRow(icon: "gift.fill", title: "My credit & referrals", tone: .orange)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private enum ActionTone { case ink, orange }

    private func actionRow(icon: String, title: String, tone: ActionTone) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.headline)
                .foregroundStyle(tone == .ink ? Brand.cream : .white)
                .frame(width: 32)
            Text(title.uppercased())
                .font(.system(size: 12, weight: .heavy))
                .tracking(2)
                .foregroundStyle(tone == .ink ? Brand.cream : .white)
            Spacer()
            Image(systemName: "arrow.up.right")
                .foregroundStyle((tone == .ink ? Brand.cream : .white).opacity(0.7))
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(tone == .ink ? Brand.ink : Brand.orange)
        )
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
            return auth.email ?? "there"
        }
        return "there"
    }

    private func userFullName(_ session: any AuthSession) -> String? {
        if let auth = session as? AuthSessionAuthenticated, let name = auth.profile?.fullName, !name.isEmpty {
            return name
        }
        return nil
    }

    private func warehouseCodeFor(_ session: any AuthSession) -> String? {
        if let auth = session as? AuthSessionAuthenticated, let id = auth.profile?.warehouseId, !id.isEmpty {
            return id
        }
        return nil
    }
}
