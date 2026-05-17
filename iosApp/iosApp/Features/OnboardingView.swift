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
        case .shipArc:       ShipArcIllustration(trigger: appearTrigger)
        case .warehouseDrop: WarehouseDropIllustration(trigger: appearTrigger)
        case .statusPills:   StatusPillsIllustration(trigger: appearTrigger)
        case .payMethods:    PayMethodsIllustration(trigger: appearTrigger)
        }
    }
}

// MARK: - Page 1 illustration — parcel + GB→KE arc

private struct ShipArcIllustration: View {
    let trigger: Int
    @State private var arcProgress: CGFloat = 0
    @State private var parcelOffset: CGFloat = 0

    var body: some View {
        ZStack {
            // Arc line — animated stroke from GB to KE.
            GeometryReader { proxy in
                let w = proxy.size.width
                let h = proxy.size.height
                Path { p in
                    p.move(to: CGPoint(x: w * 0.18, y: h * 0.72))
                    p.addQuadCurve(
                        to: CGPoint(x: w * 0.82, y: h * 0.72),
                        control: CGPoint(x: w * 0.5, y: h * 0.18)
                    )
                }
                .trim(from: 0, to: arcProgress)
                .stroke(Brand.orange, style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [2, 6]))
            }
            .frame(height: 180)

            HStack(spacing: 0) {
                FlagDot(label: "UK")
                Spacer()
                FlagDot(label: "KE")
            }
            .padding(.horizontal, 32)
            .offset(y: 50)

            Image(systemName: "shippingbox.fill")
                .font(.system(size: 42, weight: .bold))
                .foregroundStyle(Brand.ink)
                .offset(x: parcelOffset, y: -28)
        }
        .onAppear(perform: replay)
        .onChange(of: trigger) { _, _ in replay() }
    }

    private func replay() {
        arcProgress = 0
        parcelOffset = -120
        withAnimation(.easeInOut(duration: 1.1)) {
            arcProgress = 1
        }
        withAnimation(.easeInOut(duration: 1.1)) {
            parcelOffset = 120
        }
    }
}

private struct FlagDot: View {
    let label: String
    var body: some View {
        VStack(spacing: 6) {
            Circle()
                .fill(Brand.ink)
                .frame(width: 44, height: 44)
                .overlay(
                    Text(label)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Brand.cream)
                )
            Circle()
                .fill(Brand.ink.opacity(0.18))
                .frame(width: 56, height: 8)
                .blur(radius: 4)
                .offset(y: -4)
        }
    }
}

// MARK: - Page 2 illustration — package falling into warehouse

private struct WarehouseDropIllustration: View {
    let trigger: Int
    @State private var boxY: CGFloat = -80
    @State private var settle: CGFloat = 1.0

    var body: some View {
        ZStack(alignment: .bottom) {
            // Warehouse outline — simple shed silhouette.
            WarehouseSilhouette()
                .fill(Brand.ink)
                .frame(width: 220, height: 140)

            Image(systemName: "shippingbox.fill")
                .font(.system(size: 36, weight: .bold))
                .foregroundStyle(Brand.orange)
                .offset(y: boxY)
                .scaleEffect(settle)
        }
        .frame(height: 240, alignment: .bottom)
        .onAppear(perform: replay)
        .onChange(of: trigger) { _, _ in replay() }
    }

    private func replay() {
        boxY = -160
        settle = 1.0
        withAnimation(.easeIn(duration: 0.6)) {
            boxY = -54
        }
        withAnimation(.spring(response: 0.35, dampingFraction: 0.5).delay(0.6)) {
            settle = 0.9
        }
        withAnimation(.spring(response: 0.35, dampingFraction: 0.7).delay(0.8)) {
            settle = 1.0
        }
    }
}

private struct WarehouseSilhouette: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let roofPeak = rect.minY + rect.height * 0.18
        let roofEnd = rect.minY + rect.height * 0.35
        p.move(to: CGPoint(x: rect.minX, y: roofEnd))
        p.addLine(to: CGPoint(x: rect.midX, y: roofPeak))
        p.addLine(to: CGPoint(x: rect.maxX, y: roofEnd))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

// MARK: - Page 3 illustration — status timeline cascade

private struct StatusPillsIllustration: View {
    let trigger: Int
    @State private var visibleCount: Int = 0

    private let steps: [(String, String)] = [
        ("checkmark.circle.fill", "Purchased"),
        ("airplane",              "In flight"),
        ("doc.text.fill",         "Customs"),
        ("truck.box.fill",        "Out for delivery"),
        ("house.fill",            "Delivered"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(steps.indices, id: \.self) { idx in
                HStack(spacing: 14) {
                    Image(systemName: steps[idx].0)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Brand.cream)
                        .frame(width: 32, height: 32)
                        .background(Brand.ink, in: Circle())
                    Text(steps[idx].1)
                        .font(.headline)
                        .foregroundStyle(Brand.ink)
                    Spacer()
                }
                .opacity(idx < visibleCount ? 1 : 0)
                .offset(x: idx < visibleCount ? 0 : -20)
                .animation(.easeOut(duration: 0.35).delay(Double(idx) * 0.12), value: visibleCount)
            }
        }
        .padding(.horizontal, 32)
        .onAppear(perform: replay)
        .onChange(of: trigger) { _, _ in replay() }
    }

    private func replay() {
        visibleCount = 0
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            visibleCount = steps.count
        }
    }
}

// MARK: - Page 4 illustration — payment methods

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
