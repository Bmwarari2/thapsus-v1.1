// WarehouseAddressView.swift
// UK warehouse address card + how-to-ship guide. Mirrors the webapp's
// dashboard "warehouse address terminal" + the dedicated ShipInstructions
// page, both rolled into one SwiftUI view that scrolls comfortably on phone.

import SwiftUI
import ThapsusShared

struct WarehouseAddressView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var vm: WarehouseViewModel? = nil
    @State private var observer: StateFlowObserver<WarehouseViewModelUiState>? = nil
    @State private var copied: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Stockport HQ", systemImage: "shippingbox.fill")
                EditorialHeader(title: "Your UK\nwarehouse",
                                subtitle: "Ship to this address. We'll measure, photograph and consolidate for the next weekly flight.")

                addressCard

                SectionHeader(title: "How to ship", subtitle: "Three quick rules to make sure your parcel hits the next consolidation cleanly.")

                CrystalCard {
                    VStack(alignment: .leading, spacing: 14) {
                        howToRow(num: "1", title: "Use your warehouse code", body: "Always include \(env.currentUserID.map { _ in "TC-XXXX" } ?? "TC-XXXX") on the label so we tag the parcel to your account.")
                        Divider().background(Brand.ink.opacity(0.1))
                        howToRow(num: "2", title: "Pre-register every parcel", body: "Tap Pre-register from the dashboard before the box arrives — it speeds up intake on receipt.")
                        Divider().background(Brand.ink.opacity(0.1))
                        howToRow(num: "3", title: "Watch the cut-off", body: "Parcels in by Thursday 5 pm catch that week's flight. Anything later rolls to the next.")
                    }
                }

                HowItWorksView()
                    .padding(.top, 8)
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Warehouse")
        .glassNavigationBar()
        .task { bootstrap() }
    }

    @ViewBuilder
    private var addressCard: some View {
        switch observer?.value {
        case let loaded as WarehouseViewModelUiStateLoaded:
            if let uk = loaded.addresses["UK"] {
                addressInkCard(lines: uk.lines)
            } else {
                addressInkCard(lines: [])
            }
        case is WarehouseViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        case is WarehouseViewModelUiStateError:
            addressInkCard(lines: defaultLines)
        default:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        }
    }

    private var defaultLines: [String] {
        ["31 Collingwood Close", "Hazel Grove, Stockport", "SK7 4LB", "United Kingdom"]
    }

    private func addressInkCard(lines: [String]) -> some View {
        let resolved: [String] = lines.isEmpty ? defaultLines : lines
        // Previously rendered `env.currentUserID` (the user UUID) as the
        // "warehouse code" — confusing and not what the operator scans.
        // Pull the real warehouse code (e.g. STK-01-…) and the customer
        // name from the auth profile so the label on the parcel matches.
        let auth = env.session as? AuthSessionAuthenticated
        func nonEmpty(_ s: String?) -> String? {
            let t = s?.trimmingCharacters(in: .whitespacesAndNewlines)
            return (t?.isEmpty == false) ? t : nil
        }
        let displayName   = nonEmpty(auth?.profile?.fullName)
        let warehouseCode = nonEmpty(auth?.profile?.warehouseId) ?? "TC-XXXX"
        return InkFeatureCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 8) {
                    Image(systemName: "mappin.and.ellipse").foregroundStyle(Brand.orange)
                    Text("Warehouse address").font(.headline)
                }
                VStack(alignment: .leading, spacing: 6) {
                    if let name = displayName {
                        Text(name)
                            .font(.system(.title3, design: .monospaced).weight(.heavy))
                            .foregroundStyle(Brand.orange)
                    }
                    Text(warehouseCode)
                        .font(.system(.title3, design: .monospaced).weight(.heavy))
                        .foregroundStyle(Brand.orange)
                    ForEach(resolved, id: \.self) { line in
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

                Button(action: { copy(lines: resolved, code: warehouseCode) }) {
                    HStack(spacing: 8) {
                        Image(systemName: copied ? "checkmark.circle.fill" : "doc.on.doc")
                        Text(copied ? "Copied!" : "Copy address")
                    }
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
            }
        }
    }

    private func howToRow(num: String, title: String, body: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Text(num)
                .font(.system(size: 20, weight: .heavy))
                .foregroundStyle(Brand.orange)
                .frame(width: 36, height: 36)
                .background(Circle().fill(Brand.orange.opacity(0.12)))
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline).foregroundStyle(Brand.ink)
                Text(body).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    private func copy(lines: [String], code: String) {
        UIPasteboard.general.string = ([code] + lines).joined(separator: "\n")
        copied = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { copied = false }
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.warehouseViewModel()
        vm = model
        model.load()
        observer = StateFlowObserver(initial: model.state.value) {
            model.state
        }
    }
}
