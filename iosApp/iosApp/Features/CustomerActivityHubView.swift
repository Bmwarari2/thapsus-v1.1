// CustomerActivityHubView.swift
// Bottom-tab hub surfacing the three financial-activity surfaces that
// were previously buried inside other screens (Invoices under Orders,
// Transactions under Account → Credit, Buy-for-me under TrackingView).
// Each row deep-links to the dedicated screen.

import SwiftUI
import ThapsusShared

struct CustomerActivityHubView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EyebrowPill(label: "Activity", systemImage: "tray.full.fill")
                EditorialHeader(
                    title: "My activity",
                    subtitle: "Invoices, transactions, and Buy-for-me requests in one place."
                )

                NavigationLink { TrackingView() } label: {
                    HubCard(
                        icon: "doc.text.fill",
                        iconBg: Brand.orange,
                        title: "Invoices",
                        subtitle: "Pay shipping invoices + standalone admin charges."
                    )
                }
                .buttonStyle(.plain)

                NavigationLink { TransactionsView() } label: {
                    HubCard(
                        icon: "list.bullet.rectangle",
                        iconBg: Brand.ink,
                        title: "Transactions",
                        subtitle: "Every card or M-Pesa payment + your credit activity."
                    )
                }
                .buttonStyle(.plain)

                NavigationLink { BuyForMeView() } label: {
                    HubCard(
                        icon: "wand.and.stars",
                        iconBg: Color.purple,
                        title: "Buy-for-me requests",
                        subtitle: "Concierge orders — quotes, payments, and tracking."
                    )
                }
                .buttonStyle(.plain)
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Activity")
        .glassNavigationBar()
    }
}

private struct HubCard: View {
    let icon: String
    let iconBg: Color
    let title: String
    let subtitle: String

    var body: some View {
        CrystalCard {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous).fill(iconBg)
                    )
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline).foregroundStyle(Brand.ink)
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                        .lineLimit(2).multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.tertiary)
            }
        }
    }
}
