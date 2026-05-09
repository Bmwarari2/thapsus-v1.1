// RunStopListView.swift
// Drilldown from RiderRunView. Lists the parcels on a single run; tap a
// parcel to land in PodCaptureView with the right id pre-selected.
//
// Backed by GET /api/last-mile/runs/:id/parcels — same endpoint the
// operator dispatch UI uses, ordered by `position` from the
// last_mile_run_parcels join table (migration 012).

import SwiftUI
import ThapsusShared

struct RunStopListView: View {
    let runId: String
    let zone: String

    @State private var vm: RunStopsViewModel?
    @State private var parcels: StateFlowObserver<[DispatchParcelRow]>?
    @State private var failures: StateFlowObserver<[OutboxFailureDto]>?

    @State private var presentingPodFor: PodTarget?

    var body: some View {
        ScrollView {
            GlassEffectContainer(spacing: 16) {
                VStack(spacing: 16) {
                    SectionHeader(
                        title: "Run · \(zone)",
                        subtitle: "Tap a parcel to deliver"
                    )

                    if let list = parcels?.value, !list.isEmpty {
                        // Phase 3 — collapse parcels by recipient so a run
                        // with N parcels for one customer renders as one
                        // stop card. Tapping it opens the POD sheet with
                        // the full id list, so the rider takes one
                        // photo + signature + OTP for the whole bundle.
                        let groups = Self.groupByUser(list)
                        ForEach(Array(groups.enumerated()), id: \.element.id) { idx, group in
                            VStack(spacing: 8) {
                                Button {
                                    presentingPodFor = PodTarget(group: group)
                                } label: {
                                    GroupStopRow(sequence: idx + 1, group: group)
                                }
                                .buttonStyle(.plain)

                                // POD didn't sync? Inline retry banner so the
                                // rider knows + can re-enqueue without
                                // dropping out of the stop list (audit M2).
                                // We surface failures for any parcel in the
                                // group; tapping retry re-enqueues the whole
                                // capture.
                                ForEach(failuresForGroup(group), id: \.mutationId) { fail in
                                    PodSyncFailureBanner(
                                        failure: fail,
                                        onRetry: { vm?.retryFailure(mutationId: fail.mutationId) },
                                        onDismiss: { vm?.dismissFailure(mutationId: fail.mutationId) }
                                    )
                                }
                            }
                        }
                    } else {
                        GlassCard {
                            Text("No stops yet on this run.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(20)
            }
        }
        .navigationTitle("Stops")
        .glassNavigationBar(displayMode: .inline)
        .refreshable { vm?.refresh() }
        // Sheet dismissal is the cue that capture finished (success or
        // cancel). Re-fetch /runs/:id/parcels so a delivered parcel gets
        // its `hasPod=true` flag and the row flips to "Delivered" — the
        // local cache row alone won't change without a server roundtrip.
        // Track presence as a Bool so we don't have to retroactively
        // conform the PodTarget wrapper to Equatable.
        .onChange(of: presentingPodFor != nil) { wasShown, isShown in
            if wasShown && !isShown { vm?.refresh() }
        }
        .sheet(item: $presentingPodFor) { target in
            // PodCaptureView still needs a LastMileRunDto, but only reads
            // run.id + run.zone() from it — we synthesise a minimal stub.
            // Pass the full id list (size N for grouped stops, size 1 for
            // single-parcel stops) so the capture covers the whole bundle.
            PodCaptureView(
                run: LastMileRunDto(
                    id: runId,
                    riderId: nil,
                    zone: zone,
                    runDate: "",
                    status: RunStatus.inProgress,
                    totalStops: nil,
                    completedStops: nil,
                    riderName: nil,
                    notes: nil,
                    createdAt: nil,
                    updatedAt: nil,
                    parcels: []
                ),
                parcelIds: target.group.parcelIds
            )
            .glassSheet(detents: [.large])
        }
        // Tied to runId so the task only re-fires on a real run change,
        // not on every spurious rebuild caused by upstream StateFlow
        // emissions in RiderRunView's runs list.
        .task(id: runId) {
            // Only spin up the VM the first time we see this runId.
            // Subsequent body re-renders find vm already set and skip.
            if vm == nil {
                let v = ThapsusSdk.shared.runStopsViewModel(runId: runId)
                self.vm = v
                self.parcels = StateFlowObserver(initial: []) { v.parcels }
                self.failures = StateFlowObserver(initial: []) { v.outboxFailures }
            }
        }
        // No onDisappear cleanup: SwiftUI's @State releases the vm when the
        // view is genuinely removed from the hierarchy.  The previous
        // aggressive nil-out triggered on every brief disappear (animation,
        // upstream re-render) and blanked the list mid-view (audit D17).
    }

    /// Outbox failures attached to any parcel in the user-group. Server's
    /// 4xx rejections are keyed on the representative parcel id (the
    /// outbox event's `parcel_id` field), but we surface failures across
    /// the whole bundle so the rider sees one banner per stop.
    private func failuresForGroup(_ group: StopGroup) -> [OutboxFailureDto] {
        guard let all = failures?.value else { return [] }
        let ids = Set(group.parcelIds)
        return all.filter { ids.contains($0.targetId ?? "") }
    }

    /// Group consecutive parcels by user_id. Parcels with no user_id
    /// fall into singleton groups so they don't get accidentally
    /// bundled with each other (an unlinked parcel without an owner
    /// shouldn't share a POD with another unlinked parcel).
    fileprivate static func groupByUser(_ parcels: [DispatchParcelRow]) -> [StopGroup] {
        var groups: [StopGroup] = []
        var byUser: [String: Int] = [:]
        for p in parcels {
            let key = p.userId ?? "anon-\(p.id)"
            if let idx = byUser[key] {
                groups[idx].add(p)
            } else {
                byUser[key] = groups.count
                groups.append(StopGroup(first: p))
            }
        }
        return groups
    }
}

/// One stop on the rider's run — represents one *recipient*, who may
/// own multiple parcels in the same delivery. Identified by user_id when
/// present; falls back to a singleton bucket for unlinked parcels.
struct StopGroup: Identifiable {
    let id: String
    var parcels: [DispatchParcelRow]

    init(first: DispatchParcelRow) {
        self.id = first.userId ?? "anon-\(first.id)"
        self.parcels = [first]
    }

    mutating func add(_ p: DispatchParcelRow) { parcels.append(p) }

    var primary: DispatchParcelRow { parcels[0] }
    var parcelIds: [String] { parcels.map { $0.id } }
    var allDelivered: Bool { parcels.allSatisfy { $0.hasPod } }
}

/// Inline red banner inviting the rider to retry a 4xx-failed POD push.
/// Surfaces the server's error message so they have a chance of fixing
/// the cause (e.g. recapture the OTP), not just retrying blind.
private struct PodSyncFailureBanner: View {
    let failure: OutboxFailureDto
    let onRetry: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.red)
            VStack(alignment: .leading, spacing: 2) {
                Text("POD didn't sync — tap to retry")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.red)
                if let msg = failure.errorMessage, !msg.isEmpty {
                    Text(msg)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
            Spacer()
            Button("Retry", action: onRetry)
                .font(.caption.weight(.semibold))
                .buttonStyle(.bordered)
                .tint(.red)
            Button {
                onDismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss")
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.red.opacity(0.08))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.red.opacity(0.25), lineWidth: 1)
        )
    }
}

/// Identifiable wrapper so the .sheet(item:) binding gets a stable id
/// without having to retroactively conform StopGroup itself.
private struct PodTarget: Identifiable {
    let group: StopGroup
    var id: String { group.id }
}

/// One row per recipient — shows the recipient's name + address once,
/// then the parcels they're receiving as a small bulleted list. A
/// "Delivered" badge appears only when every parcel in the group is
/// marked hasPod.
private struct GroupStopRow: View {
    let sequence: Int
    let group: StopGroup

    var body: some View {
        let primary = group.primary
        return GlassCard {
            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    Circle().fill(Brand.gold.opacity(0.25)).frame(width: 36, height: 36)
                    Text("\(sequence)").font(.subheadline.weight(.bold))
                }
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Text(primary.name ?? "Recipient").font(.headline)
                        if group.parcels.count > 1 {
                            Text("\(group.parcels.count) parcels")
                                .font(.caption.weight(.heavy)).tracking(1)
                                .foregroundStyle(Brand.orange)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(Capsule().fill(Brand.orange.opacity(0.16)))
                        }
                    }
                    if let addr = primary.effectiveAddress {
                        Text(addr).font(.caption).foregroundStyle(.secondary)
                    }
                    if let phone = primary.phone {
                        Text(phone).font(.caption.monospaced()).foregroundStyle(.tertiary)
                    }
                    // List each parcel's description so the rider can
                    // confirm they have all of them at the door.
                    ForEach(group.parcels, id: \.id) { p in
                        HStack(spacing: 4) {
                            Image(systemName: "shippingbox")
                                .font(.caption2).foregroundStyle(.tertiary)
                            Text(p.description_ ?? "Parcel")
                                .font(.caption).foregroundStyle(.tertiary)
                                .lineLimit(1)
                        }
                    }
                    if group.allDelivered {
                        Label("Delivered", systemImage: "checkmark.seal.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.green)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }
        }
    }
}
