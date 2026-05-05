// AppEnvironment.swift
// Mirrors the Kotlin AuthRepository's StateFlow into a SwiftUI @Observable and
// drives RealtimeSync subscriptions when the user is authenticated.

import SwiftUI
import ThapsusShared

@Observable
@MainActor
final class AppEnvironment {
    var session: any AuthSession = AuthSessionInitializing()
    private(set) var authVM: AuthViewModel?

    private var sessionTask: Task<Void, Never>?
    private var realtimeStartedFor: String?

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
    }

    func teardown() {
        sessionTask?.cancel()
        sessionTask = nil
        Task { try? await ThapsusSdk.shared.realtime().stop() }
        authVM?.clear()
        authVM = nil
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
