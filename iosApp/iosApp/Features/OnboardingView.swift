// OnboardingView.swift
// First-launch walkthrough — shown once per install (persisted in
// UserDefaults under `thapsus.onboarding.completed_v1`), unskippable, four
// pages, native SwiftUI animations only. Haptics on every page advance and
// on the final "Get started" tap.
//
// Content order matches the BFM-primary product narrative:
//   1. Shop & ship (Buy-for-me)               — primary
//   2. Pre-register parcels you bought        — secondary
//   3. Tracking + customs visibility
//   4. Payment options
//
// Insurance is intentionally not mentioned (removed across all clients).
//
// Design tokens: `Brand.ink` (active fills + headlines), `Brand.cream`
// background, `Brand.orange` for accent. Sentence-case copy throughout.
//
// Persistence is owned by the parent (`RootView`) via the `onComplete`
// closure — we just flip the flag and call back so the parent can
// transition to the auth / role-routed view.

import SwiftUI
import Lottie

struct OnboardingView: View {
    /// Called once the user reaches the final page and taps "Get started".
    /// Caller is expected to persist the completion flag (so subsequent
    /// launches skip the walkthrough) before swapping the root view.
    var onComplete: () -> Void

    @State private var currentPage: Int = 0
    @State private var pageAppearTrigger: Int = 0

    private let pages: [OnboardingPage] = OnboardingPage.all

    var body: some View {
        ZStack {
            AppBackground().ignoresSafeArea()

            VStack(spacing: 0) {
                // Pager: native TabView in `.page` style. Indicator dots
                // are hidden (we render a custom dot row at the bottom so
                // they sit on the cream background, not buried under the
                // page content's safe area).
                TabView(selection: $currentPage) {
                    ForEach(pages.indices, id: \.self) { idx in
                        OnboardingPageView(page: pages[idx], appearTrigger: pageAppearTrigger)
                            .tag(idx)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut(duration: 0.25), value: currentPage)

                VStack(spacing: 20) {
                    pageDots
                    bottomCTA
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }
        }
        // Selection haptic on every page change — including swipes the
        // user makes themselves. `.appHaptic(.tap)` resolves to .selection
        // which is the soft tick Apple ships in TabView by default; this
        // line just makes sure it also fires after a programmatic advance.
        .sensoryFeedback(.appHaptic(.tap), trigger: currentPage)
        // Bump the per-page appear trigger any time the page index changes
        // so animations inside `OnboardingPageView` replay on swipe-back.
        .onChange(of: currentPage) { _, _ in
            pageAppearTrigger &+= 1
        }
    }

    // MARK: - Components

    private var pageDots: some View {
        HStack(spacing: 8) {
            ForEach(pages.indices, id: \.self) { idx in
                Capsule()
                    .fill(idx == currentPage ? Brand.ink : Brand.ink.opacity(0.22))
                    .frame(width: idx == currentPage ? 26 : 8, height: 8)
                    .animation(.easeInOut(duration: 0.2), value: currentPage)
            }
        }
        .accessibilityElement()
        .accessibilityLabel("Page \(currentPage + 1) of \(pages.count)")
    }

    @ViewBuilder
    private var bottomCTA: some View {
        if currentPage == pages.count - 1 {
            Button(action: complete) {
                Text("Get started")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
            }
            .buttonStyle(InkButtonStyle())
            .transition(.opacity.combined(with: .move(edge: .bottom)))
            .sensoryFeedback(.appHaptic(.action), trigger: currentPage)
        } else {
            Button {
                withAnimation(.easeInOut(duration: 0.3)) {
                    currentPage = min(currentPage + 1, pages.count - 1)
                }
            } label: {
                Text("Next")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
            }
            .buttonStyle(InkButtonStyle())
        }
    }

    private func complete() {
        AppHaptics.fire(.success)
        onComplete()
    }
}

// MARK: - Page model

struct OnboardingPage: Identifiable {
    enum Illustration {
        case shipArc       // page 1: parcel + GB→KE arc
        case warehouseDrop // page 2: package falling into warehouse silhouette
        case statusPills   // page 3: tracking timeline pills cascade
        case payMethods    // page 4: M-Pesa + card with pulse
    }

    let id = UUID()
    let illustration: Illustration
    let headline: String
    let body: String

    static let all: [OnboardingPage] = [
        OnboardingPage(
            illustration: .shipArc,
            headline: "Shop the UK, delivered to Kenya.",
            body: "Send us a link from any UK retailer and we'll buy on your behalf, ship it home, and handle every step in between."
        ),
        OnboardingPage(
            illustration: .warehouseDrop,
            headline: "Already bought something? We'll ship it for you.",
            body: "Send your parcels to our Preston warehouse. We consolidate, customs-clear, and deliver to your door."
        ),
        OnboardingPage(
            illustration: .statusPills,
            headline: "Track every step.",
            body: "From purchase to your door — including KRA customs clearance — visible in one place. Realtime updates, no waiting."
        ),
        OnboardingPage(
            illustration: .payMethods,
            headline: "Pay how you like.",
            body: "M-Pesa, Lipana STK push, or any card. Earn wallet credits when you refer a friend."
        ),
    ]
}

// MARK: - Single page

private struct OnboardingPageView: View {
    let page: OnboardingPage
    /// Bumped by the parent on every page-change so animations replay when
    /// the user swipes back to a page they've already seen.
    let appearTrigger: Int

    var body: some View {
        VStack(spacing: 32) {
            Spacer(minLength: 24)

            illustration
                .frame(maxWidth: .infinity)
                .frame(height: 240)

            VStack(spacing: 14) {
                Text(page.headline)
                    .font(.editorialTitle)
                    .foregroundStyle(Brand.ink)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                    .accessibilityAddTraits(.isHeader)

                Text(page.body)
                    .font(.subheadline)
                    .foregroundStyle(Brand.ink.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            Spacer(minLength: 24)
        }
        .padding(.vertical, 24)
    }

    @ViewBuilder
    private var illustration: some View {
        switch page.illustration {
        case .shipArc:       LottieOnboarding(name: "world_map_plane")
        case .warehouseDrop: LottieOnboarding(name: "handover_parcel")
        case .statusPills:   LottieOnboarding(name: "parcel_pipeline")
        case .payMethods:    PayMethodsIllustration(trigger: appearTrigger)
        }
    }
}

// MARK: - Lottie wrapper

/// Loops a bundled Lottie `.json` from `iosApp/Assets/Onboarding/` at
/// native size with `.scaleAspectFit` so the animation respects the host
/// frame height (240pt) without overflowing horizontally.
private struct LottieOnboarding: View {
    let name: String

    var body: some View {
        LottieView(animation: .named(name))
            .playing(loopMode: .loop)
            .resizable()
            .aspectRatio(contentMode: .fit)
    }
}

// MARK: - Page 4 illustration — payment methods (native; the only page
// the user opted to keep on native motion)

private struct PayMethodsIllustration: View {
    let trigger: Int
    @State private var pulse: Bool = false

    var body: some View {
        HStack(spacing: 24) {
            PayMethodBadge(systemImage: "phone.connection.fill", label: "M-Pesa")
                .scaleEffect(pulse ? 1.05 : 0.95)
                .animation(
                    .easeInOut(duration: 1.2).repeatForever(autoreverses: true),
                    value: pulse
                )
            PayMethodBadge(systemImage: "creditcard.fill", label: "Card")
                .scaleEffect(pulse ? 0.95 : 1.05)
                .animation(
                    .easeInOut(duration: 1.2).repeatForever(autoreverses: true),
                    value: pulse
                )
        }
        .onAppear { pulse = true }
        .onChange(of: trigger) { _, _ in
            pulse.toggle()
        }
    }
}

private struct PayMethodBadge: View {
    let systemImage: String
    let label: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.system(size: 36, weight: .bold))
                .foregroundStyle(Brand.cream)
                .frame(width: 90, height: 90)
                .background(Brand.ink, in: RoundedRectangle(cornerRadius: 20))
            Text(label)
                .font(.headline)
                .foregroundStyle(Brand.ink)
        }
    }
}
