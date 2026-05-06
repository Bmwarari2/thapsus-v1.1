// RoleHomeViews.swift
// Role-specific "Account" hub screens — operator, clearing agent, admin.
// Customers have their own dedicated CustomerHomeView with the larger Phase 1
// link list.

import SwiftUI
import ThapsusShared

private struct AccountLink {
    let title: String
    let icon: String
    let destination: AnyView
    init(_ title: String, _ icon: String, _ destination: AnyView) {
        self.title = title; self.icon = icon; self.destination = destination
    }
}

private struct AccountSection: View {
    let title: String
    let links: [AccountLink]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: title)
            CrystalCard {
                VStack(spacing: 0) {
                    ForEach(Array(links.enumerated()), id: \.offset) { idx, link in
                        if idx > 0 { Divider().background(Brand.ink.opacity(0.08)) }
                        NavigationLink(destination: link.destination) {
                            HStack(spacing: 14) {
                                Image(systemName: link.icon)
                                    .font(.headline)
                                    .foregroundStyle(Brand.orange)
                                    .frame(width: 28)
                                Text(link.title)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Brand.ink)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

private struct AccountHeader: View {
    let env: AppEnvironment

    var body: some View {
        let auth = env.session as? AuthSessionAuthenticated
        InkFeatureCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("SIGNED IN")
                        .font(.system(size: 10, weight: .heavy)).tracking(2)
                        .foregroundStyle(Brand.cream.opacity(0.6))
                    Spacer()
                    if let role = env.currentRole {
                        Text(roleLabel(role).uppercased())
                            .font(.system(size: 10, weight: .heavy)).tracking(2)
                            .foregroundStyle(Brand.orange)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Capsule().fill(Brand.orange.opacity(0.18)))
                    }
                }
                Text(auth?.profile?.fullName ?? "—")
                    .font(.system(size: 22, weight: .heavy))
                    .foregroundStyle(Brand.cream)
                if let email = auth?.email, !email.isEmpty {
                    Text(email)
                        .font(.subheadline)
                        .foregroundStyle(Brand.cream.opacity(0.75))
                }
                if let warehouseId = auth?.profile?.warehouseId, !warehouseId.isEmpty {
                    Text(warehouseId)
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(Brand.orange)
                }
            }
        }
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

struct OperatorHomeView: View {
    @Environment(AppEnvironment.self) private var env

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Operations", systemImage: "person.crop.circle")
                EditorialHeader(title: "Account",
                                subtitle: "Profile, support and operations tools.")
                AccountHeader(env: env)
                AccountSection(title: "Tools", links: [
                    AccountLink("Buy-for-me queue", "wand.and.stars", AnyView(OpsBuyForMeQueueView())),
                    AccountLink("Notifications", "bell.fill", AnyView(NotificationInboxView())),
                    AccountLink("Support tickets", "questionmark.bubble.fill", AnyView(TicketsListView())),
                ])
                AccountSection(title: "Account", links: [
                    AccountLink("Appearance", "circle.lefthalf.filled", AnyView(AppearanceSettingsView())),
                    AccountLink("Edit profile", "pencil.circle", AnyView(ProfileEditView())),
                ])
                Button("Sign out", role: .destructive) { env.signOut() }
                    .buttonStyle(GlassSheenButtonStyle(fill: .red, foreground: .white))
                    .padding(.top, 12)
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Account")
        .glassNavigationBar()
    }
}

struct AgentHomeView: View {
    @Environment(AppEnvironment.self) private var env

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Clearing agent", systemImage: "person.crop.circle")
                EditorialHeader(title: "Account",
                                subtitle: "Customs, invoices and your profile.")
                AccountHeader(env: env)
                AccountSection(title: "Work", links: [
                    AccountLink("Customs", "doc.text.magnifyingglass", AnyView(CustomsListView())),
                    AccountLink("My invoices", "doc.text.fill", AnyView(AgentInvoicesView())),
                    AccountLink("Notifications", "bell.fill", AnyView(NotificationInboxView())),
                    AccountLink("Support tickets", "questionmark.bubble.fill", AnyView(TicketsListView())),
                ])
                AccountSection(title: "Account", links: [
                    AccountLink("Appearance", "circle.lefthalf.filled", AnyView(AppearanceSettingsView())),
                    AccountLink("Edit profile", "pencil.circle", AnyView(ProfileEditView())),
                ])
                Button("Sign out", role: .destructive) { env.signOut() }
                    .buttonStyle(GlassSheenButtonStyle(fill: .red, foreground: .white))
                    .padding(.top, 12)
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Account")
        .glassNavigationBar()
    }
}

struct AdminHomeView: View {
    @Environment(AppEnvironment.self) private var env

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Admin", systemImage: "lock.shield.fill")
                EditorialHeader(title: "Console",
                                subtitle: "Admin overview and ops settings.")
                AccountHeader(env: env)

                AccountSection(title: "Console", links: [
                    AccountLink("Users", "person.2.fill", AnyView(AdminUsersView())),
                    AccountLink("Orders", "shippingbox.fill", AnyView(AdminOrdersView())),
                    AccountLink("Pending payments", "creditcard.fill", AnyView(AdminPaymentsView())),
                    AccountLink("Tickets (all)", "questionmark.bubble.fill", AnyView(TicketsListView(asAdmin: true))),
                    AccountLink("Revenue", "chart.line.uptrend.xyaxis", AnyView(AdminRevenueView())),
                    AccountLink("DSAR queue", "person.crop.circle.badge.questionmark", AnyView(AdminDsarQueueView())),
                    AccountLink("Audit logs", "list.bullet.rectangle.portrait", AnyView(AdminAuditLogsView())),
                    AccountLink("Error logs", "exclamationmark.bubble", AnyView(AdminErrorLogsView())),
                    AccountLink("Buy-for-me queue", "wand.and.stars", AnyView(OpsBuyForMeQueueView())),
                    AccountLink("Ops settings", "gearshape.fill", AnyView(OpsSettingsView())),
                    AccountLink("Notifications", "bell.fill", AnyView(NotificationInboxView())),
                ])
                AccountSection(title: "Account", links: [
                    AccountLink("Appearance", "circle.lefthalf.filled", AnyView(AppearanceSettingsView())),
                    AccountLink("Edit profile", "pencil.circle", AnyView(ProfileEditView())),
                ])

                Button("Sign out", role: .destructive) { env.signOut() }
                    .buttonStyle(GlassSheenButtonStyle(fill: .red, foreground: .white))
                    .padding(.top, 12)
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Console")
        .glassNavigationBar()
    }
}

struct RiderHomeView: View {
    @Environment(AppEnvironment.self) private var env

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Rider", systemImage: "person.crop.circle")
                EditorialHeader(title: "Account",
                                subtitle: "Your delivery runs and profile.")
                AccountHeader(env: env)
                AccountSection(title: "Account", links: [
                    AccountLink("Notifications", "bell.fill", AnyView(NotificationInboxView())),
                    AccountLink("Support tickets", "questionmark.bubble.fill", AnyView(TicketsListView())),
                    AccountLink("Edit profile", "pencil.circle", AnyView(ProfileEditView())),
                ])
                Button("Sign out", role: .destructive) { env.signOut() }
                    .buttonStyle(GlassSheenButtonStyle(fill: .red, foreground: .white))
                    .padding(.top, 12)
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Account")
        .glassNavigationBar()
    }
}
