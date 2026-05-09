// DispatchView.swift
// Spec §3.3 /ops/dispatch. Operator clusters released parcels by Nairobi zone
// and creates rider runs.

import SwiftUI
import ThapsusShared

struct DispatchView: View {
    @State private var vm: DispatchViewModel?
    @State private var pending: StateFlowObserver<[DispatchParcelRow]>?
    @State private var runs: StateFlowObserver<[LastMileRunDto]>?
    @State private var ridersObs: StateFlowObserver<[RiderDto]>?

    @ScaledMetric(relativeTo: .largeTitle) private var pendingCountSize: CGFloat = 56

    @State private var newRunZone: String = "Westlands"
    @State private var newRunRiderId: String = ""
    @State private var presentingNewRun = false
    @State private var selectedParcelIds: Set<String> = []
    @State private var inspectingRun: LastMileRunDto?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                EditorialHeader(
                    eyebrow: "Last mile",
                    title: "Dispatch",
                    subtitle: "Customs-cleared parcels awaiting a rider run"
                )

                InkCard {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Awaiting dispatch").font(.caption).foregroundStyle(Brand.cream.opacity(0.7))
                        Text("\(pending?.value.count ?? 0)")
                            .font(.system(size: pendingCountSize, weight: .bold, design: .rounded))
                            .contentTransition(.numericText())
                    }
                }

                Button("Create new run") { presentingNewRun = true }
                    .buttonStyle(OrangeButtonStyle())

                pendingSection

                runsSection
            }
            .padding(20)
        }
        .navigationTitle("Dispatch")
        .glassNavigationBar()
        .scrollContentBackground(.hidden)
        .appBackground()
        .refreshable { vm?.refresh() }
        .sheet(isPresented: $presentingNewRun) {
            NewRunSheet(
                zone: $newRunZone,
                riderId: $newRunRiderId,
                selectedParcelIds: $selectedParcelIds,
                availableParcels: pending?.value ?? [],
                riders: ridersObs?.value ?? []
            ) {
                let today = ISO8601DateFormatter.string(from: Date(), timeZone: TimeZone.current, formatOptions: [.withFullDate])
                vm?.createRun(
                    riderId: newRunRiderId,
                    zone: newRunZone,
                    runDate: today,
                    parcelIds: Array(selectedParcelIds)
                )
                presentingNewRun = false
                selectedParcelIds.removeAll()
            }
            .glassSheet(detents: [.large])
        }
        .sheet(item: $inspectingRun) { run in
            RunParcelsSheet(
                run: run,
                riders: ridersObs?.value ?? []
            ) { newRiderId in
                vm?.assignRider(runId: run.id, riderId: newRiderId)
            }
        }
        .task {
            guard vm == nil else { return }
            let v = ThapsusSdk.shared.dispatchViewModel()
            self.vm = v
            self.pending = StateFlowObserver(initial: []) { v.pendingParcels }
            self.runs = StateFlowObserver(initial: []) { v.runsList }
            self.ridersObs = StateFlowObserver(initial: []) { v.riders }
        }
        .onDisappear { vm?.clear(); vm = nil; pending = nil; runs = nil; ridersObs = nil }
    }

    /// Awaiting-dispatch list — every customs-cleared parcel that isn't
    /// already on a planned/in_progress run.  Mirrors what the webapp's
    /// /ops/dispatch page surfaces so an operator on iOS can see the
    /// queue without having to open the New-Run sheet.
    @ViewBuilder
    private var pendingSection: some View {
        let list = pending?.value ?? []
        VStack(spacing: 8) {
            HStack {
                Text("Awaiting dispatch")
                    .font(.headline).foregroundStyle(Brand.ink)
                Spacer()
                Text("\(list.count)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 4)

            if list.isEmpty {
                SoftCard {
                    Text("No customs-cleared parcels waiting. Once duty is paid the parcel will appear here.")
                        .font(.caption).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                ForEach(list, id: \.id) { p in
                    SoftCard {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(p.description_ ?? "Parcel")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Brand.ink)
                                Spacer()
                                if let tn = p.trackingNumber {
                                    Text(tn).font(.caption.monospaced()).foregroundStyle(.secondary)
                                }
                            }
                            if let name = p.name {
                                Text(name).font(.caption).foregroundStyle(.secondary)
                            }
                            if let phone = p.phone {
                                Text(phone).font(.caption.monospaced()).foregroundStyle(.secondary)
                            }
                            if let addr = p.effectiveAddress {
                                Text(addr).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var runsSection: some View {
        let list = runs?.value ?? []
        VStack(spacing: 8) {
            HStack {
                Text("Today's runs").font(.headline).foregroundStyle(Brand.ink)
                Spacer()
                Text("\(list.count)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 4)

            if list.isEmpty {
                SoftCard {
                    Text("No active runs. Tap Create new run to dispatch parcels above.")
                        .font(.caption).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                ForEach(list, id: \.id) { run in
                    VStack(spacing: 8) {
                        Button { inspectingRun = run } label: {
                            SoftCard {
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(run.zone()).font(.headline).foregroundStyle(Brand.ink)
                                        Text(riderLabel(for: run))
                                            .font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    GlassChip(title: friendly(run.status), systemImage: "clock", tint: Brand.orange.opacity(0.4))
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.bold))
                                        .foregroundStyle(.tertiary)
                                }
                            }
                        }
                        .buttonStyle(.plain)

                        // Start-run button — shown only when planned + rider assigned.
                        // Server will mint OTPs and notify recipients, so we surface a
                        // confirmation in the message ("Riders, on it") rather than
                        // letting it fire silently.
                        if run.status == .planned, let rider = run.riderId, !rider.isEmpty {
                            Button {
                                vm?.startRun(runId: run.id)
                            } label: {
                                Label("Start run", systemImage: "play.circle.fill")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(OrangeButtonStyle())
                        } else if run.status == .planned {
                            Text("Assign a rider before starting this run.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 4)
                        }
                    }
                }
            }
        }
    }

    /// Resolve the run's `riderId` against the riders list so the dispatch
    /// row shows the rider's name rather than a uuid prefix.  The server
    /// already includes a `rider_name` join in /api/last-mile/dispatch,
    /// which is the cheapest path; the riders-list lookup is the fallback
    /// for run rows that came from another endpoint.
    private func riderLabel(for run: LastMileRunDto) -> String {
        if let serverName = run.riderName, !serverName.isEmpty { return serverName }
        guard let id = run.riderId, !id.isEmpty else { return "Unassigned" }
        if let match = (ridersObs?.value ?? []).first(where: { $0.id == id }) {
            return match.name?.isEmpty == false ? match.name! : (match.email ?? "Rider \(id.prefix(8))")
        }
        return "Rider \(id.prefix(8))"
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
}

private struct NewRunSheet: View {
    @Binding var zone: String
    @Binding var riderId: String
    @Binding var selectedParcelIds: Set<String>
    let availableParcels: [DispatchParcelRow]
    let riders: [RiderDto]
    let onCreate: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                EditorialHeader(
                    eyebrow: "Dispatch",
                    title: "New rider run",
                    subtitle: "Pick the parcels going on this trip — server marks them out_for_delivery."
                )
                SoftCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Picker("Zone", selection: $zone) {
                            ForEach(["Westlands", "Kilimani", "Karen", "Kasarani", "Eastlands", "Lavington", "CBD"], id: \.self) { Text($0).tag($0) }
                        }
                        .pickerStyle(.menu)

                        if riders.isEmpty {
                            Text("No riders provisioned. Ask an admin to add a user with role 'rider' under Admin → Users.")
                                .font(.caption).foregroundStyle(.secondary)
                        } else {
                            Picker("Rider", selection: $riderId) {
                                Text("Select rider…").tag("")
                                ForEach(riders, id: \.id) { r in
                                    Text(displayName(for: r)).tag(r.id)
                                }
                            }
                            .pickerStyle(.menu)
                        }
                    }
                }

                parcelSelector

                Button(selectedParcelIds.isEmpty
                       ? "Create empty run"
                       : "Create run with \(selectedParcelIds.count) parcel\(selectedParcelIds.count == 1 ? "" : "s")",
                       action: onCreate)
                    .buttonStyle(InkButtonStyle())
                    .disabled(riderId.isEmpty)
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .appBackground()
    }

    private func displayName(for r: RiderDto) -> String {
        if let n = r.name, !n.isEmpty { return n }
        if let e = r.email, !e.isEmpty { return e }
        return "Rider \(r.id.prefix(8))"
    }

    @ViewBuilder
    private var parcelSelector: some View {
        SoftCard(tint: Brand.orange.opacity(0.06)) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "shippingbox").foregroundStyle(Brand.orange)
                    Text("Stops").font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    Text("\(availableParcels.count) available")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                if availableParcels.isEmpty {
                    Text("No customs-cleared parcels waiting. Once duty is paid the parcel will land here.")
                        .font(.caption).foregroundStyle(.secondary)
                } else {
                    ForEach(availableParcels, id: \.id) { p in
                        let isSel = selectedParcelIds.contains(p.id)
                        Button {
                            if isSel { selectedParcelIds.remove(p.id) } else { selectedParcelIds.insert(p.id) }
                        } label: {
                            HStack(alignment: .top, spacing: 10) {
                                Image(systemName: isSel ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(isSel ? Brand.orange : .secondary)
                                    .font(.title3)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(p.description_ ?? "Parcel")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(Brand.ink)
                                        .lineLimit(1)
                                    if let tn = p.trackingNumber {
                                        Text(tn).font(.caption.monospaced()).foregroundStyle(.secondary)
                                    }
                                    if let recipient = p.name {
                                        Text(recipient).font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                            }
                            .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                        Divider().background(Brand.ink.opacity(0.06))
                    }
                }
            }
        }
    }
}

/// Inspector sheet for an existing run — fetches its parcels via
/// `GET /api/last-mile/runs/:id/parcels` (S1-9). Read-only for now;
/// add/remove via PATCH lands in a follow-up.
private struct RunParcelsSheet: View {
    let run: LastMileRunDto
    let riders: [RiderDto]
    let onAssign: (String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var loading: Bool = true
    @State private var error: String?
    @State private var parcels: [DispatchParcelRow] = []
    @State private var riderSelection: String = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    EditorialHeader(eyebrow: "Run \(run.id.prefix(8))",
                                    title: run.zone(),
                                    subtitle: "Parcels scheduled for this rider trip.")
                    riderAssignmentCard
                    if loading {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
                    } else if let error {
                        ErrorBanner(title: "Couldn't load", message: error)
                    } else if parcels.isEmpty {
                        CrystalCard {
                            Text("No parcels assigned to this run yet.")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    } else {
                        ForEach(parcels, id: \.id) { p in
                            CrystalCard {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(p.description_ ?? "Parcel")
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(Brand.ink)
                                        Spacer()
                                        if p.hasPod {
                                            Label("Delivered", systemImage: "checkmark.seal.fill")
                                                .font(.caption.weight(.semibold))
                                                .foregroundStyle(.green)
                                        }
                                    }
                                    if let name = p.name {
                                        Text(name).font(.caption).foregroundStyle(.secondary)
                                    }
                                    if let phone = p.phone {
                                        Text(phone).font(.caption.monospaced()).foregroundStyle(.secondary)
                                    }
                                    if let addr = p.effectiveAddress {
                                        HStack(alignment: .top, spacing: 4) {
                                            Text(addr).font(.caption).foregroundStyle(.secondary)
                                            if let override = p.deliveryAddressOverride,
                                               !override.isEmpty {
                                                Text("(override)")
                                                    .font(.caption2)
                                                    .foregroundStyle(Brand.orange)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(20)
            }
            .scrollContentBackground(.hidden)
            .appBackground()
            .navigationTitle("Run parcels")
            .glassNavigationBar(displayMode: .inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
            }
            .task {
                await load()
                riderSelection = run.riderId ?? ""
            }
        }
    }

    @ViewBuilder
    private var riderAssignmentCard: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Rider").font(.headline).foregroundStyle(Brand.ink)
                if riders.isEmpty {
                    Text("No riders available. Provision one under Admin → Users.")
                        .font(.caption).foregroundStyle(.secondary)
                } else {
                    Picker("Assigned rider", selection: $riderSelection) {
                        Text("Unassigned").tag("")
                        ForEach(riders, id: \.id) { r in
                            Text(displayName(for: r)).tag(r.id)
                        }
                    }
                    .pickerStyle(.menu)

                    if riderSelection != (run.riderId ?? "") {
                        Button {
                            onAssign(riderSelection.isEmpty ? nil : riderSelection)
                            dismiss()
                        } label: {
                            Label("Save rider change", systemImage: "checkmark.circle.fill")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(OrangeButtonStyle())
                    }
                }
            }
        }
    }

    private func displayName(for r: RiderDto) -> String {
        if let n = r.name, !n.isEmpty { return n }
        if let e = r.email, !e.isEmpty { return e }
        return "Rider \(r.id.prefix(8))"
    }

    private func load() async {
        do {
            let resp = try await ThapsusSdk.shared.lastMile().fetchRunParcels(runId: run.id)
            await MainActor.run {
                parcels = resp.parcels
                loading = false
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
                self.loading = false
            }
        }
    }
}
