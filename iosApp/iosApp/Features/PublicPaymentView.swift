// PublicPaymentView.swift
// Universal-link entry: thapsus://pay/<orderId> opens this screen so anyone
// (logged in or not) can pay a duty/quote invoice. The actual payment goes
// through the Express /api/payment/:orderId routes; we hand off to Safari.

import SwiftUI

struct PublicPaymentView: View {
    let orderId: String

    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Pay invoice", systemImage: "creditcard")
                EditorialHeader(title: "Order \(orderId.prefix(8))…",
                                subtitle: "Open the secure payment page to settle this invoice.")

                CrystalCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("You can pay by M-Pesa, card, or wallet balance.")
                            .font(.subheadline).foregroundStyle(.secondary)
                        Button("Open payment page") {
                            if let u = paymentURL { openURL(u) }
                        }
                        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                        .disabled(paymentURL == nil)
                    }
                }

                if paymentURL == nil {
                    CalloutBanner(
                        icon: "exclamationmark.triangle",
                        title: "Payment temporarily unavailable",
                        message: "Payment page is temporarily unavailable. Please contact support."
                    )
                }

                CalloutBanner(
                    icon: "lock.shield",
                    title: "Secure handoff",
                    message: "We never collect card details inside the app — payment runs on thapsus.uk over HTTPS."
                )
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Pay invoice")
        .glassNavigationBar()
    }

    private var paymentURL: URL? {
        let info = Bundle.main.infoDictionary
        let api = (info?["API_BASE_URL"] as? String) ?? ""
        // Strip trailing /api so the link points to the SPA-hosted page.
        let host = api
            .replacingOccurrences(of: "/api", with: "")
            .replacingOccurrences(of: " ", with: "")
        guard !host.isEmpty else { return nil }
        return URL(string: "\(host)/pay/\(orderId)")
    }
}
