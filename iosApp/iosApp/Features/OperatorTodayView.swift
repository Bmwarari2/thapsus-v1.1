// OperatorTodayView.swift
// Spec §3.3 /ops/today: parcels expected today, late, ready to consolidate,
// in transit, held. Backed by OperatorTodayViewModel + RealtimeSync.

import SwiftUI
import ThapsusShared

struct OperatorTodayView: View {
    @State private var vm: OperatorTodayViewModel?
    @State private var state: StateFlowObserver<TodayState>?
    @State private var refreshing: StateFlowObserver<KotlinBoolean>?
    // BFM queue VM — reused from the dedicated OpsBuyForMeQueueView so
    // we share Realtime subscription state. Counts pending_quote +
    // quoted client-side from the live `orders` StateFlow.
    @State private var bfmVM: OpsBuyForMeViewModel?
    @State private var bfmOrders: StateFlowObserver<[BuyForMeOrderDto]>?

    private var bfmUnquoted: Int {
        bfmOrders?.value.filter { $0.status == "pending_quote" }.count ?? 0
    }
    private var bfmAwaitingPayment: Int {
        bfmOrders?.value.filter { $0.status == "quoted" }.count ?? 0
    }

    var body: some View {
        ScrollView {
            GlassEffectContainer(spacing: 16) {
                VStack(spacing: 16) {
                    SectionHeader(
                        title: "Today",
                        subtitle: "Concierge requests first; parcel pipeline below."
                    )

                    // BFM queue leads after the BFM-primary pivot. Two
                    // live-count tiles deep-link straight into the queue.
                    bfmStatsRow

                    let s = state?.value
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        StatTile(label: "Expected", value: "\(s?.expectedToday.count ?? 0)", systemImage: "shippingbox")
                        StatTile(
                            label: "Late",
                            value: "\(s?.late.count ?? 0)",
                            systemImage: "exclamationmark.octagon",
                            tint: (s?.late.count ?? 0) > 0 ? .red : .secondary
                        )
                        StatTile(label: "Ready to consol.", value: "\(s?.readyToConsolidate.count ?? 0)", systemImage: "tray.full")
                        StatTile(label: "In transit", value: "\(s?.inTransit.count ?? 0)", systemImage: "airplane")
                        StatTile(label: "Held", value: "\(s?.held.count ?? 0)", systemImage: "exclamationmark.triangle", tint: .orange)
                    }

                    if let late = s?.late, !late.isEmpty {
                        // Pre-registered for >7 days without arriving — flagged so
                        // operators can chase the customer. Threshold lives in
                        // shared/TodayState.LATE_THRESHOLD_DAYS.
                        lateSection(parcels: late)
                    }
                    section("Ready to consolidate", parcels: s?.readyToConsolidate ?? [])
                    section("In transit", parcels: s?.inTransit ?? [])
                    section("Held — needs action", parcels: s?.held ?? [])
                }
                .padding(20)
            }
        }
        .navigationTitle("Today")
        .glassNavigationBar()
        .refreshable { vm?.refresh() }
        .task {
            guard vm == nil else { return }
            let v = ThapsusSdk.shared.operatorTodayViewModel()
            self.vm = v
            self.state = StateFlowObserver(initial: TodayState.companion.empty()) { v.state }
            self.refreshing = StateFlowObserver(initial: KotlinBoolean(bool: false)) { v.refreshing }

            // Spin up the BFM queue VM alongside — its `orders` StateFlow
            // is the source of truth for the two leading stat tiles. The
            // VM subscribes to Realtime in init, so counts stay live as
            // customers submit / operators quote.
            let b = ThapsusSdk.shared.opsBuyForMeViewModel()
            self.bfmVM = b
            self.bfmOrders = StateFlowObserver(initial: b.orders.value) { b.orders }
        }
        .onDisappear {
            vm?.clear(); vm = nil; state = nil; refreshing = nil
            bfmVM?.clear(); bfmVM = nil; bfmOrders = nil
        }
    }

    /// Two-tile stats row: unquoted requests (status=pending_quote) and
    /// quoted-but-unpaid (status=quoted). Both tiles wrap a NavigationLink
    /// to the full queue so a tap takes the operator straight in.
    @ViewBuilder
    private var bfmStatsRow: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            NavigationLink { OpsBuyForMeQueueView() } label: {
                bfmTile(
                    label: "Unquoted BFM",
                    value: bfmUnquoted,
                    systemImage: "wand.and.stars"
                )
            }
            .buttonStyle(.plain)

            NavigationLink { OpsBuyForMeQueueView() } label: {
                bfmTile(
                    label: "Awaiting payment",
                    value: bfmAwaitingPayment,
                    systemImage: "hourglass"
                )
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private func bfmTile(label: String, value: Int, systemImage: String) -> some View {
        GlassCard(tint: Brand.orange.opacity(0.12)) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.title3)
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(Brand.orange)
                    )
                VStack(alignment: .leading, spacing: 2) {
                    Text(label.uppercased())
                        .font(.caption2.weight(.heavy)).tracking(1.5)
                        .foregroundStyle(.secondary)
                    Text("\(value)")
                        .font(.title.weight(.heavy))
                        .foregroundStyle(Brand.ink)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.tertiary)
            }
        }
    }

    @ViewBuilder
    private func lateSection(parcels: [PackageDto]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "exclamationmark.octagon.fill").foregroundStyle(.red)
                Text("Late — \(parcels.count) parcel\(parcels.count == 1 ? "" : "s") overdue").font(.headline)
            }
            .padding(.horizontal, 4)
            Text("Pre-registered \(LATE_THRESHOLD_DAYS_LABEL) ago and still hasn't arrived. Reach out to the customer.")
                .font(.caption).foregroundStyle(.secondary).padding(.horizontal, 4)
            ForEach(parcels, id: \.id) { p in
                GlassCard(tint: Color.red.opacity(0.10)) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(p.description_ ?? p.retailer ?? "Parcel").font(.headline).lineLimit(1)
                            if let r = p.retailer { Text(r).font(.caption).foregroundStyle(.secondary) }
                            if let createdAt = p.createdAt {
                                Text("Pre-registered \(formatRelative(createdAt))")
                                    .font(.caption2).foregroundStyle(.red.opacity(0.85))
                            }
                        }
                        Spacer()
                    }
                }
            }
        }
    }

    private var LATE_THRESHOLD_DAYS_LABEL: String { "more than 7 days" }

    private func formatRelative(_ iso: String) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let parsed = f.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        guard let date = parsed else { return iso.prefix(10).description }
        let style = RelativeDateTimeFormatter()
        style.unitsStyle = .full
        return style.localizedString(for: date, relativeTo: Date())
    }

    @ViewBuilder
    private func section(_ title: String, parcels: [PackageDto]) -> some View {
        if !parcels.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text(title).font(.headline).padding(.horizontal, 4)
                ForEach(parcels, id: \.id) { p in
                    GlassCard {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(p.description_ ?? p.retailer ?? "Parcel").font(.headline).lineLimit(1)
                                if let bc = p.barcode {
                                    Text(bc).font(.caption.monospaced()).foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            if let kg = p.chargeableKg?.doubleValue {
                                Text(String(format: "%.1f kg", kg))
                                    .font(.subheadline.monospacedDigit())
                            }
                        }
                    }
                }
            }
        }
    }
}
