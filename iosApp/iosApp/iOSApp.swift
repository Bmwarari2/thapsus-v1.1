// iOSApp.swift
// Single SwiftUI app entry. Boots the KMP shared SDK with Supabase credentials
// pulled from xcconfig (never hard-coded in source).

import SwiftUI
import ThapsusShared

@main
struct ThapsusCargoApp: App {
    @State private var environment = AppEnvironment()
    @State private var appearance = AppearanceSettings()

    init() {
        let info = Bundle.main.infoDictionary
        let url = (info?["SUPABASE_URL"] as? String) ?? ProcessInfo.processInfo.environment["SUPABASE_URL"] ?? ""
        let key = (info?["SUPABASE_ANON_KEY"] as? String) ?? ProcessInfo.processInfo.environment["SUPABASE_ANON_KEY"] ?? ""
        let apiBase = (info?["API_BASE_URL"] as? String) ?? ProcessInfo.processInfo.environment["API_BASE_URL"] ?? ""

        // Fail loud on placeholder / missing Express URL — otherwise Railway's
        // generic 404 ("Application not found") looks like a login bug.
        // precondition() (rather than assert()) so release builds also crash
        // here instead of silently shipping with a placeholder URL.
        precondition(
            !apiBase.contains("your-app.up.railway.app") && !apiBase.isEmpty,
            "API_BASE_URL is unset or still the placeholder. Edit iosApp/Configuration/Config.local.xcconfig — set API_BASE_URL to your real Express backend URL (Railway, or http://localhost:5000 for local dev). Clean (⇧⌘K) before rebuilding."
        )

        // Boot the KMP SDK synchronously here — Koin DSL evaluation is cheap
        // (~10–30 ms) and bootstrap() needs the registry ready before it can
        // call authViewModel(). The expensive bit (Kotlin/Native framework
        // load) already happened on dyld import; this is just module wiring.
        ThapsusSdk.shared.start(
            supabaseUrl: url,
            supabaseAnonKey: key,
            apiBaseUrl: apiBase,
            driverFactory: DatabaseDriverFactory(),
            secureSettings: SecureSettings()
        )

        // Kick auth rehydration off NOW, in parallel with SwiftUI building
        // the first frame. Previously this only fired from .task on RootView,
        // i.e. after the splash had already rendered — adding ~one frame of
        // unnecessary "Initialising" splash time on every cold launch.
        environment.bootstrap()

        // Pull server-driven app config (warehouse code, support WhatsApp,
        // OTP length …) once at boot — see audit S2-3. Falls back to bundled
        // defaults if the call fails so the WhatsApp button + warehouse
        // barcode still work offline / before Railway env vars are set.
        Task.detached(priority: .background) {
            _ = try? await ThapsusSdk.shared.appConfig().refresh(force: false)
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(environment)
                .environment(appearance)
                .preferredColorScheme(appearance.theme.colorScheme)
                .tint(Brand.orange)
        }
    }
}
