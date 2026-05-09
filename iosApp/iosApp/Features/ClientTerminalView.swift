// ClientTerminalView.swift
// "Address" tab — the editorial Stockport warehouse-address card the customer
// posts their parcels to. Dark high-contrast InkCard with copy-to-clipboard +
// the customer's per-account routing reference (so the warehouse knows whose
// package landed when the courier drops it).

import SwiftUI
import ThapsusShared
import UIKit

struct ClientTerminalView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var copied: Bool = false

    @ScaledMetric(relativeTo: .largeTitle) private var routingRefSize: CGFloat = 28

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EditorialHeader(
                    eyebrow: "Send to Stockport",
                    title: "Your UK\nwarehouse",
                    subtitle: "Use this address for every retailer order. We'll receive, weigh, and fly it to Nairobi."
                )

                addressTerminal

                instructionsCard

                Color.clear.frame(height: 24)
            }
            .padding(20)
        }
        .navigationTitle("Address")
        .glassNavigationBar()
        .scrollContentBackground(.hidden)
        .appBackground()
    }

    private var routingRef: String {
        guard let id = env.currentUserID else { return "THP-CUST" }
        let suffix = id.replacingOccurrences(of: "-", with: "").uppercased().prefix(6)
        return "THP-\(suffix)"
    }

    private var fullAddress: String {
        """
        \(routingRef)
        c/o Thapsus Cargo
        Unit 4, Pendlebury Industrial Estate
        Bridge Lane, Stockport
        SK4 5PT
        United Kingdom
        """
    }

    @ViewBuilder
    private var addressTerminal: some View {
        InkCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Stockport hub")
                        .font(.eyebrow)
                        .foregroundStyle(Brand.orange)
                    Spacer()
                    HStack(spacing: 6) {
                        Circle().fill(.green).frame(width: 8, height: 8)
                        Text("LIVE").font(.caption2.weight(.semibold))
                    }
                }

                Text("Your routing reference")
                    .font(.caption)
                    .foregroundStyle(Brand.cream.opacity(0.6))
                Text(routingRef)
                    .font(.system(size: routingRefSize, weight: .bold, design: .monospaced))
                    .foregroundStyle(Brand.cream)

                Divider().background(Brand.cream.opacity(0.18))

                Text("Ship to")
                    .font(.caption)
                    .foregroundStyle(Brand.cream.opacity(0.6))

                VStack(alignment: .leading, spacing: 4) {
                    addressLine(routingRef, weight: .semibold)
                    addressLine("c/o Thapsus Cargo")
                    addressLine("Unit 4, Pendlebury Industrial Estate")
                    addressLine("Bridge Lane, Stockport")
                    addressLine("SK4 5PT")
                    addressLine("United Kingdom")
                }

                Button(action: copy) {
                    Label(copied ? "Copied" : "Copy address", systemImage: copied ? "checkmark" : "doc.on.doc")
                }
                .buttonStyle(OrangeButtonStyle())
                .padding(.top, 6)
            }
        }
    }

    @ViewBuilder
    private func addressLine(_ text: String, weight: Font.Weight = .regular) -> some View {
        Text(text)
            .font(.body.weight(weight))
            .foregroundStyle(Brand.cream)
    }

    @ViewBuilder
    private var instructionsCard: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("How it works")
                    .font(.headline)
                    .foregroundStyle(Brand.ink)

                step(number: 1, title: "Paste at checkout", body: "Use the address above on Amazon, ASOS, eBay or any UK retailer.")
                step(number: 2, title: "Pre-register the parcel", body: "Tap the + on Home so we know what's coming.")
                step(number: 3, title: "We weigh & manifest", body: "Photographed and on the next Wednesday flight to JKIA.")
            }
        }
    }

    @ViewBuilder
    private func step(number: Int, title: String, body: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(number)")
                .font(.headline.monospacedDigit())
                .foregroundStyle(Brand.cream)
                .frame(width: 30, height: 30)
                .background(Circle().fill(Brand.ink))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                Text(body).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    private func copy() {
        UIPasteboard.general.string = fullAddress
        withAnimation(.easeInOut(duration: 0.15)) { copied = true }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            withAnimation(.easeInOut(duration: 0.2)) { copied = false }
        }
    }
}
