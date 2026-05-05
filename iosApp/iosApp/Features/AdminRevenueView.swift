// AdminRevenueView.swift
// Admin revenue tab — line-item daily breakdown + per-method summary card.
// Mirrors `routes/admin.js:689 GET /api/admin/revenue` + the CSV export at
// `/api/admin/revenue/export`. Audit S3-3.

import SwiftUI
import ThapsusShared

struct AdminRevenueView: View {
    @State private var loading: Bool = true
    @State private var rows: [AdminRevenueRow] = []
    @State private var summary: [AdminRevenueSummaryRow] = []
    @State private var errorMessage: String?
    @State private var startDate: Date = Calendar.current.date(byAdding: .day, value: -30, to: Date())!
    @State private var endDate: Date = Date()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                EditorialHeader(
                    eyebrow: "Compliance",
                    title: "Revenue",
                    subtitle: "Daily completed transactions, by method and type."
                )

                dateFilter

                if loading {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
                } else if let msg = errorMessage {
                    ErrorBanner(title: "Couldn't load revenue", message: msg)
                } else {
                    summaryCard
                    rowsCard
                    exportButton
                }
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Revenue")
        .glassNavigationBar()
        .refreshable { await load() }
        .task { await load() }
    }

    private var dateFilter: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Date range")
                    .font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                HStack(spacing: 12) {
                    DatePicker("From", selection: $startDate, displayedComponents: .date)
                        .labelsHidden()
                    Text("→").foregroundStyle(.secondary)
                    DatePicker("To", selection: $endDate, displayedComponents: .date)
                        .labelsHidden()
                    Spacer()
                    Button("Apply") { Task { await load() } }
                        .buttonStyle(.bordered).tint(Brand.orange)
                }
            }
        }
    }

    @ViewBuilder
    private var summaryCard: some View {
        if !summary.isEmpty {
            CrystalCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("By method")
                        .font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                    ForEach(Array(summary.enumerated()), id: \.offset) { _, s in
                        VStack(alignment: .leading, spacing: 2) {
                            Text((s.paymentMethod ?? "—").capitalized)
                                .font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                            HStack {
                                Text("Deposits: \(money(s.deposits))")
                                Spacer()
                                Text("Payments: \(money(s.payments))")
                                Spacer()
                                Text("Total: \(money(s.total))").fontWeight(.semibold)
                            }
                            .font(.caption).foregroundStyle(.secondary)
                        }
                        Divider().opacity(0.4)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var rowsCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Daily breakdown")
                    .font(.caption.weight(.heavy)).tracking(2).foregroundStyle(.secondary)
                if rows.isEmpty {
                    Text("No completed transactions in this range.")
                        .font(.subheadline).foregroundStyle(.secondary)
                } else {
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, r in
                        rowView(r)
                        Divider().opacity(0.4)
                    }
                }
            }
        }
    }

    private func rowView(_ r: AdminRevenueRow) -> some View {
        HStack(spacing: 8) {
            Text(formatDate(r.date)).font(.caption.monospaced()).foregroundStyle(.secondary).frame(width: 78, alignment: .leading)
            VStack(alignment: .leading, spacing: 2) {
                Text((r.paymentMethod ?? "—").capitalized).font(.caption.weight(.semibold))
                Text((r.type ?? "—").capitalized).font(.caption2).foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(money(r.total)).font(.caption.monospacedDigit().weight(.semibold))
                Text("\(r.count) tx").font(.caption2).foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private var exportButton: some View {
        if let exportURL = exportURL {
            Link(destination: exportURL) {
                Label("Export CSV", systemImage: "square.and.arrow.down")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
        }
    }

    private var exportURL: URL? {
        let info = Bundle.main.infoDictionary
        let base = (info?["API_BASE_URL"] as? String) ?? ""
        guard !base.isEmpty else { return nil }
        let q = "?startDate=\(isoDate(startDate))&endDate=\(isoDate(endDate))"
        return URL(string: "\(base)/admin/revenue/export\(q)")
    }

    private func load() async {
        loading = true
        errorMessage = nil
        do {
            let resp = try await ThapsusSdk.shared.adminRepo()
                .revenue(startDate: isoDate(startDate), endDate: isoDate(endDate))
            await MainActor.run {
                rows = resp.revenue
                summary = resp.summary
                loading = false
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
                loading = false
            }
        }
    }

    private func isoDate(_ d: Date) -> String {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .iso8601)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: d)
    }

    private func formatDate(_ raw: String) -> String {
        // Server emits "2026-04-30T00:00:00.000Z" — strip to date.
        String(raw.prefix(10))
    }

    private func money(_ amt: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.maximumFractionDigits = 0
        return "KES \(f.string(from: NSNumber(value: amt)) ?? "0")"
    }
}
