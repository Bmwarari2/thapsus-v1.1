// HowItWorksView.swift
// Mirrors the webapp Home page's 4-step "How it works" workflow card stack.
// Cards alternate between airy CrystalCard (steps 1, 3) and dark
// InkFeatureCard (steps 2, 4) — same rhythm as the webapp screenshot.

import SwiftUI

struct HowItWorksView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .center, spacing: 6) {
                Text("OUR WORKFLOW")
                    .font(.system(size: 12, weight: .heavy))
                    .tracking(4)
                    .foregroundStyle(Brand.orange)
                Text("HOW IT WORKS")
                    .font(.system(size: 36, weight: .heavy))
                    .foregroundStyle(Brand.ink)
                Capsule().fill(Brand.orange).frame(width: 60, height: 4).padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)

            stepCard(
                number: "01",
                title: "Shop from your favorite retailers",
                body: "Shop any brand in the UK or China. Use our warehouse address as yours.",
                icon: "bag.fill",
                style: .light
            )
            stepCard(
                number: "02",
                title: "Ship to our warehouse",
                body: "We handle the heavy lifting. Your package is safely received and cataloged.",
                icon: "shippingbox.fill",
                style: .dark
            )
            stepCard(
                number: "03",
                title: "We consolidate",
                body: "We combine your parcels into the next weekly UK→Nairobi flight to slash freight cost.",
                icon: "tray.full.fill",
                style: .light
            )
            stepCard(
                number: "04",
                title: "Doorstep delivery",
                body: "Customs cleared in Nairobi, then a rider hands it to you within 48 hours of arrival.",
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
                .font(.system(size: 22, weight: .heavy))
                .foregroundStyle(dark ? Brand.cream : Brand.ink)
                .lineLimit(3)
            Text(body)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(dark ? Brand.cream.opacity(0.7) : .secondary)
        }
    }
}
