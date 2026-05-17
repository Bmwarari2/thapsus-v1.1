// RootView.swift
// Decides between the splash, sign-in flow, and the role-routed tab view based
// on the AuthSession from the Kotlin AuthRepository.
//
// Universal links and custom URL schemes:
//   - thapsus://pay/<orderId>                    → PublicPaymentView sheet
//   - https://thapsus.uk/pay/<orderId>           → same
//   - https://thapsus.uk/track/<id>              → TrackingView with tracking number
//   - https://thapsus.uk/orders/<id>             → ParcelDetailView for that parcel
//   - https://thapsus.uk/reset-password?token=…  → PasswordResetView sheet
//                                                   (also used by the welcome /
//                                                    setup-account email so
//                                                    new accounts land on it)
// Routes that need auth fall back to SignInView when not signed in.

import SwiftUI
import ThapsusShared

struct RootView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var pendingPaymentOrderId: String? = nil
    @State private var pendingTrackId: String? = nil
    @State private var pendingOrderId: String? = nil
    @State private var pendingResetToken: String? = nil
    /// First-launch walkthrough gate. Once true the user is routed into the
    /// normal splash → sign-in / dashboard flow and the onboarding never
    /// shows again on this install. Persisted in UserDefaults under
    /// `thapsus.onboarding.completed_v1`. Reinstalling the app resets it.
    @State private var hasCompletedOnboarding: Bool =
        UserDefaults.standard.bool(forKey: Self.onboardingCompletedKey)
    /// Post-authentication address-capture prompt gate. Set true once the
    /// user either saves an address or taps "Skip for now"; either action
    /// suppresses the sheet on subsequent cold launches. The Account tab
    /// stays the canonical edit path regardless.
    @State private var addressPromptDismissed: Bool =
        UserDefaults.standard.bool(forKey: Self.addressPromptDismissedKey)

    private static let onboardingCompletedKey = "thapsus.onboarding.completed_v1"
    private static let addressPromptDismissedKey = "thapsus.address.prompt.dismissed_v1"

    var body: some View {
        ZStack {
            AppBackground()
            Group {
                if !hasCompletedOnboarding {
                    OnboardingView(onComplete: completeOnboarding)
                        .transition(.opacity)
                } else if env.isInitialising {
                    SplashView()
                } else if let auth = env.session as? AuthSessionAuthenticated {
                    RootTabView(role: auth.role)
                        .transition(.opacity.combined(with: .scale(scale: 0.98)))
                } else {
                    SignInView()
                        .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.3), value: hasCompletedOnboarding)
            .animation(.easeInOut(duration: 0.25), value: env.isSignedIn)
        }
        .onOpenURL(perform: handle(url:))
        .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
            if let url = activity.webpageURL { handle(url: url) }
        }
        .sheet(item: Binding(
            get: { pendingPaymentOrderId.map { DeepLinkOrder(id: $0) } },
            set: { pendingPaymentOrderId = $0?.id }
        )) { link in
            NavigationStack {
                PublicPaymentView(orderId: link.id)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Close") { pendingPaymentOrderId = nil }
                        }
                    }
            }
        }
        .sheet(item: Binding(
            get: { pendingTrackId.map { DeepLinkOrder(id: $0) } },
            set: { pendingTrackId = $0?.id }
        )) { link in
            NavigationStack {
                TrackingView(prefilledTrackingNumber: link.id)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Close") { pendingTrackId = nil }
                        }
                    }
            }
        }
        .sheet(item: Binding(
            get: { pendingOrderId.map { DeepLinkOrder(id: $0) } },
            set: { pendingOrderId = $0?.id }
        )) { link in
            NavigationStack {
                ParcelDetailView(parcelID: link.id)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Close") { pendingOrderId = nil }
                        }
                    }
            }
        }
        .sheet(item: Binding(
            get: { pendingResetToken.map { DeepLinkOrder(id: $0) } },
            set: { pendingResetToken = $0?.id }
        )) { link in
            NavigationStack {
                PasswordResetView(token: link.id)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Close") { pendingResetToken = nil }
                        }
                    }
            }
        }
        .sheet(isPresented: addressPromptBinding) {
            AddressCaptureSheet(onResolved: markAddressPromptDismissed)
        }
    }

    /// Persists the first-launch completion flag and flips `RootView` to the
    /// normal splash / sign-in / dashboard flow. Called by `OnboardingView`
    /// from its "Get started" CTA on the final page.
    private func completeOnboarding() {
        UserDefaults.standard.set(true, forKey: Self.onboardingCompletedKey)
        withAnimation(.easeInOut(duration: 0.3)) {
            hasCompletedOnboarding = true
        }
    }

    /// True when the authenticated user has no delivery address on file
    /// and hasn't previously dismissed the prompt. Read on every
    /// recomposition — once the user provides an address via
    /// `AddressCaptureSheet` (or any other path, e.g. `ProfileEditView`),
    /// the underlying `env.session.profile.deliveryAddress` updates and
    /// the sheet stops re-presenting.
    private var addressPromptBinding: Binding<Bool> {
        Binding(
            get: {
                guard hasCompletedOnboarding,
                      !addressPromptDismissed,
                      let auth = env.session as? AuthSessionAuthenticated
                else { return false }
                let trimmed = auth.profile?.deliveryAddress?
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                return trimmed.isEmpty
            },
            set: { _ in /* dismissal flows through onResolved → markAddressPromptDismissed */ }
        )
    }

    /// Called by `AddressCaptureSheet` after either a save or skip. We
    /// persist a "asked" flag (not "address present") so the sheet
    /// doesn't reappear on every cold launch for users who deliberately
    /// skip. The Account tab → ProfileEditView remains the canonical
    /// edit path for everyone.
    private func markAddressPromptDismissed() {
        UserDefaults.standard.set(true, forKey: Self.addressPromptDismissedKey)
        addressPromptDismissed = true
    }

    private func handle(url: URL) {
        let comps = url.pathComponents.filter { $0 != "/" }
        // Custom scheme: thapsus://pay/<orderId>
        if url.scheme == "thapsus", url.host == "pay", let id = comps.first,
           Self.isValidLinkId(id) {
            pendingPaymentOrderId = id; return
        }
        // Universal links: https://thapsus.uk/<route>/<id>
        // Restrict to the exact apex + www. host and HTTPS only.  A
        // substring `contains("thapsus")` previously matched anything
        // including evil-thapsus.com — and any non-https scheme would
        // have fallen through to the same branch (audit T12).
        guard url.scheme == "https",
              let host = url.host,
              host == "thapsus.uk" || host == "www.thapsus.uk"
        else { return }

        // /reset-password?token=<hex>
        // The reset-password and welcome-account emails both link here;
        // RootView surfaces the same PasswordResetView sheet for either.
        // The path has no id component — the secret rides on the query
        // string — so this branch runs before the generic id-based routes
        // below. Token shape is the 64-char hex from
        // crypto.randomBytes(32) on the server.
        if comps.count >= 1, comps[0] == "reset-password" {
            let comp = URLComponents(url: url, resolvingAgainstBaseURL: false)
            if let token = comp?.queryItems?.first(where: { $0.name == "token" })?.value,
               Self.isValidResetToken(token) {
                pendingResetToken = token
            }
            return
        }

        guard comps.count >= 2, Self.isValidLinkId(comps[1]) else { return }
        switch comps[0] {
        case "pay":    pendingPaymentOrderId = comps[1]
        case "track":  pendingTrackId = comps[1]
        case "orders": pendingOrderId = comps[1]
        default: break
        }
    }

    /// Reset-token shape check. The server mints these via
    /// `crypto.randomBytes(32).toString('hex')` — 64 lowercase hex chars.
    /// Anything else gets dropped so a hostile link can't drive the sheet
    /// with arbitrary state.
    private static func isValidResetToken(_ raw: String) -> Bool {
        guard raw.count == 64 else { return false }
        return raw.range(of: #"^[a-f0-9]{64}$"#, options: .regularExpression) != nil
    }

    /// Conservative id validator for deep-link path components.
    /// Accepts UUIDs (orders + payment) and the public tracking-number
    /// shape `TC-YYYYMMDD-<8-hex>` minted by routes/orders.js.  Rejects
    /// anything else so a hostile link can't drive `pendingOrderId`
    /// to an arbitrary string and surface a sheet for it.
    private static func isValidLinkId(_ raw: String) -> Bool {
        if UUID(uuidString: raw) != nil { return true }
        let trackingPattern = #"^TC-\d{8}-[A-F0-9]{4,16}$"#
        return raw.range(of: trackingPattern, options: .regularExpression) != nil
    }
}

private struct DeepLinkOrder: Identifiable { let id: String }

// Launch-critical view. Deliberately avoids LiquidBackdrop's morph blobs +
// .ultraThinMaterial — those add three offscreen passes and two repeating
// animations to the first frame, which pushes cold-launch over 3 s on
// real devices. A flat gradient + wordmark paints in well under one frame.
private struct SplashView: View {
    var body: some View {
        ZStack {
            AppBackground()
            VStack(spacing: 24) {
                BrandWordmark(size: .large)
                ProgressView().tint(Brand.ink)
            }
        }
    }
}
