// WebappPrimitives.swift
// Distinctive visual primitives mirrored from the Swiftcargo-main webapp:
//   • MorphingBlob — animated liquid background bloom
//   • CrystalCard  — the airy white/40 backdrop-blur-2xl glass bento
//   • InkFeatureCard — dark editorial card with an orange blur accent corner
//   • EyebrowPill  — eyebrow status chip with optional pulse dot
//   • GradientBorderCard — rainbow-edge container for primary action rails
//   • GlassSheenButtonStyle — sliding sheen overlay on dark CTAs
//
// Liquid Glass discipline carries over from GlassDesignSystem.swift: glass
// stays on chrome, content cards use material/ink. The webapp's rotation +
// hover transforms are replaced with subtle press-state tilts that feel
// native on iOS without looking like a parallax demo.

import SwiftUI
import ThapsusShared

// MARK: - Morphing background blob

/// Animated coloured bloom used as a backdrop accent. Mirrors the webapp's
/// `LiquidBlob` morph keyframes (~15s loop). Two-three of these layered behind
/// `.appBackground()` give every screen the editorial liquid feel.
struct MorphingBlob: View {
    let color: Color
    var diameter: CGFloat = 360
    var blur: CGFloat = 90
    var duration: Double = 15

    @State private var phase: CGFloat = 0

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: diameter, height: diameter)
            .blur(radius: blur)
            .opacity(0.55)
            .scaleEffect(0.95 + 0.10 * phase)
            .offset(x: 30 * sin(phase * .pi * 2),
                    y: -40 * cos(phase * .pi * 2))
            .blendMode(.multiply)
            .allowsHitTesting(false)
            .onAppear {
                withAnimation(.easeInOut(duration: duration).repeatForever(autoreverses: true)) {
                    phase = 1
                }
            }
    }
}

/// Pre-arranged blob backdrop matching the webapp's hero pages (orange + blue).
struct LiquidBackdrop: View {
    var body: some View {
        ZStack {
            AppBackground()
            GeometryReader { geo in
                MorphingBlob(color: Color.blue.opacity(0.35), diameter: geo.size.width * 0.95)
                    .position(x: geo.size.width * 0.0, y: geo.size.height * 0.10)
                MorphingBlob(color: Brand.orange.opacity(0.45), diameter: geo.size.width * 0.85, duration: 18)
                    .position(x: geo.size.width * 1.0, y: geo.size.height * 0.85)
            }
            // Soft frost on top so content stays legible.
            Color.white.opacity(0.18)
                .background(.ultraThinMaterial)
                .ignoresSafeArea()
        }
        .ignoresSafeArea()
    }
}

extension View {
    /// Page-level liquid backdrop. Use instead of `.appBackground()` for the
    /// editorial customer screens.
    func liquidBackdrop() -> some View {
        background(LiquidBackdrop())
    }
}

// MARK: - Crystal bento card

/// Airy white-glass bento card. Equivalent to the webapp's
/// `bg-white/40 backdrop-blur-2xl rounded-[2.5rem] border border-white/40`.
/// Use it as the primary surface for marketing-style customer screens.
struct CrystalCard<Content: View>: View {
    var corner: CGFloat = 28
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(.ultraThinMaterial)
            )
            .overlay(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [.white.opacity(0.35), .clear],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .blendMode(.plusLighter)
                    .allowsHitTesting(false)
            )
            .overlay(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .stroke(.white.opacity(0.55), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.06), radius: 24, x: 0, y: 10)
    }
}

// MARK: - Process steps explainer

/// Numbered step list inside a CrystalCard. Used at the top of the
/// New order + Buy-for-me sheets so the customer doesn't have to guess
/// what happens after they hit Submit. Each step is `(badge, title,
/// detail)` — badge is typically a numeric "1"…"4" but anything short
/// works (e.g. an emoji or initial). Keeps the call-site terse so the
/// sheets don't grow inline copy.
struct ProcessStepsCard: View {
    let title: String
    let steps: [(badge: String, title: String, detail: String)]

    var body: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 14) {
                Text(title)
                    .font(.headline).foregroundStyle(Brand.ink)
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(steps.enumerated()), id: \.offset) { _, step in
                        HStack(alignment: .top, spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(Brand.orange.opacity(0.18))
                                    .frame(width: 28, height: 28)
                                Text(step.badge)
                                    .font(.caption.weight(.heavy))
                                    .foregroundStyle(Brand.orange)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text(step.title)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Brand.ink)
                                Text(step.detail)
                                    .font(.caption)
                                    .foregroundStyle(Brand.ink.opacity(0.65))
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Ink feature card (dark editorial)

/// Dark slate card with an orange blur halo in one corner — mirrors the
/// webapp warehouse-address terminal and cost-estimate sidebar.
struct InkFeatureCard<Content: View>: View {
    var accent: Color = Brand.orange
    var corner: CGFloat = 28
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
            .foregroundStyle(Brand.cream)
            .background(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(Brand.ink)
            )
            .overlay(alignment: .topTrailing) {
                Circle()
                    .fill(accent.opacity(0.35))
                    .frame(width: 220, height: 220)
                    .blur(radius: 80)
                    .offset(x: 50, y: -50)
                    .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
                    .allowsHitTesting(false)
            }
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
            .shadow(color: .black.opacity(0.18), radius: 24, x: 0, y: 14)
    }
}

// MARK: - Eyebrow pill

/// Small frosted capsule with an optional pulsing status dot. The webapp uses
/// this above every page header ("Client Terminal", "Dispatch Order", …).
struct EyebrowPill: View {
    let label: String
    var systemImage: String? = nil
    var dotColor: Color? = .green

    @State private var pulse: Bool = false

    var body: some View {
        HStack(spacing: 8) {
            if let dotColor {
                Circle()
                    .fill(dotColor)
                    .frame(width: 6, height: 6)
                    .scaleEffect(pulse ? 1.4 : 1.0)
                    .opacity(pulse ? 0.5 : 1.0)
                    .onAppear {
                        withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) {
                            pulse = true
                        }
                    }
            }
            if let systemImage {
                Image(systemName: systemImage).font(.caption2.weight(.semibold))
            }
            Text(label.uppercased())
                .font(.system(size: 10, weight: .heavy, design: .default))
                .tracking(2.5)
                .foregroundStyle(Brand.ink.opacity(0.7))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(
            Capsule().fill(.ultraThinMaterial)
        )
        .overlay(
            Capsule().stroke(.white.opacity(0.6), lineWidth: 1)
        )
    }
}

// MARK: - Gradient border container

/// Rainbow-edge container. The webapp uses this around the dashboard quick-
/// action rail. We treat it as a one-off accent — never on every card.
struct GradientBorderCard<Content: View>: View {
    var corner: CGFloat = 28
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .padding(6)
            .background(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(.ultraThinMaterial)
            )
            .padding(2)
            .background(
                RoundedRectangle(cornerRadius: corner + 2, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [
                                Brand.orange,
                                Brand.orange.opacity(0.6),
                                Color.blue.opacity(0.6)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
            )
            .shadow(color: Brand.orange.opacity(0.25), radius: 22, x: 0, y: 12)
    }
}

// MARK: - Glass sheen button

/// Editorial CTA with a sliding sheen overlay. The webapp animates this every
/// 4s — we trigger it on press for a tactile micro-interaction (avoids the
/// "always-spinning" look on a phone screen).
struct GlassSheenButtonStyle: ButtonStyle {
    var fill: Color = Brand.ink
    var foreground: Color = Brand.cream

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 13, weight: .heavy))
            .tracking(2)
            .textCase(.uppercase)
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 18)
            .background(
                ZStack {
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(fill)
                    if configuration.isPressed {
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [.clear, .white.opacity(0.35), .clear],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .blendMode(.plusLighter)
                    }
                }
            )
            .opacity(configuration.isPressed ? 0.92 : 1)
            .scaleEffect(configuration.isPressed ? 0.985 : 1)
            .shadow(color: .black.opacity(0.18), radius: 20, x: 0, y: 12)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

// MARK: - Brand logo

/// 180×180 logo from the webapp's `client/public/logo.png`. Sized through the
/// `size` parameter; default keeps the aspect square.
struct BrandLogo: View {
    var size: CGFloat = 64

    var body: some View {
        Image("Logo")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityLabel("Thapsus Cargo")
    }
}

/// Reusable WhatsApp deep link to Thapsus Cargo support.
/// Opens the user's WhatsApp app pre-filled with a message.
///
/// The number is server-driven (audit S2-3) — pulled by `AppConfigRepository`
/// at app boot and cached in a hot StateFlow on the shared SDK. Falls back
/// to the bundled default if the fetch failed (e.g. first launch offline).
enum SupportContact {
    static var whatsappNumber: String {
        ThapsusSdk.shared.appConfig().config.value.supportWhatsapp
    }
    static var whatsappURL: URL? {
        let body = "Hi%20Thapsus%20Cargo%2C%20I%20need%20help%20with%20"
        return URL(string: "https://wa.me/\(whatsappNumber)?text=\(body)")
    }
}

struct WhatsAppSupportButton: View {
    var label: String = "Chat support on WhatsApp"
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button(action: open) {
            HStack(spacing: 10) {
                Image(systemName: "bubble.left.and.text.bubble.right.fill")
                    .foregroundStyle(.white)
                Text(label)
                    .font(.system(size: 14, weight: .heavy))
                    .tracking(1.5)
                    .foregroundStyle(.white)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color(red: 0.15, green: 0.69, blue: 0.30)) // WhatsApp green
            )
        }
        .buttonStyle(.plain)
    }

    private func open() {
        if let url = SupportContact.whatsappURL { openURL(url) }
    }
}

/// Brand wordmark — logo on the left, "Thapsus" + "Cargo" on the right.
/// Mirrors the webapp's LiquidGlassNav header. Use everywhere the brand
/// identifies itself (splash, sign-in hero, every authenticated screen's
/// top chrome via `.brandToolbar()`).
struct BrandWordmark: View {
    enum Size {
        case small, medium, large

        var logo: CGFloat {
            switch self {
            case .small: return 28
            case .medium: return 44
            case .large: return 96
            }
        }

        var text: Font {
            switch self {
            case .small: return .system(size: 16, weight: .heavy)
            case .medium: return .system(size: 22, weight: .heavy)
            case .large: return .system(size: 36, weight: .heavy)
            }
        }
    }

    var size: Size = .medium

    var body: some View {
        HStack(spacing: 10) {
            BrandLogo(size: size.logo)
            HStack(spacing: 4) {
                Text("Thapsus").foregroundStyle(Brand.ink)
                Text("Cargo").foregroundStyle(Brand.orange)
            }
            .font(size.text)
            .tracking(-0.5)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Thapsus Cargo")
    }
}

extension View {
    /// Centred brand wordmark in the navigation bar's principal slot.
    /// Replaces the explicit `.navigationTitle(...)` text with the wordmark.
    /// Pair with `.glassNavigationBar()` so the OS bar still gets Liquid Glass.
    func brandToolbar() -> some View {
        self.toolbar {
            ToolbarItem(placement: .principal) {
                BrandWordmark(size: .small)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Big-stat tile (numeric tile with accent badge)

/// Mirrors the webapp's "Crystal Bento" stat tiles. Used in the customer
/// dashboard for active-orders / wallet / referral-earnings.
struct BigStatTile: View {
    let eyebrow: String
    let value: String
    var unit: String? = nil
    var systemImage: String
    var accent: Color = Brand.orange
    var trailingNote: String? = nil

    var body: some View {
        CrystalCard {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(eyebrow.uppercased())
                        .font(.system(size: 10, weight: .heavy))
                        .tracking(2)
                        .foregroundStyle(Brand.ink.opacity(0.45))

                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        if let unit {
                            Text(unit)
                                .font(.system(size: 16, weight: .heavy))
                                .foregroundStyle(accent)
                        }
                        Text(value)
                            .font(.system(size: 36, weight: .heavy))
                            .foregroundStyle(Brand.ink)
                            .contentTransition(.numericText())
                    }

                    if let trailingNote {
                        Text(trailingNote.uppercased())
                            .font(.system(size: 9, weight: .heavy))
                            .tracking(2)
                            .foregroundStyle(accent)
                    }
                }
                Spacer(minLength: 12)
                Image(systemName: systemImage)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(accent)
                    .frame(width: 48, height: 48)
                    .background(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(accent.opacity(0.14))
                    )
            }
        }
    }
}
