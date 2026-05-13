// RoleHomeViews.swift
// Role-specific "Account" hub screens — operator, clearing agent, admin.
// Customers have their own dedicated CustomerHomeView with the larger Phase 1
// link list.

import SwiftUI
import SafariServices
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
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(Brand.cream.opacity(0.6))
                    Spacer()
                    if let role = env.currentRole {
                        Text(roleLabel(role).uppercased())
                            .font(.caption2.weight(.heavy)).tracking(2)
                            .foregroundStyle(Brand.orange)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Capsule().fill(Brand.orange.opacity(0.18)))
                    }
                }
                Text(auth?.profile?.fullName ?? "—")
                    .font(.title2.weight(.heavy))
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
                    AccountLink("Today's summary", "calendar", AnyView(OperatorTodayView())),
                    AccountLink("Buy-for-me queue", "wand.and.stars", AnyView(OpsBuyForMeQueueView())),
                    AccountLink("Notifications", "bell.fill", AnyView(NotificationInboxView())),
                    AccountLink("Support tickets", "questionmark.bubble.fill", AnyView(TicketsListView())),
                ])
                AccountSection(title: "Account", links: [
                    AccountLink("Appearance", "circle.lefthalf.filled", AnyView(AppearanceSettingsView())),
                    AccountLink("Edit profile", "pencil.circle", AnyView(ProfileEditView())),
                ])
                MarketingLinksSection()
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
                MarketingLinksSection()
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
                MarketingLinksSection()

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
                MarketingLinksSection()
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

// MARK: - Marketing links

/// Shared "Visit thapsus.uk" section for every role's Account hub.
/// Marketing surfaces live on the website only (per parity decision); this
/// gives in-app users a one-tap path to them via SFSafariViewController so
/// they stay inside the app's process and don't lose their session.
struct MarketingLinksSection: View {
    @State private var openURL: URL?

    private struct Entry: Identifiable {
        let id = UUID()
        let title: String
        let icon: String
        let path: String
    }

    private let entries: [Entry] = [
        Entry(title: "About Thapsus", icon: "globe", path: "/"),
        Entry(title: "FAQs",          icon: "questionmark.circle", path: "/faq"),
        Entry(title: "Privacy",       icon: "hand.raised.fill", path: "/privacy"),
        Entry(title: "Terms of service", icon: "doc.text", path: "/terms"),
    ]

    private static let baseURL = URL(string: "https://thapsus.uk")!

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "On the web")
            CrystalCard {
                VStack(spacing: 0) {
                    ForEach(Array(entries.enumerated()), id: \.element.id) { idx, entry in
                        if idx > 0 { Divider().background(Brand.ink.opacity(0.08)) }
                        Button(action: { openURL = url(for: entry.path) }) {
                            HStack(spacing: 14) {
                                Image(systemName: entry.icon)
                                    .font(.headline)
                                    .foregroundStyle(Brand.orange)
                                    .frame(width: 28)
                                Text(entry.title)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Brand.ink)
                                Spacer()
                                Image(systemName: "arrow.up.right.square")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(entry.title), opens in Safari")
                    }
                }
            }
        }
        // Use the URL itself as the sheet's identity so each tap re-presents
        // even after a user dismisses and taps the same link again.
        .sheet(item: Binding(
            get: { openURL.map(IdentifiableURL.init) },
            set: { openURL = $0?.url }
        )) { wrapper in
            SafariView(url: wrapper.url)
                .ignoresSafeArea()
        }
    }

    private func url(for path: String) -> URL {
        // appendingPathComponent normalises the leading slash for us; even
        // an empty path resolves to the base URL.
        var u = Self.baseURL
        if path != "/" {
            u = u.appendingPathComponent(path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
        }
        return u
    }
}

private struct IdentifiableURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

/// Thin SwiftUI wrapper around SFSafariViewController. Stays inside the
/// app process so the user keeps their navigation state on dismiss; no
/// session loss, no app-switcher flash.
struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let cfg = SFSafariViewController.Configuration()
        cfg.entersReaderIfAvailable = false
        let controller = SFSafariViewController(url: url, configuration: cfg)
        controller.preferredControlTintColor = UIColor(Brand.orange)
        return controller
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) { }
}
