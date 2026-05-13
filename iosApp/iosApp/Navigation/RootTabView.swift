// RootTabView.swift
// Role-aware tab bar. The cream→peach gradient lives behind every tab so the
// Liquid Glass tab bar has something to refract.

import SwiftUI
import ThapsusShared

struct RootTabView: View {
    let role: UserRole

    var body: some View {
        TabView {
            switch role {
            case .customer:
                Tab("Home", systemImage: "shippingbox.fill") {
                    NavigationStack { CustomerDashboardView() }
                }
                // Primary surface in the Buy-for-me-first pivot. Customers
                // send us a retailer link, we buy, ship, deliver. The old
                // "Orders" tab pointed at TrackingView (parcel tracking,
                // NOT order creation) and is now demoted into the Activity
                // hub alongside Pre-register, so the secondary path stays
                // one tap away.
                Tab("Shop", systemImage: "wand.and.stars") {
                    NavigationStack { BuyForMeView() }
                }
                // Hub for invoices / transactions / parcel tracking /
                // pre-register — every secondary financial + shipment
                // surface lives here. See CustomerActivityHubView.swift.
                Tab("Activity", systemImage: "tray.full.fill") {
                    NavigationStack { CustomerActivityHubView() }
                }
                Tab("Quote", systemImage: "scalemass.fill") {
                    NavigationStack { QuoteCalculatorView() }
                }
                Tab("Account", systemImage: "person.crop.circle") {
                    NavigationStack { CustomerHomeView() }
                }

            case .`operator`:
                // BFM Queue leads the operator workflow: a quote-first
                // concierge order is now the canonical inbound, replacing
                // the receive-first parcel intake mindset. The old "Today"
                // tab's summary stats are reachable via Account → Today.
                Tab("BFM", systemImage: "wand.and.stars") {
                    NavigationStack { OpsBuyForMeQueueView() }
                }
                Tab("Receive", systemImage: "printer.fill") {
                    NavigationStack { OperatorReceiveView() }
                }
                Tab("Consols", systemImage: "tray.full.fill") {
                    NavigationStack { ConsolidationListView() }
                }
                Tab("Dispatch", systemImage: "scooter") {
                    NavigationStack { DispatchView() }
                }
                Tab("Account", systemImage: "person.crop.circle") {
                    NavigationStack { OperatorHomeView() }
                }

            case .clearingAgent:
                Tab("Customs", systemImage: "doc.text.magnifyingglass") {
                    NavigationStack { CustomsListView() }
                }
                Tab("Invoices", systemImage: "doc.text.fill") {
                    NavigationStack { AgentInvoicesView() }
                }
                Tab("Account", systemImage: "person.crop.circle") {
                    NavigationStack { AgentHomeView() }
                }

            case .rider:
                Tab("Today", systemImage: "map.fill") {
                    NavigationStack { RiderRunView() }
                }
                Tab("Outbox", systemImage: "tray.and.arrow.up") {
                    NavigationStack { OutboxView() }
                }
                Tab("Account", systemImage: "person.crop.circle") {
                    NavigationStack { RiderHomeView() }
                }

            case .admin:
                Tab("Console", systemImage: "chart.bar.doc.horizontal") {
                    NavigationStack { AdminDashboardView() }
                }
                Tab("KPI", systemImage: "chart.line.uptrend.xyaxis") {
                    NavigationStack { KPIDashboardView() }
                }
                Tab("Customer", systemImage: "doc.text.fill") {
                    NavigationStack { AdminCustomerConsolidationsView() }
                }
                Tab("Shipping", systemImage: "tray.full.fill") {
                    NavigationStack { ConsolidationListView() }
                }
                Tab("Account", systemImage: "person.crop.circle") {
                    NavigationStack { AdminHomeView() }
                }

            @unknown default:
                Tab("Home", systemImage: "house") {
                    NavigationStack { CustomerDashboardView() }
                }
            }
        }
        .glassTabBar()
    }
}

private struct ProfileView: View {
    @Environment(AppEnvironment.self) private var env

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                EditorialHeader(eyebrow: "Account", title: "Profile")

                SoftCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Signed in").font(.headline).foregroundStyle(Brand.ink)
                        Text(env.currentUserID ?? "—").font(.callout).foregroundStyle(.secondary)
                        if let role = env.currentRole {
                            GlassChip(title: roleLabel(role), systemImage: "person.text.rectangle", tint: Brand.orange.opacity(0.4))
                        }
                    }
                }
                Button("Sign out", role: .destructive) { env.signOut() }
                    .buttonStyle(InkButtonStyle())
                    .padding(.top, 24)
            }
            .padding(20)
        }
        .navigationTitle("Profile")
        .glassNavigationBar()
        .scrollContentBackground(.hidden)
        .appBackground()
    }

    private func roleLabel(_ r: UserRole) -> String {
        switch r {
        case .customer: return "Customer"
        case .`operator`: return "Operator"
        case .clearingAgent: return "Clearing agent"
        case .rider: return "Rider"
        case .admin: return "Admin"
        @unknown default: return "—"
        }
    }
}
