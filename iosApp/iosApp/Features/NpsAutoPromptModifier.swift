// NpsAutoPromptModifier.swift
// Watches the customer's package stream; when a parcel transitions into the
// `delivered` state and we haven't asked NPS for it yet, presents the
// post-delivery survey sheet. Idempotent across launches via UserDefaults
// keyed by parcel id.

import SwiftUI
import ThapsusShared

private let npsAskedKeyPrefix = "thapsus.nps.asked."

struct NpsAutoPrompt: ViewModifier {
    @Environment(AppEnvironment.self) private var env

    @State private var observerTask: Task<Void, Never>?
    @State private var promptTarget: String?  // parcel id awaiting survey
    @State private var inFlight: Bool = false  // guard against re-entrancy
    @State private var serverPending: Set<String> = []  // canonical "should ask" set from /nps/pending

    func body(content: Content) -> some View {
        content
            .sheet(item: Binding(
                get: { promptTarget.map { NpsPromptId(parcelId: $0) } },
                set: { _ in promptTarget = nil }
            )) { target in
                NpsSurveyView(parcelId: target.parcelId) {
                    markAsked(target.parcelId)
                    promptTarget = nil
                }
            }
            .task { startObserving() }
            .onDisappear {
                observerTask?.cancel()
                observerTask = nil
            }
    }

    private func startObserving() {
        guard observerTask == nil, let userId = env.currentUserID else { return }
        observerTask = Task { @MainActor in
            await refreshPending()
            let repo = ThapsusSdk.shared.packages()
            for await rows in repo.observeForUser(userId: userId) {
                handle(rows)
            }
        }
    }

    /// Pull the server's pending-invitations list. The local UserDefaults
    /// "asked" flag is still respected on top — once a customer dismisses
    /// the sheet on this device we don't re-pop within this install. Server
    /// state is the cross-device source of truth for "should we ask at all".
    private func refreshPending() async {
        let pending = (try? await ThapsusSdk.shared.nps().fetchPending()) ?? Set()
        serverPending = Set(pending.map { String(describing: $0) })
    }

    private func handle(_ rows: [PackageDto]) {
        guard !inFlight, promptTarget == nil else { return }
        for pkg in rows where pkg.status == .delivered
            && serverPending.contains(pkg.id)
            && !hasAsked(pkg.id) {
            inFlight = true
            promptTarget = pkg.id
            inFlight = false
            return  // present one at a time
        }
    }

    private func hasAsked(_ id: String) -> Bool {
        UserDefaults.standard.bool(forKey: npsAskedKeyPrefix + id)
    }

    private func markAsked(_ id: String) {
        UserDefaults.standard.set(true, forKey: npsAskedKeyPrefix + id)
    }
}

private struct NpsPromptId: Identifiable {
    let parcelId: String
    var id: String { parcelId }
}

extension View {
    /// Mount on the customer dashboard / home so the NPS sheet pops once
    /// per delivered parcel.
    func npsAutoPrompt() -> some View { modifier(NpsAutoPrompt()) }
}
