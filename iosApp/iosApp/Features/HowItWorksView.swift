// HowItWorksView.swift
// Mirrors the webapp Home page's 4-step "How it works" workflow card stack.
// Cards alternate between airy CrystalCard (steps 1, 3) and dark
// InkFeatureCard (steps 2, 4) — same rhythm as the webapp screenshot.

import SwiftUI

struct HowItWorksView: View {
    @ScaledMetric(relativeTo: .largeTitle) private var howItWorksHeadlineSize: CGFloat = 36

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .center, spacing: 6) {
                Text("OUR WORKFLOW")
                    .font(.caption.weight(.heavy))
                    .tracking(4)
                    .foregroundStyle(Brand.orange)
                Text("HOW IT WORKS")
                    .font(.system(size: howItWorksHeadlineSize, weight: .heavy))
                    .foregroundStyle(Brand.ink)
                Capsule().fill(Brand.orange).frame(width: 60, height: 4).padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)

            stepCard(
                number: "01",
                title: "Send us a retailer link",
                body: "Found something on Amazon, ASOS, John Lewis — anywhere in the UK? Drop us the link and we'll take it from there. No UK card or address required.",
                icon: "link",
                style: .light
            )
            stepCard(
                number: "02",
                title: "We buy on your behalf",
                body: "An operator quotes you the total in GBP, you pay by card or M-Pesa, and we order it on your behalf.",
                icon: "wand.and.stars",
                style: .dark
            )
            stepCard(
                number: "03",
                title: "UK warehouse and air freight",
                body: "Your purchases land at our Stockport hub, get consolidated into the next UK→Nairobi flight, and clear customs in Kenya.",
                icon: "airplane.departure",
                style: .light
            )
            stepCard(
                number: "04",
                title: "Door-step delivery in Kenya",
                body: "A rider drops it at your address within 48 hours of touchdown. Already bought somewhere else? Pre-register the parcel and it joins the same flight.",
                icon: "scooter",
                style: .dark
            )
        }
    }

    private enum CardStyle { case light, dark }

    @ViewBuilder
    private func stepCard(
        number: String,
        title: String,
        body: String,
        icon: String,
        style: CardStyle
    ) -> some View {
        switch style {
        case .light:
            CrystalCard {
                stepBody(number: number, title: title, body: body, icon: icon, dark: false)
            }
        case .dark:
            InkFeatureCard {
                stepBody(number: number, title: title, body: body, icon: icon, dark: true)
            }
        }
    }

    @ViewBuilder
    private func stepBody(number: String, title: String, body: String, icon: String, dark: Bool) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                Image(systemName: icon)
                    .font(.title)
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(Brand.orange)
                    )
                Spacer()
            }
            Text("\(number). \(title)")
                .font(.title2.weight(.heavy))
                .foregroundStyle(dark ? Brand.cream : Brand.ink)
                .lineLimit(3)
            Text(body)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(dark ? Brand.cream.opacity(0.7) : .secondary)
        }
    }
}
