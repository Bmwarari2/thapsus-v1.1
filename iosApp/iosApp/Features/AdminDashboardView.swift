// AdminDashboardView.swift
// Top-level admin overview: stats, AML queue, recent users. Mirrors the
// webapp's AdminDashboard.jsx but flattened for phone consumption.

import SwiftUI
import ThapsusShared

struct AdminDashboardView: View {
    @State private var vm: AdminDashboardViewModel? = nil
    @State private var observer: StateFlowObserver<AdminDashboardViewModelUiState>? = nil
    @State private var actionObs: StateFlowObserver<AdminDashboardViewModelActionState>? = nil
    @State private var showTestEmail: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Admin", systemImage: "lock.shield.fill")
                EditorialHeader(title: "Admin console",
                                subtitle: "Stats, risk queue, and user management.")
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Admin")
        .glassNavigationBar()
        .task { bootstrap() }
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as AdminDashboardViewModelUiStateLoaded:
            revenueCard(loaded.revenue.summary)
            statsRow(loaded.stats)
            bfmQueueCard
            createBfmCard
            issueInvoiceCard
            emailConfigCard(loaded.emailConfig)
            actionBanner
            SectionHeader(title: "AML queue", subtitle: loaded.flags.isEmpty ? "No open flags." : "Open risk reviews.")
            ForEach(loaded.flags, id: \.id) { flag in
                amlRow(flag)
            }
            SectionHeader(title: "Recent users")
            ForEach(loaded.users, id: \.id) { user in
                userRow(user)
            }
        case is AdminDashboardViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        case let err as AdminDashboardViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case let done as AdminDashboardViewModelActionStateDone:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Sent", message: done.message)
        case let err as AdminDashboardViewModelActionStateError:
            ErrorBanner(title: "Email failed", message: err.message)
        case is AdminDashboardViewModelActionStateInFlight:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    private func emailConfigCard(_ cfg: EmailConfigResponse) -> some View {
        let tint: Color = cfg.configured ? .green : .red
        let title = cfg.configured ? "Email service ready" : "Email service not configured"
        return CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: cfg.configured ? "envelope.badge.fill" : "exclamationmark.triangle.fill")
                        .foregroundStyle(tint)
                    Text(title)
                        .font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    Button("Test send") { showTestEmail = true }
                        .buttonStyle(.bordered)
                        .tint(Brand.orange)
                        .disabled(!cfg.configured)
                }

                envRow("GMAIL_CLIENT_ID",     cfg.hasClientId,     Int(cfg.clientIdLength),     cfg.clientIdPreview)
                envRow("GMAIL_CLIENT_SECRET", cfg.hasClientSecret, Int(cfg.clientSecretLength), nil)
                envRow("GMAIL_REFRESH_TOKEN", cfg.hasRefreshToken, Int(cfg.refreshTokenLength), nil)

                if let sender = cfg.senderEmail {
                    Text("Sender: \(sender)")
                        .font(.caption2).foregroundStyle(.secondary)
                }
                Text("Service uptime: \(cfg.processUptimeSeconds)s. Vars are read live from process.env on every request — if Railway shows them set but `len` is 0 here, redeploy the service so the new env is loaded.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .sheet(isPresented: $showTestEmail) {
            TestEmailSheet(initial: cfg.senderEmail ?? "") { addr in
                vm?.sendTestEmail(to: addr)
                showTestEmail = false
            }
        }
    }

    private func envRow(_ name: String, _ present: Bool, _ length: Int, _ preview: String?) -> some View {
        HStack(spacing: 8) {
            Image(systemName: present ? "checkmark.circle.fill" : "xmark.circle.fill")
                .font(.caption)
                .foregroundStyle(present ? .green : .red)
            Text(name).font(.caption.monospaced()).foregroundStyle(.secondary)
            Spacer()
            if present {
                let label = (preview.flatMap { $0.isEmpty ? nil : $0 }).map { "len \(length) · \($0)" } ?? "len \(length)"
                Text(label).font(.caption2.monospaced()).foregroundStyle(.tertiary)
            } else {
                Text("missing").font(.caption2.weight(.heavy)).foregroundStyle(.red)
            }
        }
    }

    private func statsRow(_ s: AdminStatsResponse) -> some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                statTile("Users", "\(s.totalUsers)", color: .blue)
                statTile("Orders", "\(s.totalOrders)", color: Brand.orange)
            }
            HStack(spacing: 12) {
                statTile("Active", "\(s.activeOrders)", color: .green)
                statTile("Delivered", "\(s.deliveredOrders)", color: .purple)
            }
        }
    }

    /// Money Thapsus has actually earned. Reads /api/admin/revenue-summary
    /// rather than the legacy `revenue.totalRevenue` block, which sourced
    /// from the now-empty `transactions` table (wallet rip, mig 028).
    private func revenueCard(_ rev: AdminRevenueSummary) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Revenue")
                        .font(.caption.weight(.heavy))
                        .tracking(2).foregroundStyle(.secondary)
                    Spacer()
                    Text(formatGbp(rev.totalRevenueGbp))
                        .font(.title.weight(.heavy))
                        .foregroundStyle(Brand.ink)
                }
                HStack(alignment: .firstTextBaseline) {
                    revenueLine(label: "Buy-for-me 10%",
                                amount: rev.buyForMeCommissionGbp,
                                count: Int(rev.buyForMePaidCount))
                    Spacer()
                    revenueLine(label: "Invoices",
                                amount: rev.invoiceRevenueGbp,
                                count: Int(rev.invoicePaidCount))
                }
            }
        }
    }

    private func revenueLine(label: String, amount: Double, count: Int) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label.uppercased())
                .font(.system(size: 9, weight: .heavy)).tracking(2)
                .foregroundStyle(.secondary)
            Text(formatGbp(amount))
                .font(.headline).foregroundStyle(Brand.ink)
            Text("\(count) paid")
                .font(.caption2).foregroundStyle(.tertiary)
        }
    }

    private func formatGbp(_ amount: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = "GBP"
        f.maximumFractionDigits = amount >= 1000 ? 0 : 2
        return f.string(from: NSNumber(value: amount)) ?? "£\(amount)"
    }

    private func statTile(_ label: String, _ value: String, color: Color) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                Text(label.uppercased())
                    .font(.system(size: 9, weight: .heavy))
                    .tracking(2)
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.title2.weight(.heavy))
                    .foregroundStyle(color)
            }
        }
    }

    /// Concierge queue lives in the account hub today; admins want a
    /// one-tap surface from the console to triage quotes.
    @ViewBuilder
    private var bfmQueueCard: some View {
        NavigationLink {
            OpsBuyForMeQueueView()
        } label: {
            CrystalCard {
                HStack(spacing: 12) {
                    Image(systemName: "wand.and.stars")
                        .font(.title3)
                        .foregroundStyle(Brand.cream)
                        .frame(width: 40, height: 40)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Brand.orange)
                        )
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Buy-for-me queue")
                            .font(.headline).foregroundStyle(Brand.ink)
                        Text("Quote concierge requests from customers")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(.tertiary)
                }
            }
        }
        .buttonStyle(.plain)
    }

    /// Create a BFM on behalf of a customer who placed the order
    /// off-platform (WhatsApp, phone, in person). Optionally pre-quotes
    /// in the same form so the customer can pay straight away.
    @ViewBuilder
    private var createBfmCard: some View {
        NavigationLink {
            AdminCreateBuyForMeView()
        } label: {
            CrystalCard {
                HStack(spacing: 12) {
                    Image(systemName: "wand.and.stars")
                        .font(.title3)
                        .foregroundStyle(Brand.cream)
                        .frame(width: 40, height: 40)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.purple)
                        )
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Create Buy-for-me")
                            .font(.headline).foregroundStyle(Brand.ink)
                        Text("On behalf of a WhatsApp / phone order")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(.tertiary)
                }
            }
        }
        .buttonStyle(.plain)
    }

    /// PR 5: standalone invoice creation — admin issues a one-off charge
    /// (customs penalty, storage fee, off-platform settlement). Customer
    /// pays via the regular target_kind='consolidation' payment flow.
    @ViewBuilder
    private var issueInvoiceCard: some View {
        NavigationLink {
            AdminIssueInvoiceView()
        } label: {
            CrystalCard {
                HStack(spacing: 12) {
                    Image(systemName: "doc.text.fill")
                        .font(.title3)
                        .foregroundStyle(Brand.cream)
                        .frame(width: 40, height: 40)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Brand.ink)
                        )
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Issue invoice")
                            .font(.headline).foregroundStyle(Brand.ink)
                        Text("One-off charge — customer pays via card or M-Pesa")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(.tertiary)
                }
            }
        }
        .buttonStyle(.plain)
    }

    private func amlRow(_ flag: AmlFlagDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(flag.userName ?? flag.userEmail ?? flag.userId)
                        .font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    Text(flag.status.uppercased())
                        .font(.system(size: 9, weight: .heavy)).tracking(2)
                        .foregroundStyle(.orange)
                }
                Text(flag.reason).font(.subheadline).foregroundStyle(.secondary)
                HStack(spacing: 10) {
                    Button("Clear") { vm?.resolveFlag(id: flag.id, status: "cleared") }
                        .buttonStyle(.bordered).tint(.green)
                    Button("Escalate") { vm?.resolveFlag(id: flag.id, status: "escalated") }
                        .buttonStyle(.bordered).tint(.red)
                }
            }
        }
    }

    private func userRow(_ user: AdminUserDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(user.name).font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    Text(user.role.uppercased())
                        .font(.system(size: 9, weight: .heavy)).tracking(2)
                        .foregroundStyle(Brand.orange)
                }
                Text(user.email).font(.caption).foregroundStyle(.secondary)
                if !user.isActive {
                    Text("INACTIVE")
                        .font(.system(size: 9, weight: .heavy)).tracking(2)
                        .foregroundStyle(.red)
                }
            }
        }
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.adminDashboardViewModel()
        vm = model
        model.load()
        observer = StateFlowObserver(initial: model.state.value) { model.state }
        actionObs = StateFlowObserver(initial: model.action.value) { model.action }
    }
}

private struct TestEmailSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State var address: String
    let onSubmit: (String) -> Void

    init(initial: String, onSubmit: @escaping (String) -> Void) {
        _address = State(initialValue: initial)
        self.onSubmit = onSubmit
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Send a test email") {
                    TextField("Recipient", text: $address)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                }
                Section {
                    Text("Sends a one-line test message via the live Gmail credentials. Useful for verifying deliverability before provisioning real accounts.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Test email")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Send") { onSubmit(address) }
                        .disabled(!address.contains("@"))
                }
            }
        }
    }
}
