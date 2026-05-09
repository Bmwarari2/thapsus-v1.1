// AdminDsarQueueView.swift
// Admin DSAR queue — list every open data-subject-access request, mark
// fulfilled / rejected, trigger the data export. Mirrors `routes/dsar.js`
// GET /queue, PATCH /:id, POST /:id/export. Audit S3-3.

import SwiftUI
import ThapsusShared

struct AdminDsarQueueView: View {
    @State private var loading: Bool = true
    @State private var rows: [DsarRequestDto] = []
    @State private var errorMessage: String?
    @State private var actionBanner: ActionBanner?
    @State private var exportingIds: Set<String> = []

    private struct ActionBanner: Identifiable {
        let id = UUID()
        let title: String
        let message: String
        let isError: Bool
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                EditorialHeader(
                    eyebrow: "Compliance",
                    title: "DSAR queue",
                    subtitle: "Open data-subject-access requests waiting for action."
                )

                if let banner = actionBanner {
                    if banner.isError {
                        ErrorBanner(title: banner.title, message: banner.message)
                    } else {
                        CalloutBanner(icon: "checkmark.circle.fill",
                                      title: banner.title, message: banner.message)
                    }
                }

                if loading {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
                } else if let msg = errorMessage {
                    ErrorBanner(title: "Couldn't load DSAR queue", message: msg)
                } else if rows.isEmpty {
                    CrystalCard {
                        Text("Queue is empty — no open DSAR requests.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                } else {
                    ForEach(rows, id: \.id) { row in
                        rowCard(row)
                    }
                }
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("DSAR queue")
        .glassNavigationBar()
        .refreshable { await load() }
        .task { await load() }
    }

    private func rowCard(_ r: DsarRequestDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(r.userName ?? r.userEmail ?? r.userId)
                            .font(.headline).foregroundStyle(Brand.ink)
                        if let email = r.userEmail, r.userName != nil {
                            Text(email).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    Text(r.type.uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(r.type == "erase" ? .red : Brand.orange)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Capsule().fill((r.type == "erase" ? Color.red : Brand.orange).opacity(0.16)))
                }

                if let due = r.dueAt {
                    Text("Due \(formatDate(due))")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if let notes = r.notes, !notes.isEmpty {
                    Text(notes).font(.caption).foregroundStyle(.secondary)
                }
                if let url = r.exportUrl, !url.isEmpty, let dl = URL(string: url) {
                    Link(destination: dl) {
                        Label("Download export", systemImage: "square.and.arrow.down")
                            .font(.caption.weight(.semibold))
                    }
                    .tint(.blue)
                }

                actionRow(r)
            }
        }
    }

    @ViewBuilder
    private func actionRow(_ r: DsarRequestDto) -> some View {
        if r.status == "open" {
            HStack(spacing: 8) {
                if r.type == "export" {
                    Button {
                        Task { await runExport(r) }
                    } label: {
                        if exportingIds.contains(r.id) {
                            ProgressView().controlSize(.small)
                        } else {
                            Label("Generate export", systemImage: "arrow.down.doc.fill")
                                .font(.caption.weight(.semibold))
                        }
                    }
                    .buttonStyle(.bordered).tint(Brand.orange)
                    .disabled(exportingIds.contains(r.id))
                }
                Button("Mark fulfilled") {
                    Task { await updateStatus(r, status: "fulfilled") }
                }
                .buttonStyle(.bordered).tint(.green)

                Button("Reject") {
                    Task { await updateStatus(r, status: "rejected") }
                }
                .buttonStyle(.bordered).tint(.red)
            }
        } else {
            Text(r.status.capitalized)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
        }
    }

    private func formatDate(_ raw: String) -> String { String(raw.prefix(10)) }

    private func load() async {
        loading = true
        errorMessage = nil
        do {
            let queue = try await ThapsusSdk.shared.dsar().queue()
            await MainActor.run {
                rows = queue
                loading = false
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
                loading = false
            }
        }
    }

    private func updateStatus(_ r: DsarRequestDto, status: String) async {
        do {
            try await ThapsusSdk.shared.dsar().updateStatus(id: r.id, status: status, notes: nil)
            await MainActor.run {
                actionBanner = ActionBanner(
                    title: "Updated",
                    message: "Marked \(r.userName ?? r.userId) → \(status).",
                    isError: false
                )
            }
            await load()
        } catch {
            await MainActor.run {
                actionBanner = ActionBanner(
                    title: "Couldn't update",
                    message: error.localizedDescription,
                    isError: true
                )
            }
        }
    }

    private func runExport(_ r: DsarRequestDto) async {
        await MainActor.run { exportingIds.insert(r.id) }
        do {
            let confirmation = try await ThapsusSdk.shared.dsar().export(id: r.id)
            await MainActor.run {
                exportingIds.remove(r.id)
                actionBanner = ActionBanner(
                    title: "Export emailed",
                    message: confirmation,
                    isError: false
                )
            }
            await load()
        } catch {
            await MainActor.run {
                exportingIds.remove(r.id)
                actionBanner = ActionBanner(
                    title: "Export failed",
                    message: error.localizedDescription,
                    isError: true
                )
            }
        }
    }
}
