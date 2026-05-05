// GlassDesignSystem.swift
// Thapsus Cargo design tokens.
//
// Visual language matches the webapp: a soft cream→peach gradient backdrop,
// bold editorial typography, vibrant orange accents, and high-contrast dark
// "ink" cards for marketing-style call-outs.
//
// Liquid Glass discipline (iOS 26):
//   • `.glassEffect` is reserved for navigation chrome (tab bar, FAB, chips).
//   • Dense content cards use `.regularMaterial` or opaque ink so we never
//     stack glass-on-glass — that's the blur perf trap and visual mush.

import SwiftUI

// MARK: - Brand tokens

enum Brand {
    /// Vibrant brand orange — used on primary CTAs and accents.
    static let orange = Color("BrandOrange")
    /// Editorial dark — for headers and ink cards in light mode (auto-flips in dark mode).
    static let ink = Color("BrandInk")
    /// Warm cream — top of the page gradient.
    static let cream = Color("BrandCream")
    /// Peach — bottom of the page gradient.
    static let peach = Color("BrandPeach")
    /// Legacy aliases so older call sites keep compiling while we transition.
    static let navy = Color("BrandNavy")
    static let gold = Color("BrandGold")
}

enum Layout {
    static let cardCorner: CGFloat = 22
    static let chipCorner: CGFloat = 18
    static let fabSize: CGFloat = 60
    static let modalCorner: CGFloat = 30
}

// MARK: - Typography

extension Font {
    /// Bold editorial display, "TRACK YOUR PACKAGE" / "Shipping Calculator".
    static var editorialDisplay: Font {
        .system(size: 34, weight: .heavy, design: .default)
    }
    /// Section header inside a screen.
    static var editorialTitle: Font {
        .system(size: 24, weight: .bold, design: .default)
    }
    /// Slim eyebrow label above a header.
    static var eyebrow: Font {
        .system(size: 12, weight: .semibold, design: .default)
            .smallCaps()
    }
}

// MARK: - Backgrounds

/// Full-bleed cream→peach gradient. Auto-adapts in dark mode (defined in the
/// asset catalog colour entries themselves).
struct AppBackground: View {
    var body: some View {
        LinearGradient(
            colors: [Brand.cream, Brand.peach],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

extension View {
    /// Drop-in replacement for `.background(...)` that applies the brand gradient
    /// behind the screen content and is happy to live behind a NavigationStack.
    func appBackground() -> some View {
        background(AppBackground())
    }
}

// MARK: - Content cards (NOT glass — material/ink)

/// Soft material card for dense content (forms, lists, breakdowns).
/// Uses `.regularMaterial` so the gradient bleeds through subtly without the
/// stacking-blur hit that Liquid Glass would cost on every row.
struct SoftCard<Content: View>: View {
    var tint: Color? = nil
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Layout.cardCorner, style: .continuous)
                    .fill(.regularMaterial)
            )
            .overlay(
                // Tint is purely decorative — without `.allowsHitTesting(false)`,
                // the filled rectangle swallows every tap inside the card,
                // which is how the customer Calculator's Phone/Laptop toggles
                // ended up unresponsive (audit follow-up 2026-04-30).
                RoundedRectangle(cornerRadius: Layout.cardCorner, style: .continuous)
                    .fill(tint ?? .clear)
                    .allowsHitTesting(false)
            )
            .overlay(
                RoundedRectangle(cornerRadius: Layout.cardCorner, style: .continuous)
                    .stroke(Brand.ink.opacity(0.06), lineWidth: 1)
                    .allowsHitTesting(false)
            )
    }
}

/// High-contrast "ink" card — black in light mode, cream in dark mode — used
/// for the warehouse-address terminal and other editorial moments.
struct InkCard<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
            .foregroundStyle(invertedForeground)
            .background(
                RoundedRectangle(cornerRadius: Layout.cardCorner, style: .continuous)
                    .fill(Brand.ink)
            )
    }

    private var invertedForeground: Color {
        // Brand.ink already flips in dark mode (light text in dark mode means
        // we want dark foreground). Use the cream colour as the readable text.
        Brand.cream
    }
}

/// Tinted callout/banner — soft orange wash for warnings & info banners
/// (e.g. "Electronics & Special Handling" on the calculator).
struct CalloutBanner: View {
    var tint: Color = Brand.orange.opacity(0.16)
    var icon: String? = nil
    var iconTint: Color = Brand.orange
    var strokeTint: Color = Brand.orange.opacity(0.35)
    var title: String
    var message: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if let icon {
                Image(systemName: icon)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(iconTint)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(message).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(tint)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(strokeTint, lineWidth: 1)
        )
    }
}

/// Error variant of [CalloutBanner] — red wash, error icon, used everywhere
/// the customer / operator needs to see a recoverable failure (network
/// timeouts, server 4xx/5xx, validation rejects). Audit S2-4 introduced
/// this as the single primitive so views stop hand-rolling
/// `CalloutBanner(tint: Color.red.opacity(0.12), …)`.
///
/// For inline field-level errors (under a TextField), use [InlineFieldError]
/// instead — that's the small caption row, not a banner.
struct ErrorBanner: View {
    var icon: String = "exclamationmark.triangle.fill"
    var title: String
    var message: String

    var body: some View {
        CalloutBanner(
            tint: Color.red.opacity(0.12),
            icon: icon,
            iconTint: .red,
            strokeTint: Color.red.opacity(0.35),
            title: title,
            message: message
        )
    }
}

/// Caption-level error row for under a TextField or button. Audit S2-4
/// formalises the pattern that views were hand-rolling as
/// `Text(msg).font(.caption).foregroundStyle(.red)`.
struct InlineFieldError: View {
    let message: String
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "exclamationmark.circle.fill")
                .font(.caption2)
                .foregroundStyle(.red)
            Text(message)
                .font(.caption)
                .foregroundStyle(.red)
                .multilineTextAlignment(.leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Glass chrome (navigation only)

/// Glass chip — only for chrome (filter pills, status badges floating over
/// content). Do NOT use as a primary content surface.
struct GlassChip: View {
    let title: String
    var systemImage: String? = nil
    var tint: Color? = nil

    var body: some View {
        HStack(spacing: 6) {
            if let systemImage { Image(systemName: systemImage) }
            Text(title).font(.subheadline.weight(.semibold))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .glassEffect(.regular.tint(tint ?? .clear), in: Capsule())
    }
}

/// Floating action button. Lives over the gradient + content layer, so glass
/// here genuinely refracts the page beneath.
///
/// `glassID` + `namespace` are optional — when supplied they wire a shared-
/// element morph into the destination view via `.glassEffectID(...)`. The
/// morph was previously always-on, which made the FAB intermittently
/// non-interactive: while the sheet was animating dismissal, the
/// glassEffectID match still claimed ownership of the FAB's hit region and
/// the next tap was swallowed. Callers that need the morph effect opt in
/// explicitly; callers that just want a reliable button leave both at nil.
struct GlassFAB: View {
    let systemImage: String
    let glassID: String?
    let namespace: Namespace.ID?
    let action: () -> Void

    init(
        systemImage: String,
        glassID: String? = nil,
        namespace: Namespace.ID? = nil,
        action: @escaping () -> Void
    ) {
        self.systemImage = systemImage
        self.glassID = glassID
        self.namespace = namespace
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.title2.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: Layout.fabSize, height: Layout.fabSize)
                // Full circle is the hit target — without this the tappable
                // area is whatever shape the glass effect resolves to,
                // which on a morph mid-animation can collapse to a slim
                // rectangle and silently swallow taps.
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.tint(Brand.orange).interactive(), in: Circle())
        .modifier(OptionalGlassEffectIDModifier(glassID: glassID, namespace: namespace))
        .shadow(color: .black.opacity(0.18), radius: 18, x: 0, y: 10)
        .accessibilityLabel(Text("New order"))
    }
}

/// Wraps `.glassEffectID(...)` so it only attaches when the caller supplies
/// both an id and a namespace. Lets `GlassFAB` keep a single body without
/// branching at the modifier level.
private struct OptionalGlassEffectIDModifier: ViewModifier {
    let glassID: String?
    let namespace: Namespace.ID?

    func body(content: Content) -> some View {
        if let glassID, let namespace {
            content.glassEffectID(glassID, in: namespace)
        } else {
            content
        }
    }
}

// MARK: - Section header

struct SectionHeader: View {
    let title: String
    var subtitle: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.editorialTitle)
                .foregroundStyle(Brand.ink)
            if let subtitle {
                Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
    }
}

/// Editorial display header — for the top of marketing-style screens.
struct EditorialHeader: View {
    let eyebrow: String?
    let title: String
    let subtitle: String?

    init(eyebrow: String? = nil, title: String, subtitle: String? = nil) {
        self.eyebrow = eyebrow
        self.title = title
        self.subtitle = subtitle
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let eyebrow {
                Text(eyebrow)
                    .font(.eyebrow)
                    .foregroundStyle(Brand.orange)
            }
            Text(title)
                .font(.editorialDisplay)
                .foregroundStyle(Brand.ink)
                .lineLimit(2)
                .minimumScaleFactor(0.9)
            if let subtitle {
                Text(subtitle)
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Stat tile (uses material, not glass — appears in dense grids)

struct StatTile: View {
    let label: String
    let value: String
    var systemImage: String? = nil
    var tint: Color? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                if let systemImage {
                    Image(systemName: systemImage).foregroundStyle(tint ?? Brand.orange)
                }
                Text(label).font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
            }
            Text(value)
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(Brand.ink)
                .contentTransition(.numericText())
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: Layout.cardCorner, style: .continuous)
                .fill(.regularMaterial)
        )
    }
}

// MARK: - Backwards-compat shim

/// Older screens still call `GlassCard` — alias it to `SoftCard` so we don't
/// have to rewrite every callsite at once. Glass remains on chrome only.
struct GlassCard<Content: View>: View {
    let glassID: String?
    let namespace: Namespace.ID?
    let tint: Color?
    @ViewBuilder let content: () -> Content

    init(
        glassID: String? = nil,
        in namespace: Namespace.ID? = nil,
        tint: Color? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.glassID = glassID
        self.namespace = namespace
        self.tint = tint
        self.content = content
    }

    var body: some View {
        SoftCard(tint: tint, content: content)
    }
}

// MARK: - Primary action button

/// Dark, editorial primary CTA — solid Brand.ink with cream text, matches the
/// webapp "Track" / "Get Quote" buttons.
struct InkButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(Brand.cream)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Brand.ink)
            )
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// Vibrant orange CTA — secondary CTA / "Copy Address" / "Get a quote" action.
struct OrangeButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Brand.orange)
            )
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}
