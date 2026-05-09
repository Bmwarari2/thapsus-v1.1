// NotificationInboxView.swift
// Notifications feed for the signed-in customer. Mirrors the webapp's
// NotificationBanner inbox surface, but as a full screen with mark-read.
//
// Tapping a row marks it read AND deep-links to the relevant screen when
// the type/message hints at one. The DTO carries no explicit target, so
// the routing is heuristic — same approach the banner already uses for
// icon/headline selection. Rows with no inferred destination still mark
// read but don't push.

import SwiftUI
import ThapsusShared

struct NotificationInboxView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var vm: NotificationInboxViewModel? = nil
    @State private var observer: StateFlowObserver<NotificationInboxViewModelUiState>? = nil
    @State private var route: NotificationRoute? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Inbox", systemImage: "bell.fill")
                EditorialHeader(eyebrow: nil, title: "Notifications", subtitle: "Status updates, messages and reminders.")

                content
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 40)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Notifications")
        .glassNavigationBar()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Mark all read") { vm?.markAllRead() }
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Brand.orange)
            }
        }
        .navigationDestination(item: $route) { r in
            r.destinationView
        }
        .task { bootstrap() }
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as NotificationInboxViewModelUiStateLoaded:
            if loaded.items.isEmpty {
                CrystalCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("All caught up").font(.headline).foregroundStyle(Brand.ink)
                        Text("You don't have any notifications yet.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            } else {
                ForEach(loaded.items, id: \.id) { item in
                    notificationCard(item)
                }
            }
        case is NotificationInboxViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        case let err as NotificationInboxViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        }
    }

    private func notificationCard(_ item: NotificationDto) -> some View {
        let target = NotificationRoute.from(notification: item, role: env.currentRole)
        return Button {
            if !item.isRead { vm?.markRead(id: item.id) }
            if let target { route = target }
        } label: {
            CrystalCard {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: item.isRead ? "envelope.open" : "envelope.badge.fill")
                        .font(.title3)
                        .foregroundStyle(item.isRead ? Color.secondary : Brand.orange)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.message)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Brand.ink)
                            .multilineTextAlignment(.leading)
                        HStack(spacing: 6) {
                            if let created = item.createdAt {
                                Text(created)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            if let target {
                                if item.createdAt != nil {
                                    Text("·").font(.caption2).foregroundStyle(.secondary)
                                }
                                Text("Open \(target.label)")
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(Brand.orange)
                            }
                        }
                    }
                    Spacer(minLength: 0)
                    if target != nil {
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .buttonStyle(.plain)
    }

    private func bootstrap() {
        guard vm == nil, let userId = env.currentUserID else { return }
        let model = ThapsusSdk.shared.notificationInboxViewModel(userId: userId)
        vm = model
        observer = StateFlowObserver(initial: model.state.value) {
            model.state
        }
    }
}

// MARK: - Routing

enum NotificationRoute: Hashable, Identifiable {
    // Customer
    case tracking
    case activity
    case transactions
    case quote
    case profile
    // Cross-role
    case tickets
    case ticketsAdmin
    // Staff / admin
    case opsBuyForMe
    case agentInvoices
    case customs
    case adminPayments
    case adminUsers
    case adminOrders
    case adminDsar

    var id: Self { self }

    var label: String {
        switch self {
        case .tracking: return "Orders"
        case .activity: return "Activity"
        case .transactions: return "Transactions"
        case .quote: return "Quote"
        case .profile: return "Profile"
        case .tickets, .ticketsAdmin: return "Support"
        case .opsBuyForMe: return "Buy-for-me queue"
        case .agentInvoices: return "Invoices"
        case .customs: return "Customs"
        case .adminPayments: return "Payments"
        case .adminUsers: return "Users"
        case .adminOrders: return "Orders"
        case .adminDsar: return "DSAR queue"
        }
    }

    @ViewBuilder
    var destinationView: some View {
        switch self {
        case .tracking: TrackingView()
        case .activity: CustomerActivityHubView()
        case .transactions: TransactionsView()
        case .quote: QuoteCalculatorView()
        case .profile: ProfileEditView()
        case .tickets: TicketsListView()
        case .ticketsAdmin: TicketsListView(asAdmin: true)
        case .opsBuyForMe: OpsBuyForMeQueueView()
        case .agentInvoices: AgentInvoicesView()
        case .customs: CustomsListView()
        case .adminPayments: AdminPaymentsView()
        case .adminUsers: AdminUsersView()
        case .adminOrders: AdminOrdersView()
        case .adminDsar: AdminDsarQueueView()
        }
    }

    /// Maps a notification to a destination based on `type` + message keywords.
    /// Returns nil when the notification has no obvious deep-link target —
    /// the caller should still mark it read but skip the push.
    static func from(notification n: NotificationDto, role: UserRole?) -> NotificationRoute? {
        let t = n.type.lowercased()
        let m = n.message.lowercased()

        // Tickets / support replies
        if t.contains("ticket") || m.contains("ticket") || m.contains("support reply") || m.contains("reply on your") {
            return role == .admin ? .ticketsAdmin : .tickets
        }

        // DSAR (admin-only surface)
        if t.contains("dsar") || m.contains("dsar") || m.contains("data request") {
            if role == .admin { return .adminDsar }
        }

        // Buy-for-me
        if t.contains("bfm") || t.contains("buy_for_me") || m.contains("buy-for-me") || m.contains("buy for me") {
            switch role {
            case .`operator`, .admin: return .opsBuyForMe
            default: return .activity
            }
        }

        // Customs (clearing-agent flow)
        if t.contains("customs") || m.contains("customs") || m.contains("clearance") {
            return role == .clearingAgent ? .customs : .tracking
        }

        // Money-moving: payment / wallet / transaction / refund / invoice / topup
        let moneyHit = m.contains("payment") || m.contains("wallet") ||
            m.contains("transaction") || m.contains("deposit") ||
            m.contains("topup") || m.contains("top-up") || m.contains("top up") ||
            m.contains("refund") || m.contains("invoice") || m.contains("paid") ||
            t.contains("payment") || t.contains("wallet") || t.contains("invoice")
        if moneyHit {
            switch role {
            case .admin: return .adminPayments
            case .clearingAgent: return .agentInvoices
            default:
                // Pure transaction/wallet hits go straight to Transactions;
                // invoices land on the Activity hub which fans out to invoices.
                if m.contains("invoice") { return .activity }
                return .transactions
            }
        }

        // KYC / profile / verification
        if m.contains("kyc") || m.contains("verify") || m.contains("verification") ||
            m.contains("document") || m.contains("profile") {
            return .profile
        }

        // Quote / pricing
        if m.contains("quote") || m.contains("rate card") {
            return .quote
        }

        // Parcel / shipment / consolidation status
        let parcelHit = m.contains("delivered") || m.contains("dispatched") ||
            m.contains("shipped") || m.contains("arrived") ||
            m.contains("in transit") || m.contains("warehouse") ||
            m.contains("parcel") || m.contains("package") ||
            m.contains("consolidation") || m.contains("consol") ||
            m.contains("tracking") || m.contains("status update") ||
            t.contains("shipment") || t.contains("status") || t.contains("parcel")
        if parcelHit {
            switch role {
            case .admin: return .adminOrders
            default: return .tracking
            }
        }

        // Account / signup / KYC adjacent (admin-only user mgmt)
        if role == .admin && (m.contains("user") || m.contains("signed up") || m.contains("registered")) {
            return .adminUsers
        }

        return nil
    }
}
