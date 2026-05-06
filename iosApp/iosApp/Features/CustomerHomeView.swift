// CustomerHomeView.swift
// Liquid-glass redesign of the customer Account hub.
// Profile card, referral-credit hero, link list cards, sign-out.
// Adds an "Appearance" entry that links to AppearanceSettingsView.

import SwiftUI
import ThapsusShared

struct CustomerHomeView: View {
    @Environment(AppEnvironment.self) private var env

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                CutoffBannerView()

                profileCard

                creditHero

                linkCard(rows: [
                    Row(title: "Warehouse address",
                        subtitle: warehouseSubtitle,
                        systemImage: "building.2.fill",
                        destination: AnyView(WarehouseAddressView())),
                    Row(title: "Notifications",
                        subtitle: "Updates on your shipments",
                        systemImage: "bell.fill",
                        destination: AnyView(NotificationInboxView())),
                    Row(title: "Prohibited items",
                        subtitle: "What you can't ship",
                        systemImage: "exclamationmark.shield.fill",
                        destination: AnyView(ProhibitedSearchView())),
                    Row(title: "Refer friends",
                        subtitle: "Earn KES 600 per referral",
                        systemImage: "gift.fill",
                        destination: AnyView(ReferralView())),
                ])

                linkCard(rows: [
                    Row(title: "New order",
                        subtitle: "Pre-register a parcel",
                        systemImage: "shippingbox.fill",
                        destination: AnyView(NewOrderView())),
                    Row(title: "Buy for me",
                        subtitle: "Concierge purchase",
                        systemImage: "wand.and.stars",
                        destination: AnyView(BuyForMeView())),
                ])

                linkCard(rows: [
                    Row(title: "Appearance",
                        subtitle: "Light, dark, or system",
                        systemImage: "circle.lefthalf.filled",
                        destination: AnyView(AppearanceSettingsView())),
                    Row(title: "Edit profile",
                        subtitle: "Name, phone, address",
                        systemImage: "person.crop.circle.fill",
                        destination: AnyView(ProfileEditView())),
                    Row(title: "Support",
                        subtitle: "Tickets and help",
                        systemImage: "questionmark.bubble.fill",
                        destination: AnyView(TicketsListView())),
                    Row(title: "Data rights (GDPR)",
                        subtitle: "Export or delete data",
                        systemImage: "lock.shield.fill",
                        destination: AnyView(DsarView())),
                ])

                WhatsAppSupportButton()
                    .padding(.top, 8)

                Button(action: env.signOut) { Text("Sign out") }
                    .buttonStyle(InkButtonStyle(solid: true))
                    .padding(.top, 6)
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 100)
        }
        .scrollContentBackground(.hidden)
        .background(LiquidGlassBackground())
        .navigationTitle("Account")
        .glassNavigationBar()
        .overlay(alignment: .top) { NotificationBannerView() }
        .npsAutoPrompt()
    }

    // MARK: - Profile

    private var profileCard: some View {
        let auth = env.session as? AuthSessionAuthenticated
        let initial = String((auth?.profile?.fullName ?? "?").prefix(1)).uppercased()
        return GlassPanel(corner: LG.Radius.xl, padding: 18) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(LG.accentGradient)
                    Text(initial)
                        .font(.heading(22, weight: .heavy))
                        .foregroundStyle(.white)
                }
                .frame(width: 56, height: 56)
                .shadow(color: LG.accent2.opacity(0.40), radius: 12, x: 0, y: 6)

                VStack(alignment: .leading, spacing: 2) {
                    Text(auth?.profile?.fullName ?? "Welcome")
                        .font(.body(17, weight: .bold))
                        .foregroundStyle(LG.fg)
                    if let email = auth?.email, !email.isEmpty {
                        Text(email)
                            .font(.body(13, weight: .medium))
                            .foregroundStyle(LG.fg3)
                    }
                }

                Spacer(minLength: 8)

                NavigationLink {
                    ProfileEditView()
                } label: {
                    Text("Edit")
                        .font(.body(13, weight: .semibold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            Capsule().fill(.ultraThinMaterial)
                        )
                        .overlay(
                            Capsule().fill(LG.glassBgStrong).blendMode(.plusLighter)
                        )
                        .overlay(
                            Capsule().strokeBorder(LG.glassBorder, lineWidth: 1)
                        )
                        .foregroundStyle(LG.fg)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var warehouseSubtitle: String {
        let auth = env.session as? AuthSessionAuthenticated
        let id = auth?.profile?.warehouseId
        return (id?.isEmpty == false ? id! : "Stockport") + " · UK warehouse"
    }

    // MARK: - Credit hero

    private var creditHero: some View {
        GlassPanel(corner: LG.Radius.xl, padding: 18, tint: LG.accentSoft) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 4) {
                    LGEyebrow(text: "Referral credit", tone: .accent)
                    Text("KES —")
                        .font(.mono(26, weight: .bold))
                        .foregroundStyle(LG.fg)
                    Text("Auto-applies on next payment")
                        .font(.body(12, weight: .medium))
                        .foregroundStyle(LG.fg3)
                }
                Spacer()
                NavigationLink {
                    ReferralView()
                } label: {
                    Text("Invite")
                        .font(.body(13, weight: .semibold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(Capsule().fill(.ultraThinMaterial))
                        .overlay(Capsule().fill(LG.glassBgStrong).blendMode(.plusLighter))
                        .overlay(Capsule().strokeBorder(LG.glassBorder, lineWidth: 1))
                        .foregroundStyle(LG.fg)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Link list

    private struct Row {
        let title: String
        let subtitle: String
        let systemImage: String
        let destination: AnyView
    }

    private func linkCard(rows: [Row]) -> some View {
        GlassPanel(corner: LG.Radius.xl, padding: 4) {
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.offset) { idx, row in
                    NavigationLink(destination: row.destination) {
                        HStack(spacing: 14) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 11, style: .continuous)
                                    .fill(LG.glassBgStrong)
                                Image(systemName: row.systemImage)
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(LG.accent2)
                            }
                            .frame(width: 36, height: 36)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(row.title)
                                    .font(.body(14.5, weight: .bold))
                                    .foregroundStyle(LG.fg)
                                Text(row.subtitle)
                                    .font(.body(12.5, weight: .medium))
                                    .foregroundStyle(LG.fg3)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(LG.fgMute)
                        }
                        .padding(12)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if idx < rows.count - 1 {
                        Rectangle()
                            .fill(LG.line)
                            .frame(height: 1)
                            .padding(.horizontal, 14)
                    }
                }
            }
        }
    }
}
