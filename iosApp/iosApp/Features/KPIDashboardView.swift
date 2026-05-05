// KPIDashboardView.swift
// Spec §4.12. Founder dashboard. Computes from the cache so it works offline
// and updates reactively when RealtimeSync writes through.

import SwiftUI
import ThapsusShared

struct KPIDashboardView: View {
    @State private var vm: KPIDashboardViewModel?
    @State private var snap: StateFlowObserver<KPISnapshot>?
    @State private var serverObs: StateFlowObserver<AdminStatsFullResponse?>?
    @State private var errorObs: StateFlowObserver<ErrorLogStatsResponse?>?
    @State private var founderObs: StateFlowObserver<KpiBlock?>?

    var body: some View {
        ScrollView {
            GlassEffectContainer(spacing: 16) {
                VStack(spacing: 16) {
                    SectionHeader(
                        title: "KPI",
                        subtitle: "Live cache + server snapshot"
                    )

                    let s = snap?.value
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        StatTile(
                            label: "Chargeable kg",
                            value: String(format: "%.1f", Double(truncating: (s?.chargeableKgThisWeek ?? 0) as NSNumber)),
                            systemImage: "scalemass"
                        )
                        StatTile(
                            label: "Total parcels",
                            value: "\(s?.totalParcels ?? 0)",
                            systemImage: "shippingbox"
                        )
                        StatTile(
                            label: "Delivered",
                            value: "\(s?.deliveredCount ?? 0)",
                            systemImage: "checkmark.circle"
                        )
                        StatTile(
                            label: "In transit",
                            value: "\(s?.inTransitCount ?? 0)",
                            systemImage: "airplane"
                        )
                        StatTile(
                            label: "Held",
                            value: "\(s?.heldCount ?? 0)",
                            systemImage: "exclamationmark.triangle",
                            tint: .orange
                        )
                        StatTile(
                            label: "On-time %",
                            value: String(format: "%.0f%%", Double(truncating: (s?.onTimePercent ?? 0) as NSNumber)),
                            systemImage: "clock.badge.checkmark"
                        )
                    }

                    GlassCard {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Average declared value").font(.caption).foregroundStyle(.secondary)
                            Text(String(format: "£%.2f / kg", Double(s?.averageMarginPerKgPence ?? 0) / 100))
                                .font(.title2.weight(.semibold))
                        }
                    }

                    if let founder = founderObs?.value {
                        founderTilesCard(founder)
                    }

                    if let stats = serverObs?.value?.stats {
                        revenueCard(stats)
                        statusBreakdownCard(stats)
                        dailyOrdersCard(stats)
                    } else {
                        ProgressView().padding(.top, 12)
                    }

                    if let err = errorObs?.value {
                        errorStatsCard(err)
                    }
                }
                .padding(20)
            }
        }
        .navigationTitle("KPI")
        .glassNavigationBar()
        .refreshable { vm?.refresh() }
        .task {
            guard vm == nil else { return }
            let v = ThapsusSdk.shared.kpiDashboardViewModel()
            self.vm = v
            self.snap = StateFlowObserver(initial: KPISnapshot.companion.empty()) { v.snapshot }
            self.serverObs = StateFlowObserver(initial: nil) { v.serverStats }
            self.errorObs = StateFlowObserver(initial: nil) { v.errorStats }
            self.founderObs = StateFlowObserver(initial: nil) { v.founder }
            v.refresh()
        }
        .onDisappear {
            vm?.clear(); vm = nil
            snap = nil; serverObs = nil; errorObs = nil; founderObs = nil
        }
    }

    /// Founder-tier tiles from `GET /api/kpi`. This-week kg + parcel count,
    /// trend vs last week, on-time %, NPS avg, wallet KES, complaints/100,
    /// pending inbound. All values arrive from the server and tolerate empty
    /// tables via the loose-numeric DTOs.
    @ViewBuilder
    private func founderTilesCard(_ k: KpiBlock) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Founder snapshot")
                    .font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    miniStat("This week (kg)", String(format: "%.1f", k.kgThisWeek), tint: Brand.ink)
                    miniStat("Last week (kg)", String(format: "%.1f", k.kgLastWeek), tint: Brand.ink)
                    miniStat("Parcels (week)", "\(k.parcelsThisWeek)", tint: Brand.orange)
                    miniStat("Trend", trendLabel(k.kgTrendPct?.doubleValue),
                             tint: trendColor(k.kgTrendPct?.doubleValue))
                    miniStat("On-time", percentLabel(k.onTimePct?.doubleValue), tint: .green)
                    miniStat("Complaints / 100", String(format: "%.2f", k.complaintsPer100), tint: .orange)
                    miniStat("NPS", k.npsAvg.map { String(format: "%.1f", $0.doubleValue) } ?? "—",
                             tint: .blue)
                    miniStat("NPS responses", "\(k.npsResponses)", tint: .secondary)
                    miniStat("Wallet KES", "KES \(decimal(k.walletKes))", tint: .purple)
                    miniStat("Pending inbound", "\(Int(k.pendingInbound))", tint: Brand.orange)
                }
            }
        }
    }

    private func trendLabel(_ pct: Double?) -> String {
        guard let pct else { return "—" }
        let sign = pct >= 0 ? "+" : ""
        return "\(sign)\(String(format: "%.0f", pct))%"
    }

    private func trendColor(_ pct: Double?) -> Color {
        guard let pct else { return .secondary }
        return pct >= 0 ? .green : .red
    }

    private func percentLabel(_ pct: Double?) -> String {
        guard let pct else { return "—" }
        return String(format: "%.0f%%", pct)
    }

    @ViewBuilder
    private func revenueCard(_ stats: AdminStatsBlock) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Revenue (server, KES)")
                    .font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                Text("KES \(decimal(stats.revenue.totalRevenue))")
                    .font(.title.weight(.heavy)).foregroundStyle(Brand.ink)
                HStack(spacing: 12) {
                    miniStat("Deposits", "KES \(decimal(stats.revenue.deposits))", tint: .green)
                    miniStat("Payments", "KES \(decimal(stats.revenue.payments))", tint: .blue)
                }
                Divider()
                HStack(spacing: 12) {
                    miniStat("New users today", "\(stats.users.newToday)", tint: Brand.orange)
                    miniStat("New orders today", "\(stats.orders.newToday)", tint: Brand.orange)
                }
            }
        }
    }

    @ViewBuilder
    private func statusBreakdownCard(_ stats: AdminStatsBlock) -> some View {
        if !stats.orderStatuses.isEmpty {
            GlassCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Order status (server)")
                        .font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                    let total = max(1, stats.orderStatuses.reduce(0) { $0 + Int($1.count) })
                    ForEach(stats.orderStatuses, id: \.status) { slice in
                        statusBar(slice.status, count: Int(slice.count), total: total)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func dailyOrdersCard(_ stats: AdminStatsBlock) -> some View {
        if !stats.dailyOrders.isEmpty {
            GlassCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Last 14 days")
                        .font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                    let maxCount = max(1, stats.dailyOrders.map { Int($0.count) }.max() ?? 1)
                    HStack(alignment: .bottom, spacing: 4) {
                        ForEach(stats.dailyOrders, id: \.date) { point in
                            VStack(spacing: 2) {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Brand.orange)
                                    .frame(height: max(4, CGFloat(Int(point.count)) / CGFloat(maxCount) * 80))
                                Text(point.date.suffix(2)).font(.system(size: 8)).foregroundStyle(.tertiary)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .frame(height: 100)
                }
            }
        }
    }

    @ViewBuilder
    private func errorStatsCard(_ err: ErrorLogStatsResponse) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Error logs")
                    .font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                HStack(spacing: 12) {
                    miniStat("Total", "\(err.stats.total)", tint: Brand.ink)
                    miniStat("Last 24h", "\(err.stats.last24h)", tint: .orange)
                    miniStat("Last 7d", "\(err.stats.last7d)", tint: .red)
                    miniStat("Fatal 24h", "\(err.stats.fatal24h)", tint: .red)
                }
            }
        }
    }

    private func miniStat(_ label: String, _ value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .font(.system(size: 9, weight: .heavy)).tracking(2)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.subheadline.weight(.heavy))
                .foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func statusBar(_ status: String, count: Int, total: Int) -> some View {
        HStack {
            Text(status.replacingOccurrences(of: "_", with: " ").capitalized)
                .font(.caption.weight(.semibold))
                .frame(width: 130, alignment: .leading)
                .foregroundStyle(Brand.ink)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Brand.ink.opacity(0.08))
                    Capsule()
                        .fill(Brand.orange)
                        .frame(width: geo.size.width * CGFloat(count) / CGFloat(total))
                }
            }
            .frame(height: 8)
            Text("\(count)").font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                .frame(width: 36, alignment: .trailing)
        }
    }

    private func decimal(_ n: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.maximumFractionDigits = 0
        return f.string(from: NSNumber(value: n)) ?? "\(Int(n))"
    }
}
