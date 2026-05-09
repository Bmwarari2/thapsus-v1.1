// AppEnvironment.swift
// Mirrors the Kotlin AuthRepository's StateFlow into a SwiftUI @Observable and
// drives RealtimeSync subscriptions when the user is authenticated.

import SwiftUI
import UIKit
import ThapsusShared

@Observable
@MainActor
final class AppEnvironment {
    var session: any AuthSession = AuthSessionInitializing()
    private(set) var authVM: AuthViewModel?

    private var sessionTask: Task<Void, Never>?
    private var realtimeStartedFor: String?
    private var foregroundObserver: NSObjectProtocol?
    // Audit W6.1 follow-up — minimum gap between foreground-driven
    // /me calls so a user who task-switches rapidly doesn't spam the
    // server. 5 minutes sits comfortably below the server's default
    // 24h refresh threshold, so any genuine long-background still
    // gets a fresh token.
    private static let minForegroundRefreshIntervalSec: TimeInterval = 300
    private var lastForegroundRefreshAt: Date = .distantPast

    func bootstrap() {
        guard authVM == nil else { return }
        let vm = ThapsusSdk.shared.authViewModel()
        authVM = vm
        let stream = vm.session
        sessionTask = Task { [weak self] in
            for await value in stream {
                self?.session = value
                await self?.applyRealtime(for: value)
            }
        }
        // Listen for foreground transitions and silently re-fetch /me.
        // The KMP layer (AuthRepository.refreshSession) handles the
        // refreshed_token swap into Keychain. Swift's only job is to
        // fire the call at the right moment.
        foregroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.willEnterForegroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.refreshIfStale() }
        }
    }

    func teardown() {
        sessionTask?.cancel()
        sessionTask = nil
        if let observer = foregroundObserver {
            NotificationCenter.default.removeObserver(observer)
            foregroundObserver = nil
        }
        Task { try? await ThapsusSdk.shared.realtime().stop() }
        authVM?.clear()
        authVM = nil
    }

    /// Silently rotate the sc_token via /auth/me when:
    ///   • the user is actually signed in (skip on the auth landing screen)
    ///   • the last refresh was more than minForegroundRefreshIntervalSec ago
    /// The KMP side throttle is implicit: the server only mints a new token
    /// when the presented iat is >= 24h old. The Swift-side throttle prevents
    /// even calling /me on rapid resume cycles.
    private func refreshIfStale() {
        guard isSignedIn else { return }
        let elapsed = Date().timeIntervalSince(lastForegroundRefreshAt)
        guard elapsed >= Self.minForegroundRefreshIntervalSec else { return }
        lastForegroundRefreshAt = Date()
        authVM?.refresh()
    }

    private func applyRealtime(for session: any AuthSession) async {
        let realtime = ThapsusSdk.shared.realtime()
        if let auth = session as? AuthSessionAuthenticated {
            guard realtimeStartedFor != auth.userId else { return }
            try? await realtime.stop()
            if auth.role == .customer {
                try? await realtime.startForCustomer(userId: auth.userId)
            } else {
                try? await realtime.startForStaff()
            }
            realtimeStartedFor = auth.userId

            // Sync language preference from the freshly authenticated profile
            // so localized strings reflect the user's choice immediately.
            LocalizationStore.shared.apply(languagePref: auth.profile?.languagePref)
        } else {
            if realtimeStartedFor != nil {
                try? await realtime.stop()
                realtimeStartedFor = nil
            }
        }
    }

    var currentUserID: String? {
        if let s = session as? AuthSessionAuthenticated { return s.userId }
        return nil
    }

    var currentRole: UserRole? {
        if let s = session as? AuthSessionAuthenticated { return s.role }
        return nil
    }

    var isInitialising: Bool { session is AuthSessionInitializing }
    var isSignedIn: Bool { session is AuthSessionAuthenticated }

    func signOut() { authVM?.signOut() }
}
