// PaymentConfirmationOverlay.swift
// Full-screen confirmation shown after a successful payment action.
// Replaces the small inline `CalloutBanner(title: "Done", …)` that the
// pay invoice flow used to surface — customers were missing it on
// the cluttered Pay screen and re-paying because they couldn't tell
// the first attempt had landed.
//
// Three variants drive distinct customer mental models:
//
//   .received  — money has actually moved (Stripe completion or
//                credit fully-covered settlement). Green checkmark,
//                .success haptic. Headline: "Payment received".
//
//   .submitted — customer has reported a manual transfer; admin
//                review is still pending (M-Pesa SMS submitted).
//                Amber clock-with-check icon, .warning haptic. Copy
//                makes the "still pending" state explicit so the
//                customer doesn't think the parcel has cleared yet.
//
// Entrance is staged so the eye lands on the icon first:
//   1. Backdrop fades in   (0.25s ease-out)
//   2. Tinted ring scales in behind the icon (0.35s ease-out)
//   3. Icon pops in        (0.4s ease-out, +0.1s)
//   4. Headline + amount + button fade in (0.3s ease-out, +0.4s)
//
// All curves are ease-out — no springs. The previous spring/0.5–0.6
// damping fractions read as a "bouncy" overshoot on the M-Pesa
// confirmation in particular, which clashes with the
// "your money is in motion" tone the screen needs.
//
// Caller is responsible for dismissing the parent sheet on the
// `onDismiss` callback — the overlay itself just covers the screen.

import SwiftUI

struct PaymentConfirmationOverlay: View {
    enum Variant {
        case received
        case submitted

        var icon: String {
            switch self {
            case .received: "checkmark.circle.fill"
            case .submitted: "clock.badge.checkmark.fill"
            }
        }

        var tint: Color {
            switch self {
            case .received: .green
            case .submitted: Brand.orange
            }
        }

        var hapticType: UINotificationFeedbackGenerator.FeedbackType {
            switch self {
            case .received: .success
            case .submitted: .warning
            }
        }

        var title: String {
            switch self {
            case .received: "Payment received"
            case .submitted: "Confirmation submitted"
            }
        }
    }

    let variant: Variant
    let subtitle: String
    let amountKesGross: Int64?
    let onDismiss: () -> Void

    @State private var ringScale: CGFloat = 0
    @State private var iconScale: CGFloat = 0
    @State private var iconOpacity: Double = 0
    @State private var contentOpacity: Double = 0
    @State private var backdropOpacity: Double = 0

    // Dynamic-Type-aware sizes for the celebratory checkmark + amount.
    // @ScaledMetric scales with the user's preferred text size while keeping
    // the visual emphasis ratio. Bound to .largeTitle / .title so they grow
    // proportionally with the rest of the overlay copy.
    @ScaledMetric(relativeTo: .largeTitle) private var iconSize: CGFloat = 76
    @ScaledMetric(relativeTo: .title) private var amountSize: CGFloat = 36

    var body: some View {
        ZStack {
            Rectangle()
                .fill(.black.opacity(0.45))
                .ignoresSafeArea()
                .opacity(backdropOpacity)
                .onTapGesture { /* swallow taps so the user must explicitly tap Done */ }

            VStack(spacing: 0) {
                Spacer(minLength: 40)

                SoftCard {
                    VStack(spacing: 22) {
                        ZStack {
                            Circle()
                                .fill(variant.tint.opacity(0.15))
                                .frame(width: 132, height: 132)
                                .scaleEffect(ringScale)

                            Image(systemName: variant.icon)
                                .font(.system(size: iconSize, weight: .bold))
                                .foregroundStyle(variant.tint)
                                .scaleEffect(iconScale)
                                .opacity(iconOpacity)
                        }
                        .frame(height: 144)
                        .padding(.top, 6)

                        VStack(spacing: 10) {
                            Text(variant.title)
                                .font(.title2.weight(.bold))
                                .foregroundStyle(Brand.ink)
                                .multilineTextAlignment(.center)

                            Text(subtitle)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 4)

                            if let amount = amountKesGross {
                                Text("KES \(formatKes(amount))")
                                    .font(.system(size: amountSize, weight: .black, design: .rounded))
                                    .foregroundStyle(Brand.ink)
                                    .padding(.top, 4)
                                    .contentTransition(.numericText())
                            }
                        }
                        .opacity(contentOpacity)

                        Button {
                            onDismiss()
                        } label: {
                            Label("Done", systemImage: "checkmark")
                                .frame(maxWidth: .infinity)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(GlassSheenButtonStyle(fill: variant.tint, foreground: .white))
                        .opacity(contentOpacity)
                    }
                    .padding(20)
                }
                .padding(.horizontal, 22)

                Spacer(minLength: 20)
            }
        }
        .onAppear { runEntranceAnimation() }
    }

    private func runEntranceAnimation() {
        UINotificationFeedbackGenerator().notificationOccurred(variant.hapticType)
        withAnimation(.easeOut(duration: 0.25)) {
            backdropOpacity = 1
        }
        withAnimation(.easeOut(duration: 0.35)) {
            ringScale = 1
        }
        withAnimation(.easeOut(duration: 0.4).delay(0.1)) {
            iconScale = 1
            iconOpacity = 1
        }
        withAnimation(.easeOut(duration: 0.3).delay(0.4)) {
            contentOpacity = 1
        }
    }

    private func formatKes(_ value: Int64) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.maximumFractionDigits = 0
        return f.string(from: NSNumber(value: value)) ?? "\(value)"
    }
}
