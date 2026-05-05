// AdminAuditLogsView.swift
// Admin audit-log tab — paginated feed of privileged actions (provision a
// user, force a password reset, edit pricing). Mirrors `routes/admin.js:738
// GET /api/admin/logs`. Audit S3-3.

import SwiftUI
import ThapsusShared

struct AdminAuditLogsView: View {
    @State private var loading: Bool = true
    @State private var loadingMore: Bool = false
    @State private var rows: [AdminLogRow] = []
    @State private var page: Int = 1
    @State private var totalPages: Int = 1
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                EditorialHeader(
                    eyebrow: "Compliance",
                    title: "Audit logs",
                    subtitle: "Every privileged action recorded server-side."
                )

                if loading && rows.isEmpty {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
                } else if let msg = errorMessage, rows.isEmpty {
                    ErrorBanner(title: "Couldn't load audit logs", message: msg)
                } else if rows.isEmpty {
                    CrystalCard {
                        Text("No admin actions recorded yet.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                } else {
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        rowCard(row)
                    }
                    if page < totalPages {
                        Button(loadingMore ? "Loading…" : "Load more") {
                            Task { await loadMore() }
                        }
                        .disabled(loadingMore)
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity)
                    }
                }
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Audit logs")
        .glassNavigationBar()
        .refreshable {
            page = 1
            await load()
        }
        .task { await load() }
    }

    private func rowCard(_ r: AdminLogRow) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(r.action.replacingOccurrences(of: "_", with: " ").capitalized)
                        .font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                    Spacer()
                    if let when = r.createdAt {
                        Text(timestamp(when))
                            .font(.caption.monospaced()).foregroundStyle(.secondary)
                    }
                }
                if let admin = r.adminEmail ?? r.adminName {
                    Text("by \(admin)").font(.caption).foregroundStyle(.secondary)
                }
                if let details = r.details, !details.isEmpty {
                    Text(details)
                        .font(.caption2.monospaced()).foregroundStyle(.tertiary)
                        .lineLimit(3)
                }
            }
        }
    }

    private func timestamp(_ raw: String) -> String {
        // Server emits ISO 8601. Show yyyy-MM-dd HH:mm.
        if raw.count >= 16 {
            return String(raw.prefix(10)) + " " + String(raw.dropFirst(11).prefix(5))
        }
        return raw
    }

    private func load() async {
        loading = true
        errorMessage = nil
        do {
            let resp = try await ThapsusSdk.shared.adminRepo().adminLogs(page: Int32(1), limit: Int32(25))
            await MainActor.run {
                rows = resp.logs
                page = Int(resp.pagination?.page ?? 1)
                totalPages = Int(resp.pagination?.totalPages ?? 1)
                loading = false
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
                loading = false
            }
        }
    }

    private func loadMore() async {
        guard page < totalPages else { return }
        loadingMore = true
        do {
            let resp = try await ThapsusSdk.shared.adminRepo().adminLogs(page: Int32(page + 1), limit: Int32(25))
            await MainActor.run {
                rows += resp.logs
                page = Int(resp.pagination?.page ?? Int32(page + 1))
                totalPages = Int(resp.pagination?.totalPages ?? Int32(totalPages))
                loadingMore = false
            }
        } catch {
            await MainActor.run { loadingMore = false }
        }
    }
}
