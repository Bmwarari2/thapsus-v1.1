// LiquidGlass.swift
// Thapsus v1.1 design system — liquid-glass redesign.
//
// Tokens (light/dark), typography, primitives. Existing screens keep using
// SoftCard / InkCard / OrangeButtonStyle etc. — those live in
// GlassDesignSystem.swift and now resolve through these tokens, so the whole
// app picks up the refresh without rewriting every callsite.
//
// Design source: thapsus-ui-redesign/project/Thapsus Mobile Redesign.html.

import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Theme manager

enum ThapsusTheme: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var label: String {
        switch self {
        case .system: return "System"
        case .light: return "Light"
        case .dark: return "Dark"
        }
    }
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

/// Persisted user appearance choice. Bound at the root via
/// `.preferredColorScheme(...)` so every screen tracks the toggle.
@MainActor
@Observable
final class AppearanceSettings {
    private static let defaultsKey = "thapsus.appearance.theme"

    var theme: ThapsusTheme {
        didSet {
            UserDefaults.standard.set(theme.rawValue, forKey: Self.defaultsKey)
        }
    }

    init() {
        let raw = UserDefaults.standard.string(forKey: Self.defaultsKey) ?? ThapsusTheme.system.rawValue
        self.theme = ThapsusTheme(rawValue: raw) ?? .system
    }
}

// MARK: - Color tokens
//
// OKLCH values from the design CSS, converted to closest sRGB hex equivalents.
// Tokens auto-flip in dark mode via UITraitCollection.

enum LG {
    // Background gradient stops
    static let bgA      = Color.dynamic(light: 0xF8F4EC, dark: 0x252220)   // warm beige / charcoal
    static let bgB      = Color.dynamic(light: 0xE8F5F4, dark: 0x1B2128)   // mint blue / cool slate
    static let bgC      = Color.dynamic(light: 0xEFF5EC, dark: 0x23252D)   // green tint / mid slate

    // Ambient blob colors (top-right warm, bottom-left cool)
    static let blobWarm = Color.dynamic(light: 0xF8C68A, dark: 0xC76A2E, lightAlpha: 0.55, darkAlpha: 0.35)
    static let blobCool = Color.dynamic(light: 0x9DCAD8, dark: 0x4F7589, lightAlpha: 0.55, darkAlpha: 0.45)

    // Foreground / text — bumped contrast so dim labels stay readable on the
    // glass cards. Light tones run darker; dark tones run brighter.
    static let fg       = Color.dynamic(light: 0x1B1816, dark: 0xF6F4F0)
    static let fg2      = Color.dynamic(light: 0x3F3A33, dark: 0xDED9D2)
    static let fg3      = Color.dynamic(light: 0x665E52, dark: 0xB6B0A6)
    static let fgMute   = Color.dynamic(light: 0x8C8273, dark: 0x8E867B)

    // Lines / dividers
    static let line     = Color.dynamic(light: 0x2A2722, dark: 0xFFFFFF, lightAlpha: 0.10, darkAlpha: 0.10)
    static let lineStrong = Color.dynamic(light: 0x2A2722, dark: 0xFFFFFF, lightAlpha: 0.18, darkAlpha: 0.18)

    // Glass surfaces — tuned so text on top of `.ultraThinMaterial` stays
    // legible. Light fills sit at low opacity (the material itself supplies
    // the frosted look); dark fills are warmer and more opaque so dim text
    // doesn't disappear.
    static let glassBg       = Color.dynamic(light: 0xFFFFFF, dark: 0x3A3530, lightAlpha: 0.18, darkAlpha: 0.55)
    static let glassBgStrong = Color.dynamic(light: 0xFFFFFF, dark: 0x44403A, lightAlpha: 0.40, darkAlpha: 0.70)
    static let glassBorder   = Color.dynamic(light: 0xFFFFFF, dark: 0xFFFFFF, lightAlpha: 0.30, darkAlpha: 0.10)

    // Accent (logo orange)
    static let accent   = Color(hex: 0xE58D40) // oklch(0.72 0.18 52)
    static let accent2  = Color(hex: 0xD85D2A) // oklch(0.66 0.20 42)
    static let accentSoft = Color.dynamic(light: 0xE58D40, dark: 0xE58D40, lightAlpha: 0.14, darkAlpha: 0.22)

    static let accentGradient = LinearGradient(
        colors: [Color(hex: 0xE69245), Color(hex: 0xD05122)],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    // Status colors
    static let ok    = Color(hex: 0x4FB892)
    static let warn  = Color(hex: 0xD9A53C)
    static let err   = Color(hex: 0xC74A3A)
    static let info  = Color(hex: 0x6FA3CB)

    // Radii
    enum Radius {
        static let xs: CGFloat = 8
        static let sm: CGFloat = 12
        static let md: CGFloat = 18
        static let lg: CGFloat = 24
        static let xl: CGFloat = 32
        static let xxl: CGFloat = 40
    }

    // Motion
    static let animation: Animation = .easeInOut(duration: 0.28)
}

// MARK: - Color hex helpers

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >> 8)  & 0xFF) / 255
        let b = Double(hex & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }

    /// Light/dark dynamic color, flips automatically with the trait collection.
    static func dynamic(
        light: UInt32,
        dark: UInt32,
        lightAlpha: Double = 1,
        darkAlpha: Double = 1
    ) -> Color {
        Color(UIColor { trait in
            let useDark = trait.userInterfaceStyle == .dark
            let hex = useDark ? dark : light
            let a   = useDark ? darkAlpha : lightAlpha
            let r = CGFloat((hex >> 16) & 0xFF) / 255
            let g = CGFloat((hex >> 8)  & 0xFF) / 255
            let b = CGFloat(hex & 0xFF) / 255
            return UIColor(red: r, green: g, blue: b, alpha: CGFloat(a))
        })
    }
}

// MARK: - Typography
//
// Fonts: prefer Manrope / JetBrains Mono if registered, fall back to the
// system rounded / monospaced. The design renders identical pixel weights
// either way at body sizes.

extension Font {
    static func display(_ size: CGFloat = 30, weight: Font.Weight = .heavy) -> Font {
        custom("Manrope", size: size, fallback: .system(size: size, weight: weight, design: .default))
    }
    static func heading(_ size: CGFloat = 22, weight: Font.Weight = .bold) -> Font {
        custom("Manrope", size: size, fallback: .system(size: size, weight: weight, design: .default))
    }
    static func body(_ size: CGFloat = 15, weight: Font.Weight = .regular) -> Font {
        custom("Manrope", size: size, fallback: .system(size: size, weight: weight, design: .default))
    }
    static func mono(_ size: CGFloat = 14, weight: Font.Weight = .semibold) -> Font {
        custom("JetBrains Mono", size: size, fallback: .system(size: size, weight: weight, design: .monospaced))
    }
    static func eyebrowLG(_ size: CGFloat = 11) -> Font {
        custom("Manrope", size: size, fallback: .system(size: size, weight: .heavy, design: .default))
    }

    private static func custom(_ name: String, size: CGFloat, fallback: Font) -> Font {
        if UIFont(name: name, size: size) != nil {
            return Font.custom(name, size: size)
        }
        return fallback
    }
}

// MARK: - Background

/// Liquid-glass app background. Soft radial gradient between two corners,
/// plus two ambient color blobs. Used everywhere via `.appBackground()`
/// (legacy AppBackground now wraps this).
struct LiquidGlassBackground: View {
    var body: some View {
        ZStack {
            // Base wash — keeps text readable when blobs aren't enough.
            LG.bgC

            // Top-left to bottom-right radial wash (beige → mint).
            GeometryReader { geo in
                let w = geo.size.width
                let h = geo.size.height
                Canvas { ctx, _ in
                    let topLeft = CGRect(x: -w * 0.2, y: -h * 0.2, width: w * 1.4, height: h * 1.0)
                    ctx.fill(
                        Path(ellipseIn: topLeft),
                        with: .radialGradient(
                            Gradient(colors: [LG.bgA, .clear]),
                            center: CGPoint(x: 0, y: 0),
                            startRadius: 0, endRadius: max(w, h)
                        )
                    )
                    let botRight = CGRect(x: -w * 0.2, y: 0, width: w * 1.4, height: h * 1.4)
                    ctx.fill(
                        Path(ellipseIn: botRight),
                        with: .radialGradient(
                            Gradient(colors: [LG.bgB, .clear]),
                            center: CGPoint(x: w, y: h),
                            startRadius: 0, endRadius: max(w, h)
                        )
                    )
                }

                // Ambient warm blob top-right
                Circle()
                    .fill(LG.blobWarm)
                    .frame(width: 320, height: 320)
                    .blur(radius: 60)
                    .position(x: w + 30, y: -40)

                // Ambient cool blob bottom-left
                Circle()
                    .fill(LG.blobCool)
                    .frame(width: 360, height: 360)
                    .blur(radius: 70)
                    .position(x: -60, y: h + 80)
            }
            .allowsHitTesting(false)
        }
        .ignoresSafeArea()
    }
}

// MARK: - Glass primitive

/// Liquid-glass card: blurred translucent fill + edge highlight + soft shadow.
struct GlassPanel<Content: View>: View {
    var corner: CGFloat = LG.Radius.xl
    var padding: CGFloat = 16
    var strong: Bool = false
    var tint: Color? = nil
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                ZStack {
                    RoundedRectangle(cornerRadius: corner, style: .continuous)
                        .fill(.ultraThinMaterial)
                    RoundedRectangle(cornerRadius: corner, style: .continuous)
                        .fill(strong ? LG.glassBgStrong : LG.glassBg)
                    if let tint {
                        RoundedRectangle(cornerRadius: corner, style: .continuous)
                            .fill(tint)
                    }
                }
                .allowsHitTesting(false)
            )
            .overlay(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .strokeBorder(LG.glassBorder, lineWidth: 1)
                    .allowsHitTesting(false)
            )
            .shadow(color: .black.opacity(0.08), radius: 18, x: 0, y: 10)
            .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }
}

// MARK: - Buttons

struct LGPrimaryButtonStyle: ButtonStyle {
    var compact: Bool = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body(15, weight: .bold))
            .foregroundStyle(Color.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, compact ? 11 : 14)
            .padding(.horizontal, 18)
            .background(
                Capsule(style: .continuous)
                    .fill(LG.accentGradient)
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(Color.white.opacity(0.35), lineWidth: 1)
                    .blendMode(.plusLighter)
            )
            .shadow(color: LG.accent2.opacity(0.45), radius: 18, x: 0, y: 8)
            .shadow(color: LG.accent2.opacity(0.25), radius: 4, x: 0, y: 2)
            .opacity(configuration.isPressed ? 0.92 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

struct LGGlassButtonStyle: ButtonStyle {
    var compact: Bool = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body(15, weight: .semibold))
            .foregroundStyle(LG.fg)
            .frame(maxWidth: .infinity)
            .padding(.vertical, compact ? 11 : 14)
            .padding(.horizontal, 18)
            .background(
                ZStack {
                    Capsule(style: .continuous).fill(.ultraThinMaterial)
                    Capsule(style: .continuous).fill(LG.glassBgStrong)
                }
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(LG.glassBorder, lineWidth: 1)
            )
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

// MARK: - Pills / chips

enum PillTone {
    case neutral, accent, ok, warn, info, err
}

struct LGPill: View {
    let text: String
    var systemImage: String? = nil
    var tone: PillTone = .neutral

    var body: some View {
        HStack(spacing: 6) {
            if let systemImage {
                Image(systemName: systemImage).font(.caption2.weight(.bold))
            }
            Text(text)
                .font(.body(12.5, weight: .semibold))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 5)
        .foregroundStyle(textColor)
        .background(
            Capsule(style: .continuous)
                .fill(bgColor)
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(borderColor, lineWidth: 1)
        )
    }

    private var bgColor: Color {
        switch tone {
        case .neutral: return LG.glassBgStrong
        case .accent:  return LG.accentSoft
        case .ok:      return LG.ok.opacity(0.18)
        case .warn:    return LG.warn.opacity(0.20)
        case .info:    return LG.info.opacity(0.18)
        case .err:     return LG.err.opacity(0.18)
        }
    }
    private var borderColor: Color {
        switch tone {
        case .neutral: return LG.glassBorder
        case .accent:  return LG.accent.opacity(0.30)
        case .ok:      return LG.ok.opacity(0.30)
        case .warn:    return LG.warn.opacity(0.30)
        case .info:    return LG.info.opacity(0.30)
        case .err:     return LG.err.opacity(0.30)
        }
    }
    private var textColor: Color {
        switch tone {
        case .neutral: return LG.fg2
        case .accent:  return LG.accent2
        case .ok:      return LG.ok
        case .warn:    return LG.warn
        case .info:    return LG.info
        case .err:     return LG.err
        }
    }
}

// MARK: - Eyebrow / section header

struct LGEyebrow: View {
    let text: String
    var tone: PillTone = .neutral
    var body: some View {
        Text(text.uppercased())
            .font(.body(11, weight: .heavy))
            .tracking(1.4)
            .foregroundStyle(tone == .accent ? LG.accent2 : LG.fg3)
    }
}

// MARK: - Floating glass dock (replaces system tab bar visually)

struct LGDockItem: Identifiable {
    let id: String
    let label: String
    let systemImage: String
}

struct LGDock: View {
    let items: [LGDockItem]
    @Binding var active: String

    var body: some View {
        HStack(spacing: 4) {
            ForEach(items) { item in
                Button {
                    withAnimation(LG.animation) { active = item.id }
                } label: {
                    Image(systemName: item.systemImage)
                        .font(.system(size: 19, weight: .semibold))
                        .frame(width: 48, height: 48)
                        .foregroundStyle(active == item.id ? Color.white : LG.fg3)
                        .background(
                            Capsule(style: .continuous)
                                .fill(active == item.id ? AnyShapeStyle(LG.accentGradient) : AnyShapeStyle(Color.clear))
                        )
                        .shadow(color: active == item.id ? LG.accent2.opacity(0.45) : .clear, radius: 12, x: 0, y: 4)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(item.label))
            }
        }
        .padding(8)
        .background(
            ZStack {
                Capsule(style: .continuous).fill(.ultraThinMaterial)
                Capsule(style: .continuous).fill(LG.glassBgStrong)
            }
            .allowsHitTesting(false)
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(LG.glassBorder, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.18), radius: 28, x: 0, y: 18)
        .shadow(color: .black.opacity(0.06), radius: 6, x: 0, y: 2)
    }
}

// MARK: - Segmented selector

struct LGSegment<T: Hashable>: View {
    @Binding var selection: T
    let options: [(value: T, label: String)]

    var body: some View {
        HStack(spacing: 2) {
            ForEach(options, id: \.value) { opt in
                Button {
                    withAnimation(LG.animation) { selection = opt.value }
                } label: {
                    Text(opt.label)
                        .font(.body(13, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 14)
                        .foregroundStyle(selection == opt.value ? LG.fg : LG.fg3)
                        .background(
                            Capsule(style: .continuous)
                                .fill(selection == opt.value ? LG.glassBgStrong : Color.clear)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(
            Capsule(style: .continuous)
                .fill(.ultraThinMaterial)
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(LG.glassBorder, lineWidth: 1)
        )
    }
}

// MARK: - Glass toggle

struct LGToggle: View {
    @Binding var isOn: Bool

    var body: some View {
        Button {
            withAnimation(LG.animation) { isOn.toggle() }
        } label: {
            ZStack(alignment: isOn ? .trailing : .leading) {
                Capsule()
                    .fill(isOn ? AnyShapeStyle(LG.accentGradient) : AnyShapeStyle(LG.lineStrong))
                    .frame(width: 46, height: 28)
                Circle()
                    .fill(Color.white)
                    .frame(width: 22, height: 22)
                    .shadow(color: .black.opacity(0.18), radius: 2, x: 0, y: 1)
                    .padding(3)
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Form field

struct LGTextField: View {
    let label: String?
    let placeholder: String
    @Binding var text: String
    var keyboard: UIKeyboardType = .default
    var capitalization: TextInputAutocapitalization = .sentences
    var isSecure: Bool = false
    var leadingIcon: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let label {
                Text(label.uppercased())
                    .font(.body(11, weight: .bold))
                    .tracking(0.6)
                    .foregroundStyle(LG.fg3)
            }
            HStack(spacing: 10) {
                if let leadingIcon {
                    Image(systemName: leadingIcon)
                        .foregroundStyle(LG.fgMute)
                }
                if isSecure {
                    SecureField(placeholder, text: $text)
                } else {
                    TextField(placeholder, text: $text)
                        .keyboardType(keyboard)
                        .textInputAutocapitalization(capitalization)
                }
            }
            .font(.body(15, weight: .medium))
            .foregroundStyle(LG.fg)
            .padding(.vertical, 13)
            .padding(.horizontal, 16)
            .background(
                ZStack {
                    RoundedRectangle(cornerRadius: LG.Radius.lg, style: .continuous)
                        .fill(.ultraThinMaterial)
                    RoundedRectangle(cornerRadius: LG.Radius.lg, style: .continuous)
                        .fill(LG.glassBg)
                }
                .allowsHitTesting(false)
            )
            .overlay(
                RoundedRectangle(cornerRadius: LG.Radius.lg, style: .continuous)
                    .strokeBorder(LG.glassBorder, lineWidth: 1)
            )
        }
    }
}

// MARK: - Stat tiles & action cards

struct LGStatTile: View {
    let label: String
    let value: String
    var accent: Bool = false
    var systemImage: String? = nil

    var body: some View {
        GlassPanel(padding: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text(value)
                    .font(.mono(22, weight: .bold))
                    .foregroundStyle(accent ? LG.accent2 : LG.fg)
                Text(label.uppercased())
                    .font(.body(10.5, weight: .bold))
                    .tracking(0.6)
                    .foregroundStyle(LG.fg3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct LGActionCard: View {
    let title: String
    let subtitle: String
    let systemImage: String
    var tone: PillTone = .neutral
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                ZStack {
                    Circle()
                        .fill(tone == .accent ? Color.white.opacity(0.18) : LG.glassBgStrong)
                    Image(systemName: systemImage)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(tone == .accent ? Color.white : LG.accent2)
                }
                .frame(width: 36, height: 36)
                .padding(.bottom, 16)

                Text(title)
                    .font(.body(15, weight: .bold))
                    .foregroundStyle(tone == .accent ? Color.white : LG.fg)

                Text(subtitle)
                    .font(.body(12.5, weight: .medium))
                    .foregroundStyle(tone == .accent ? Color.white.opacity(0.85) : LG.fg3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(
                Group {
                    if tone == .accent {
                        RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                            .fill(LG.accentGradient)
                    } else {
                        ZStack {
                            RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                                .fill(.ultraThinMaterial)
                            RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                                .fill(LG.glassBg)
                        }
                    }
                }
            )
            .overlay(
                RoundedRectangle(cornerRadius: LG.Radius.xl, style: .continuous)
                    .strokeBorder(tone == .accent ? Color.white.opacity(0.25) : LG.glassBorder, lineWidth: 1)
            )
            .shadow(color: tone == .accent ? LG.accent2.opacity(0.30) : .black.opacity(0.10), radius: 18, x: 0, y: 10)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Progress bar & timeline dot

struct LGProgressBar: View {
    var value: Double // 0…1
    var height: CGFloat = 6
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(LG.line)
                Capsule()
                    .fill(LG.accentGradient)
                    .frame(width: max(0, min(1, value)) * geo.size.width)
            }
        }
        .frame(height: height)
    }
}

enum TimelineState { case pending, active, done }

struct LGTimelineDot: View {
    let state: TimelineState
    var body: some View {
        Circle()
            .fill(fillStyle)
            .frame(width: 12, height: 12)
            .overlay(
                Circle()
                    .strokeBorder(state == .pending ? LG.lineStrong : Color.clear, lineWidth: 2)
            )
            .overlay(
                Circle()
                    .strokeBorder(state == .active ? LG.accentSoft : Color.clear, lineWidth: 4)
                    .scaleEffect(1.6)
            )
    }
    private var fillStyle: AnyShapeStyle {
        switch state {
        case .pending: return AnyShapeStyle(LG.glassBgStrong)
        case .active:  return AnyShapeStyle(LinearGradient(colors: [LG.accent, LG.accent2], startPoint: .topLeading, endPoint: .bottomTrailing))
        case .done:    return AnyShapeStyle(LG.accent)
        }
    }
}

// MARK: - Glass logo block (used in sign-in hero)

struct LGLogoBlock: View {
    var size: CGFloat = 72

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.30, style: .continuous)
                .fill(LG.accentGradient)
            Image(systemName: "shippingbox.fill")
                .font(.system(size: size * 0.50, weight: .bold))
                .foregroundStyle(Color.white)
        }
        .frame(width: size, height: size)
        .shadow(color: LG.accent2.opacity(0.40), radius: 18, x: 0, y: 10)
    }
}

// MARK: - View modifiers

extension View {
    /// Drop-in liquid-glass background. Replaces the legacy
    /// `.appBackground()` for screens that opt into the new chrome.
    func liquidGlassBackground() -> some View {
        background(LiquidGlassBackground())
    }
}
