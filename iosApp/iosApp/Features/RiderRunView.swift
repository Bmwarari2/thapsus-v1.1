// RiderRunView.swift
// Spec §4.6. Rider PWA equivalent for the native iOS app. Today's runs by zone,
// next stop, OTP-confirmed POD capture. Outbox-backed for offline-first.

import SwiftUI
import ThapsusShared

struct RiderRunView: View {
    @Environment(AppEnvironment.self) private var env
    @State private var vm: RiderRunViewModel?
    @State private var runs: StateFlowObserver<[LastMileRunDto]>?

    @Namespace private var morph

    var body: some View {
        ScrollView {
            GlassEffectContainer(spacing: 16) {
                VStack(spacing: 16) {
                    SectionHeader(
                        title: "Today's runs",
                        subtitle: "Tap a run to see stops"
                    )

                    if let list = runs?.value, !list.isEmpty {
                        ForEach(list, id: \.id) { run in
                            NavigationLink(value: run.id) {
                                RunRow(run: run)
                            }
                            .buttonStyle(.plain)
                            .glassEffectID("run-\(run.id)", in: morph)
                        }
                    } else {
                        GlassCard {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Nothing scheduled").font(.headline)
                                Text("New runs appear here as soon as ops dispatches them.")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .padding(20)
            }
        }
        .navigationTitle("Runs")
        .glassNavigationBar()
        .refreshable { vm?.refresh() }
        .navigationDestination(for: String.self) { runId in
            // Look up the run once for the zone label, then pass only the
            // stable runId + zone Strings to the destination.  Without this,
            // every emission of the rider's `runs` StateFlow rebuilt the
            // closure with a fresh LastMileRunDto Swift wrapper, SwiftUI
            // re-mounted RunStopListView, and the stop list flickered then
            // blanked (audit D17).
            let run = runs?.value.first(where: { $0.id == runId })
            RunStopListView(runId: runId, zone: run?.zone() ?? "")
        }
        .task(id: env.currentUserID) {
            guard vm == nil else { return }
            // Runs are scoped to the rider's user id. RootTabView only routes
            // here for an authenticated rider; if currentUserID is nil the
            // session has expired — defer rather than fall back to "rider"
            // which would surface another rider's runs (or none).
            guard let riderId = env.currentUserID else { return }
            let v = ThapsusSdk.shared.riderRunViewModel(riderId: riderId)
            self.vm = v
            self.runs = StateFlowObserver(initial: []) { v.runs }
        }
        .onDisappear {
            vm?.clear()
            vm = nil
            runs = nil
        }
    }
}

private struct RunRow: View {
    let run: LastMileRunDto

    var body: some View {
        GlassCard {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "map.circle.fill")
                    .font(.title)
                    .foregroundStyle(Brand.gold)
                VStack(alignment: .leading, spacing: 4) {
                    Text(run.zone()).font(.headline)
                    Text("Run \(run.id.prefix(8))").font(.caption).foregroundStyle(.secondary)
                    GlassChip(title: friendly(run.status), systemImage: "clock", tint: tint(run.status))
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }
        }
    }

    private func friendly(_ s: RunStatus) -> String {
        switch s {
        case .planned: return "Planned"
        case .inProgress: return "In progress"
        case .completed: return "Completed"
        case .cancelled: return "Cancelled"
        @unknown default: return "—"
        }
    }

    private func tint(_ s: RunStatus) -> Color? {
        switch s {
        case .inProgress: return Brand.gold
        case .completed: return .green.opacity(0.4)
        case .cancelled: return .red.opacity(0.4)
        default: return nil
        }
    }
}
