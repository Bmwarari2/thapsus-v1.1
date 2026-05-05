// CustomerHomeView.swift
// "More" hub on the Profile tab — links every Phase 1 customer feature into
// a single discoverable list, matching the webapp's nav drawer.

import SwiftUI
import ThapsusShared

struct CustomerHomeView: View {
    @Environment(AppEnvironment.self) private var env

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Account", systemImage: "person.crop.circle")
                EditorialHeader(title: "Account",
                                subtitle: env.currentRole.map { _ in "Manage profile, orders and support." } ?? "Sign in to access your account.")

                CutoffBannerView()

                profileCard

                section(title: "Send & ship", links: [
                    Link("New order", "shippingbox.fill", AnyView(NewOrderView())),
                    Link("Buy for me", "wand.and.stars", AnyView(BuyForMeView())),
                    Link("Warehouse", "mappin.and.ellipse", AnyView(WarehouseAddressView())),
                    Link("Prohibited items", "exclamationmark.shield", AnyView(ProhibitedSearchView())),
                ])

                WhatsAppSupportButton()

                section(title: "Account", links: [
                    Link("Notifications", "bell.fill", AnyView(NotificationInboxView())),
                    Link("Support", "questionmark.bubble.fill", AnyView(TicketsListView())),
                    Link("Referrals", "person.2.fill", AnyView(ReferralView())),
                    Link("Edit profile", "pencil.circle", AnyView(ProfileEditView())),
                    Link("Data rights", "lock.shield", AnyView(DsarView())),
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
        .overlay(alignment: .top) {
            NotificationBannerView()
        }
        .npsAutoPrompt()
    }

    private var profileCard: some View {
        let auth = env.session as? AuthSessionAuthenticated
        return InkFeatureCard {
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

    private struct Link {
        let title: String
        let icon: String
        let destination: AnyView

        init(_ title: String, _ icon: String, _ destination: AnyView) {
            self.title = title
            self.icon = icon
            self.destination = destination
        }
    }

    private func section(title: String, links: [Link]) -> some View {
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
