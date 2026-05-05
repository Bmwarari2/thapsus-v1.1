// NotificationInboxView.swift
// Notifications feed for the signed-in customer. Mirrors the webapp's
// NotificationBanner inbox surface, but as a full screen with mark-read.

import SwiftUI
import ThapsusShared

struct NotificationInboxView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var vm: NotificationInboxViewModel? = nil
    @State private var observer: StateFlowObserver<NotificationInboxViewModelUiState>? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Inbox", systemImage: "bell.fill")
                EditorialHeader(eyebrow: nil, title: "Notifications", subtitle: "Status updates, messages and reminders.")

                content
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 40)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Notifications")
        .glassNavigationBar()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Mark all read") { vm?.markAllRead() }
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Brand.orange)
            }
        }
        .task { bootstrap() }
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as NotificationInboxViewModelUiStateLoaded:
            if loaded.items.isEmpty {
                CrystalCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("All caught up").font(.headline).foregroundStyle(Brand.ink)
                        Text("You don't have any notifications yet.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            } else {
                ForEach(loaded.items, id: \.id) { item in
                    notificationCard(item)
                }
            }
        case is NotificationInboxViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        case let err as NotificationInboxViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        }
    }

    private func notificationCard(_ item: NotificationDto) -> some View {
        Button {
            if !item.isRead { vm?.markRead(id: item.id) }
        } label: {
            CrystalCard {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: item.isRead ? "envelope.open" : "envelope.badge.fill")
                        .font(.title3)
                        .foregroundStyle(item.isRead ? Color.secondary : Brand.orange)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.message)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Brand.ink)
                            .multilineTextAlignment(.leading)
                        if let created = item.createdAt {
                            Text(created)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .buttonStyle(.plain)
    }

    private func bootstrap() {
        guard vm == nil, let userId = env.currentUserID else { return }
        let model = ThapsusSdk.shared.notificationInboxViewModel(userId: userId)
        vm = model
        observer = StateFlowObserver(initial: model.state.value) {
            model.state
        }
    }
}
