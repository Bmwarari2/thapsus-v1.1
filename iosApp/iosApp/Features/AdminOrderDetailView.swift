// AdminOrderDetailView.swift
// Drill-down for an admin tapping an order row. Reads GET /api/orders/:id
// (admin bypass) and renders the live cost_breakdown the server recomputes
// from current pricing — same data the React webapp's OrderDetail.jsx shows.

import SwiftUI
import ThapsusShared

struct AdminOrderDetailView: View {
    let orderId: String

    @State private var vm: AdminOrderDetailViewModel?
    @State private var observer: StateFlowObserver<AdminOrderDetailViewModelUiState>?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Order")
        .glassNavigationBar(displayMode: .inline)
        .task { bootstrap() }
        .onDisappear { vm?.clear(); vm = nil; observer = nil }
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as AdminOrderDetailViewModelUiStateLoaded:
            header(loaded.order)
            customerLine(loaded.order)
            specsCard(loaded.order)
            costBreakdownCard(loaded.order)
            packagesCard(loaded.order)
            timestampsCard(loaded.order)
        case is AdminOrderDetailViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
        case let err as AdminOrderDetailViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func header(_ order: AdminOrderDetailDto) -> some View {
        InkFeatureCard {
            VStack(alignment: .leading, spacing: 6) {
                EyebrowPill(label: "Admin", systemImage: "shippingbox.fill")
                Text(order.trackingNumber ?? String(order.id.prefix(8)))
                    .font(.title3.monospaced().weight(.heavy))
                    .foregroundStyle(Brand.cream)
                if let d = order.description_, !d.isEmpty {
                    Text(d).font(.subheadline).foregroundStyle(Brand.cream.opacity(0.8))
                }
                statusBadge(order.status).padding(.top, 4)
            }
        }
    }

    @ViewBuilder
    private func customerLine(_ order: AdminOrderDetailDto) -> some View {
        CrystalCard {
            HStack {
                Image(systemName: "person.crop.circle").foregroundStyle(Brand.orange)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Customer").font(.eyebrow).foregroundStyle(.secondary)
                    Text(order.userId.prefix(8) + "…")
                        .font(.caption.monospaced())
                        .foregroundStyle(Brand.ink)
                }
            }
        }
    }

    @ViewBuilder
    private func specsCard(_ order: AdminOrderDetailDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Specs").font(.headline).foregroundStyle(Brand.ink)
                row("Retailer", order.retailer ?? "—")
                row("Market", order.market ?? "—")
                row("Speed", order.shippingSpeed?.capitalized ?? "—")
                if let weight = order.weightKg?.doubleValue {
                    row("Weight", String(format: "%.2f kg", weight))
                }
                if let value = order.declaredValue?.doubleValue {
                    row("Declared value", String(format: "£ %.2f", value))
                }
                if let elec = order.electronicsItem, !elec.isEmpty {
                    row("Electronics", elec.replacingOccurrences(of: "_", with: " ").capitalized)
                }
                if let n = order.orderNotes, !n.isEmpty {
                    Divider()
                    Text("Internal notes").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    Text(n).font(.subheadline).foregroundStyle(Brand.ink)
                }
            }
        }
    }

    @ViewBuilder
    private func costBreakdownCard(_ order: AdminOrderDetailDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Cost breakdown").font(.headline).foregroundStyle(Brand.ink)
                if let cb = order.costBreakdown {
                    if let line = cb.breakdown.baseShipping {
                        costRow(line.label ?? "Base shipping", line.amount)
                    }
                    if let line = cb.breakdown.electronicsHandling, line.amount > 0 {
                        costRow(line.label ?? "Electronics handling", line.amount)
                    }
                    if let line = cb.breakdown.handlingFee, line.amount > 0 {
                        costRow(line.label ?? "Handling fee", line.amount)
                    }
                    if let line = cb.breakdown.customsEstimate, line.amount > 0 {
                        costRow(line.label ?? "Customs estimate", line.amount)
                    }
                    Divider()
                    HStack {
                        Text("Total").font(.headline)
                        Spacer()
                        Text(String(format: "£ %.2f", cb.total))
                            .font(.title3.monospaced().weight(.heavy))
                            .foregroundStyle(Brand.orange)
                    }
                    Text("* Estimated. Final cost confirmed after weighing.")
                        .font(.caption2).foregroundStyle(.tertiary)
                } else {
                    Text("Server didn't return a breakdown for this order.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                if let actual = order.actualCost?.doubleValue {
                    Divider()
                    row("Actual charged", String(format: "£ %.2f", actual))
                }
                if let duty = order.customsDuty?.doubleValue, duty > 0 {
                    // Stored value is now GBP (post-pricing-fix). The cost
                    // breakdown above already shows it; this row stays as a
                    // fallback when the breakdown isn't returned, otherwise
                    // we'd render it twice. Customs is set by KRA at clearing.
                    row("Customs duty (est.)", String(format: "£ %.2f", duty))
                }
            }
        }
    }

    @ViewBuilder
    private func packagesCard(_ order: AdminOrderDetailDto) -> some View {
        if !order.packages.isEmpty {
            CrystalCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Packages (\(order.packages.count))")
                        .font(.headline).foregroundStyle(Brand.ink)
                    ForEach(order.packages, id: \.id) { pkg in
                        VStack(alignment: .leading, spacing: 2) {
                            HStack {
                                Text(pkg.description_ ?? String(pkg.id.prefix(8)))
                                    .font(.subheadline.weight(.semibold))
                                Spacer()
                                if let s = pkg.status {
                                    Text(s.replacingOccurrences(of: "_", with: " ").uppercased())
                                        .font(.system(size: 9, weight: .heavy)).tracking(2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            HStack(spacing: 12) {
                                if let kg = pkg.weightKg?.doubleValue {
                                    Text(String(format: "%.2f kg", kg))
                                        .font(.caption.monospacedDigit())
                                }
                                if let loc = pkg.warehouseLocation {
                                    Text("@ \(loc)").font(.caption)
                                }
                            }
                            .foregroundStyle(.secondary)
                        }
                        Divider()
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func timestampsCard(_ order: AdminOrderDetailDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                if let c = order.createdAt { row("Created", c) }
                if let u = order.updatedAt { row("Updated", u) }
            }
        }
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label.uppercased())
                .font(.caption2.weight(.heavy)).tracking(2)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value).font(.subheadline).foregroundStyle(Brand.ink)
        }
    }

    private func costRow(_ label: String, _ amount: Double) -> some View {
        HStack {
            Text(label).font(.subheadline).foregroundStyle(Brand.ink)
            Spacer()
            Text(String(format: "£ %.2f", amount))
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(Brand.ink)
        }
    }

    private func statusBadge(_ status: String) -> some View {
        let color: Color
        switch status {
        case "delivered": color = .green
        case "cancelled": color = .red
        case "out_for_delivery": color = .cyan
        case "in_transit", "consolidating", "manifested": color = .blue
        case "customs", "awaiting_duty_payment": color = .orange
        default: color = .gray
        }
        return Text(status.replacingOccurrences(of: "_", with: " ").uppercased())
            .font(.system(size: 9, weight: .heavy)).tracking(2)
            .foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Capsule().fill(color.opacity(0.16)))
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.adminOrderDetailViewModel(orderId: orderId)
        vm = model
        model.load()
        observer = StateFlowObserver(initial: model.state.value) { model.state }
    }
}
