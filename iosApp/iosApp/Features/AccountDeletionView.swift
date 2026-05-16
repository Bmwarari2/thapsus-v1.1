// AccountDeletionView.swift
// Customer-self-serve account deletion with a 14-day cooldown. On
// request the server builds an HTML data export, uploads it to
// private Supabase Storage, and emails the customer a signed
// download link. The same link is shown here while the cooldown
// runs; "Cancel deletion" voids the request before the scheduled
// date.
//
// Backed by the shared AccountDeletionViewModel (presentation/
// AccountDeletionViewModel.kt). SKIE flattens its sealed-interface
// UiState into AccountDeletionViewModelUiState* below.

import SwiftUI
import ThapsusShared

struct AccountDeletionView: View {
    @State private var vm: AccountDeletionViewModel? = nil
    @State private var stateObs: StateFlowObserver<AccountDeletionViewModelUiState>? = nil
    @State private var actionObs: StateFlowObserver<AccountDeletionViewModelActionState>? = nil

    @State private var showConfirmAlert: Bool = false
    @State private var showCancelAlert: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EyebrowPill(label: "Account", systemImage: "person.crop.circle.badge.xmark")
                EditorialHeader(
                    title: "Delete your account",
                    subtitle: "Closing your account starts a 14-day cooldown. We'll email you a copy of everything we hold on you as a single HTML file, in case you want it for your records."
                )

                actionBanner
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Delete account")
        .glassNavigationBar()
        .alert(
            "Start 14-day deletion?",
            isPresented: $showConfirmAlert
        ) {
            Button("Start deletion", role: .destructive) {
                vm?.startDeletion()
                showConfirmAlert = false
            }
            Button("Cancel", role: .cancel) { showConfirmAlert = false }
        } message: {
            Text("Your account will be permanently deleted 14 days from now. You can cancel any time before then. We'll email you a download of your data.")
        }
        .alert(
            "Cancel deletion?",
            isPresented: $showCancelAlert
        ) {
            Button("Cancel deletion") {
                vm?.cancelDeletion(reason: nil)
                showCancelAlert = false
            }
            Button("Keep deletion", role: .cancel) { showCancelAlert = false }
        } message: {
            Text("Your account will stay as it is. You can request deletion again at any time.")
        }
        .task { bootstrap() }
    }

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case let done as AccountDeletionViewModelActionStateDone:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Done", message: done.message)
        case let err as AccountDeletionViewModelActionStateError:
            ErrorBanner(title: "Couldn't update", message: err.message)
        case is AccountDeletionViewModelActionStateInFlight:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch stateObs?.value {
        case is AccountDeletionViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        case let active as AccountDeletionViewModelUiStateActive:
            activeView(active.request)
        case let cancelled as AccountDeletionViewModelUiStateCancelled:
            cancelledView(cancelled.request)
        case is AccountDeletionViewModelUiStateCompleted:
            completedView
        case is AccountDeletionViewModelUiStateIdle:
            idleView
        case let err as AccountDeletionViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    // MARK: - States

    private var idleView: some View {
        VStack(alignment: .leading, spacing: 14) {
            CrystalCard {
                VStack(alignment: .leading, spacing: 8) {
                    Label("What happens", systemImage: "info.circle")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Brand.ink)
                    bulletRow("We email you a single HTML file with everything we hold on you (profile, orders, payments, tickets…).")
                    bulletRow("Your account is held in cooldown for 14 days. You can cancel from this screen any time.")
                    bulletRow("On the 14th day, your account and every order, package, payment, ticket, and message is permanently deleted.")
                    bulletRow("Payment-proof artefacts (M-Pesa SMS contents, payer phone, Stripe IDs) are not included in the export.")
                }
            }
            Button("Start deletion") { showConfirmAlert = true }
                .buttonStyle(GlassSheenButtonStyle(fill: Color.red, foreground: .white))
        }
    }

    private func activeView(_ request: AccountDeletionRequestDto) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            CrystalCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("\(request.daysRemaining) days remaining")
                        .font(.title2.weight(.heavy))
                        .foregroundStyle(Brand.ink)
                    Text("Scheduled deletion: \(formatScheduled(request.scheduledDeletionAt))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if request.exportEmailedAt != nil {
                        Label("Data export emailed", systemImage: "envelope.fill")
                            .font(.caption)
                            .foregroundStyle(.green)
                    }
                }
            }

            if let url = request.exportSignedUrl, let parsed = URL(string: url) {
                CrystalCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("Your data, as HTML", systemImage: "doc.text.fill")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Brand.ink)
                        Text("Open in any browser. The link in the email and here both stay live for 30 days.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Link(destination: parsed) {
                            Label("Download my data", systemImage: "arrow.down.circle.fill")
                                .frame(maxWidth: .infinity)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

                        Button("Refresh download link") { vm?.refreshExportUrl() }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Brand.ink.opacity(0.7))
                    }
                }
            } else {
                CrystalCard {
                    Text("Your data export wasn't generated. Tap below to refresh.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Button("Generate download link") { vm?.refreshExportUrl() }
                        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                }
            }

            Button("Cancel deletion") { showCancelAlert = true }
                .buttonStyle(GlassSheenButtonStyle(
                    fill: Brand.ink.opacity(0.08),
                    foreground: Brand.ink
                ))
        }
    }

    private func cancelledView(_ request: AccountDeletionRequestDto) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            CrystalCard {
                VStack(alignment: .leading, spacing: 8) {
                    Label("Previous request cancelled", systemImage: "xmark.circle.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Brand.ink)
                    if let cancelledAt = request.cancelledAt {
                        Text("Cancelled \(formatScheduled(cancelledAt))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Text("You can request deletion again any time.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            idleView
        }
    }

    private var completedView: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                Label("Account already deleted", systemImage: "checkmark.seal.fill")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Brand.ink)
                Text("If you're seeing this, your session is stale. Sign out and back in to confirm.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Helpers

    private func bulletRow(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("•")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Brand.orange)
            Text(text)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private func formatScheduled(_ iso: String) -> String {
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let parsed = isoFormatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        guard let date = parsed else { return iso }
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .short
        return f.string(from: date)
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.accountDeletionViewModel()
        vm = model
        model.load()
        stateObs = StateFlowObserver(initial: model.state.value) { model.state }
        actionObs = StateFlowObserver(initial: model.action.value) { model.action }
    }
}
