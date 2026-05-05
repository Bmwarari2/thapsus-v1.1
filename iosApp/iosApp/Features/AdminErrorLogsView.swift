// AdminErrorLogsView.swift
// Server error log feed with level filter + search.

import SwiftUI
import ThapsusShared

struct AdminErrorLogsView: View {
    @State private var vm: AdminErrorLogsViewModel? = nil
    @State private var observer: StateFlowObserver<AdminErrorLogsViewModelUiState>? = nil
    @State private var search: String = ""
    @State private var level: String = "all"
    @State private var confirmingClear: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EyebrowPill(label: "Admin", systemImage: "exclamationmark.bubble")
                EditorialHeader(title: "Error logs", subtitle: "Production server errors. Filter and clear.")

                searchBar
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Error logs")
        .glassNavigationBar()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Clear", role: .destructive) { confirmingClear = true }
                    .tint(.red)
            }
        }
        .alert("Clear all error logs?", isPresented: $confirmingClear) {
            Button("Cancel", role: .cancel) {}
            Button("Clear all", role: .destructive) { vm?.clearLogs() }
        } message: {
            Text("This permanently deletes every server-side error log. The action can't be undone.")
        }
        .task { bootstrap() }
    }

    private var searchBar: some View {
        VStack(spacing: 10) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Search message", text: $search)
                    .textFieldStyle(.plain)
                    .submitLabel(.search)
                    .onSubmit { reload() }
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(.ultraThinMaterial))

            Picker("Level", selection: $level) {
                Text("All").tag("all")
                Text("Error").tag("error")
                Text("Warn").tag("warn")
                Text("Info").tag("info")
            }
            .pickerStyle(.segmented)
            .onChange(of: level) { _, _ in reload() }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as AdminErrorLogsViewModelUiStateLoaded:
            statsRow(loaded.stats)
            if loaded.logs.isEmpty {
                CrystalCard { Text("No matching logs.").font(.subheadline).foregroundStyle(.secondary) }
            } else {
                ForEach(loaded.logs, id: \.id) { log in
                    logRow(log)
                }
            }
        case is AdminErrorLogsViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
        case let err as AdminErrorLogsViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func statsRow(_ stats: ErrorLogStatsResponse) -> some View {
        HStack(spacing: 12) {
            statTile("Total", "\(stats.stats.total)", color: .blue)
            statTile("Last 24h", "\(stats.stats.last24h)", color: .red)
            statTile("Last 7d", "\(stats.stats.last7d)", color: .orange)
        }
    }

    private func statTile(_ label: String, _ value: String, color: Color) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 4) {
                Text(label.uppercased())
                    .font(.system(size: 9, weight: .heavy)).tracking(2)
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.title3.weight(.heavy))
                    .foregroundStyle(color)
            }
        }
    }

    private func logRow(_ log: ErrorLogRow) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(log.level.uppercased())
                        .font(.system(size: 9, weight: .heavy)).tracking(2)
                        .foregroundStyle(badgeColor(log.level))
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Capsule().fill(badgeColor(log.level).opacity(0.16)))
                    Spacer()
                    if let path = log.path {
                        let label = (log.method.map { "\($0) " } ?? "") + path
                        Text(label).font(.caption.monospaced()).foregroundStyle(.secondary)
                    }
                }
                Text(log.message).font(.subheadline).foregroundStyle(Brand.ink)
                if let stack = log.stack {
                    Text(stack)
                        .font(.caption2.monospaced())
                        .foregroundStyle(.tertiary)
                        .lineLimit(4)
                }
                if let created = log.createdAt {
                    Text(created).font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
    }

    private func badgeColor(_ level: String) -> Color {
        switch level.lowercased() {
        case "error": return .red
        case "warn", "warning": return .orange
        default: return .blue
        }
    }

    private func reload() {
        let levelFilter: String? = (level == "all") ? nil : level
        let searchFilter: String? = search.isEmpty ? nil : search
        vm?.load(level: levelFilter, search: searchFilter)
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.adminErrorLogsViewModel()
        vm = model
        model.load(level: nil, search: nil)
        observer = StateFlowObserver(initial: model.state.value) { model.state }
    }
}
