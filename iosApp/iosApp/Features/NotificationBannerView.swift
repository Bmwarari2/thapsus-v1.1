// NotificationBannerView.swift
// Transient banner that observes NotificationsRepository's live stream and
// pops the latest unseen row over the host screen for ~5 seconds. Tapping
// marks it as read; auto-dismiss does not. Mounted on the customer's
// Dashboard and Home so customers see status pushes without opening the
// inbox.
//
// Driven by the realtime subscription added in Phase 4 #4 — when a server
// `pushToUser(userId, 'notification', …)` writes a row to `notifications`,
// Supabase Realtime fans the INSERT into the local cache, the cache flow
// re-emits, and this view picks up the new row.

import SwiftUI
import ThapsusShared

struct NotificationBannerView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var vm: NotificationInboxViewModel?
    @State private var observer: StateFlowObserver<NotificationInboxViewModelUiState>?
    @State private var seenIds: Set<String> = []
    @State private var visible: NotificationDto?
    @State private var dismissTask: Task<Void, Never>?

    var body: some View {
        ZStack(alignment: .top) {
            Color.clear
            if let n = visible {
                bannerCard(n)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(2)
            }
        }
        .animation(.spring(duration: 0.35), value: visible?.id)
        .task { bootstrap() }
        .onDisappear {
            dismissTask?.cancel()
            vm = nil
            observer = nil
        }
    }

    private func bootstrap() {
        guard vm == nil, let userId = env.currentUserID else { return }
        let model = ThapsusSdk.shared.notificationInboxViewModel(userId: userId)
        vm = model
        let obs = StateFlowObserver(initial: model.state.value) { model.state }
        observer = obs

        // First emission populates `seenIds` so we don't pop banners for
        // notifications that already existed when the customer signed in.
        if let loaded = obs.value as? NotificationInboxViewModelUiStateLoaded {
            seenIds = Set(loaded.items.map(\.id))
        }

        // Subsequent emissions: any unread row with a fresh id is "new".
        // We only auto-pop the topmost (most recent) unseen unread row
        // from the loaded list, then mark it seen so re-emits don't
        // re-trigger.
        Task { @MainActor in
            // The Kotlin StateFlow drives the observer; we don't need an
            // explicit collect loop here — `observer.value` is updated in
            // place. Instead, watch `observer?.value` via SwiftUI by
            // re-evaluating in `onChange` below.
        }
    }

    /// Re-evaluates whenever the observer fires.
    private var latestUnread: NotificationDto? {
        guard let loaded = observer?.value as? NotificationInboxViewModelUiStateLoaded else { return nil }
        return loaded.items.first { !$0.isRead && !seenIds.contains($0.id) }
    }

    private func bannerCard(_ n: NotificationDto) -> some View {
        Button {
            vm?.markRead(id: n.id)
            seenIds.insert(n.id)
            withAnimation { visible = nil }
        } label: {
            InkCard {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: iconName(for: n))
                        .font(.title3)
                        .foregroundStyle(Brand.cream)
                        .frame(width: 30, height: 30)
                        .background(Circle().fill(Brand.orange.opacity(0.7)))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(headline(for: n))
                            .font(.eyebrow)
                            .foregroundStyle(Brand.cream.opacity(0.7))
                        Text(n.message)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Brand.cream)
                            .multilineTextAlignment(.leading)
                            .lineLimit(3)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(Brand.cream.opacity(0.5))
                }
            }
        }
        .buttonStyle(.plain)
        .onChange(of: latestUnread?.id) { _, newId in
            guard let id = newId, id != visible?.id else { return }
            visible = latestUnread
            // Auto-dismiss after 5 s; tapping cancels it manually above.
            dismissTask?.cancel()
            dismissTask = Task {
                try? await Task.sleep(for: .seconds(5))
                await MainActor.run {
                    if visible?.id == id {
                        seenIds.insert(id)
                        withAnimation { visible = nil }
                    }
                }
            }
        }
    }

    private func iconName(for n: NotificationDto) -> String {
        let m = n.message.lowercased()
        if m.contains("delivered") { return "checkmark.circle.fill" }
        if m.contains("payment") { return "creditcard.fill" }
        if m.contains("reminder") { return "bell.badge.fill" }
        if m.contains("ticket") { return "questionmark.bubble.fill" }
        return "bell.fill"
    }

    private func headline(for n: NotificationDto) -> String {
        let m = n.message.lowercased()
        if m.contains("delivered") { return "Parcel delivered" }
        if m.contains("payment") { return "Payment update" }
        if m.contains("reminder") { return "Reminder" }
        if m.contains("ticket") { return "Support" }
        return n.type.replacingOccurrences(of: "_", with: " ").uppercased()
    }
}
