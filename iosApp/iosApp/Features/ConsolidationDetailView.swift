// ConsolidationDetailView.swift
// Spec §4.4 manifest builder, AWB capture, Tudor handover.
// Operators add ready-to-consol parcels to the manifest from the same screen.

import SwiftUI
import ThapsusShared

struct ConsolidationDetailView: View {
    let consolidationId: String

    @Environment(AppEnvironment.self) private var env

    @State private var vm: ConsolidationDetailViewModel?
    @State private var parcels: StateFlowObserver<[PackageDto]>?
    @State private var available: StateFlowObserver<[PackageDto]>?
    @State private var summary: StateFlowObserver<ConsolidationSummary>?
    @State private var saving: StateFlowObserver<KotlinBoolean>?
    @State private var errorObs: StateFlowObserver<String?>?

    @State private var awb: String = ""
    @State private var tudorInvoice: String = ""
    @State private var showAddParcels: Bool = false
    @State private var showAssignAgent: Bool = false
    @State private var consolDto: StateFlowObserver<ConsolidationDto?>?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let msg = errorObs?.value {
                    ErrorBanner(title: "Something went wrong", message: msg)
                        .onTapGesture { vm?.dismissError() }
                }
                summaryCard
                lockCard
                lifecycleCard
                addParcelsCard
                if env.currentRole == .admin { assignAgentCard }
                printManifestCard
                awbCard
                tudorCard
                parcelsCard
            }
            .padding(20)
        }
        .navigationTitle("Consolidation")
        .glassNavigationBar(displayMode: .inline)
        .scrollContentBackground(.hidden)
        .appBackground()
        .sheet(isPresented: $showAddParcels) {
            AddParcelsSheet(available: available?.value ?? []) { ids in
                vm?.assignParcels(parcelIds: ids)
            }
        }
        .sheet(isPresented: $showAssignAgent) {
            AssignAgentSheet(currentAgentId: consolDto?.value?.assignedAgentId) { selectedId in
                vm?.assignAgent(agentId: selectedId)
            }
        }
        .task {
            guard vm == nil else { return }
            let v = ThapsusSdk.shared.consolidationDetailViewModel(consolidationId: consolidationId)
            self.vm = v
            self.parcels = StateFlowObserver(initial: []) { v.parcels }
            self.available = StateFlowObserver(initial: []) { v.availableParcels }
            self.summary = StateFlowObserver(
                initial: ConsolidationSummary.companion.empty()
            ) { v.summary }
            self.saving = StateFlowObserver(initial: KotlinBoolean(bool: false)) { v.saving }
            self.errorObs = StateFlowObserver(initial: nil) { v.error }
            let consolFlow = ThapsusSdk.shared.consolidations().observeOne(id: consolidationId)
            self.consolDto = StateFlowObserver(initial: nil) { consolFlow }
        }
        .onDisappear { vm?.clear(); vm = nil; parcels = nil; available = nil; summary = nil; saving = nil; errorObs = nil; consolDto = nil }
    }

    @ViewBuilder
    private var summaryCard: some View {
        let s = summary?.value
        InkCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Manifest summary").font(.headline)
                HStack {
                    statBlock("Parcels", "\(s?.totalParcels ?? 0)")
                    statBlock("Chargeable", String(format: "%.1f kg", Double(truncating: (s?.totalChargeableKg ?? 0) as NSNumber)))
                    statBlock("Declared", String(format: "£%.0f", Double(s?.totalDeclaredValueGbpPence ?? 0) / 100))
                }
            }
        }
    }

    @ViewBuilder
    private func statBlock(_ k: String, _ v: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(k).font(.caption).foregroundStyle(Brand.cream.opacity(0.7))
            Text(v).font(.title3.weight(.semibold)).contentTransition(.numericText())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var printManifestCard: some View {
        let count = parcels?.value.count ?? 0
        SoftCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Manifest").font(.headline).foregroundStyle(Brand.ink)
                Text("Print an A4 manifest for the master AWB packet — parcel list, weights, declared values, totals.")
                    .font(.caption).foregroundStyle(.secondary)
                Button {
                    printManifest()
                } label: {
                    Label(count == 0 ? "No parcels yet" : "Print manifest", systemImage: "printer.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(InkButtonStyle())
                .disabled(count == 0)
            }
        }
    }

    private func printManifest() {
        guard let list = parcels?.value, !list.isEmpty else { return }
        let s = summary?.value
        let totalParcels = Int(s?.totalParcels ?? 0)
        let totalKg = Double(truncating: (s?.totalChargeableKg ?? 0) as NSNumber)
        let totalDeclared = Double(s?.totalDeclaredValueGbpPence ?? 0) / 100
        let opName: String
        if let auth = env.session as? AuthSessionAuthenticated {
            opName = auth.profile?.fullName ?? auth.email ?? "Operator"
        } else {
            opName = "Operator"
        }
        ManifestPrinter.print(
            consolidationId: consolidationId,
            parcels: list,
            totalParcels: totalParcels,
            totalChargeableKg: totalKg,
            totalDeclaredValueGbp: totalDeclared,
            masterAwb: awb.isEmpty ? nil : awb,
            operatorName: opName,
            onComplete: { _ in }
        )
    }

    @ViewBuilder
    private var addParcelsCard: some View {
        let count = available?.value.count ?? 0
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Ready to consolidate").font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    Text("\(count) available")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                Button {
                    showAddParcels = true
                } label: {
                    Label(count == 0 ? "No parcels ready" : "Add parcels to manifest", systemImage: "tray.and.arrow.down")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(OrangeButtonStyle())
                .disabled(count == 0 || saving?.value.boolValue == true)
            }
        }
    }

    @ViewBuilder
    private var assignAgentCard: some View {
        let assigned = consolDto?.value?.assignedAgentId
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Clearing agent").font(.headline).foregroundStyle(Brand.ink)
                Text(assigned == nil
                     ? "No clearing agent assigned. The Customs tab will stay empty for the agent until you pick one."
                     : "Currently assigned: \(assigned!)")
                    .font(.caption).foregroundStyle(.secondary)
                Button {
                    showAssignAgent = true
                } label: {
                    Label(assigned == nil ? "Assign clearing agent" : "Change clearing agent",
                          systemImage: "person.crop.circle.badge.plus")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(OrangeButtonStyle())
                .disabled(saving?.value.boolValue == true)
            }
        }
    }

    @ViewBuilder
    private var lockCard: some View {
        // The lock toggle freezes the manifest server-side so customers stop
        // seeing the cut-off countdown and operators can't accidentally
        // attach more parcels. Audit §3.2.9.
        let status = consolDto?.value?.status
        let isLocked = status == .cutoffLocked
        let isOpen = status == .open
        SoftCard {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Image(systemName: isLocked ? "lock.fill" : "lock.open.fill")
                            .foregroundStyle(isLocked ? Brand.orange : .secondary)
                        Text(isLocked ? "Manifest locked" : "Manifest open")
                            .font(.headline).foregroundStyle(Brand.ink)
                    }
                    Text(isLocked
                         ? "No more parcels can be attached. Unlock to re-open."
                         : "Lock once you've added every parcel for this flight.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                if isLocked {
                    Button("Unlock") { vm?.unlock() }
                        .buttonStyle(.bordered)
                        .tint(Brand.orange)
                        .disabled(saving?.value.boolValue == true)
                } else if isOpen {
                    Button("Lock") { vm?.lock() }
                        .buttonStyle(InkButtonStyle())
                        .disabled(saving?.value.boolValue == true)
                } else {
                    // Once it's flown (in_transit / arrived / cleared), the
                    // lock state is implicit and shouldn't be flipped from
                    // here — surface a disabled badge so operators know not
                    // to touch it.
                    Text(status?.name.capitalized.replacingOccurrences(of: "_", with: " ") ?? "—")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    /// Status-driven action card. Each lifecycle hop (in_transit →
    /// arrived_jkia → cleared → closed) surfaces exactly one button so an
    /// operator/admin can flip the status without typing the value into a
    /// PATCH body. The arrival/cleared timestamps are stamped server-side
    /// via the shared view-model helpers.
    @ViewBuilder
    private var lifecycleCard: some View {
        let status = consolDto?.value?.status
        let arrival = consolDto?.value?.arrivalAt
        SoftCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Lifecycle").font(.headline).foregroundStyle(Brand.ink)
                Text(lifecycleSubtitle(status))
                    .font(.caption).foregroundStyle(.secondary)
                if status == .inTransit || status == .handedToTudor {
                    Button {
                        vm?.markArrivedJkia()
                    } label: {
                        Label("Mark arrived in Nairobi", systemImage: "airplane.arrival")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(OrangeButtonStyle())
                    .disabled(saving?.value.boolValue == true)
                } else if status == .jkiaArrived {
                    if let arrival, !arrival.isEmpty {
                        Text("Touched down: \(arrival.prefix(16))")
                            .font(.caption2.monospaced()).foregroundStyle(.tertiary)
                    }
                    Button {
                        vm?.markCleared()
                    } label: {
                        Label("Mark customs cleared", systemImage: "checkmark.shield")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(InkButtonStyle())
                    .disabled(saving?.value.boolValue == true)
                } else if status == .cleared {
                    Button {
                        vm?.markClosed()
                    } label: {
                        Label("Close consolidation", systemImage: "archivebox")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(Brand.ink)
                    .disabled(saving?.value.boolValue == true)
                } else if status == .closed {
                    Label("Closed", systemImage: "archivebox.fill")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private func lifecycleSubtitle(_ s: ConsolidationStatus?) -> String {
        switch s {
        case .open, .cutoffLocked, .manifested: return "Submit the master AWB to flip the consolidation to in-transit."
        case .handedToTudor, .inTransit: return "Tap the button below once the flight lands at JKIA."
        case .jkiaArrived: return "Customs is reviewing. Mark cleared once the agent confirms release."
        case .cleared: return "Parcels are released. Close the consolidation when every parcel has shipped to last-mile."
        case .closed: return "This consolidation is finished. No further action required."
        default: return ""
        }
    }

    @ViewBuilder
    private var awbCard: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Master AWB").font(.headline).foregroundStyle(Brand.ink)
                inkField("AWB number", text: $awb)
                Button("Submit AWB & mark in-transit") {
                    vm?.submitMasterAwb(awb: awb, pdfUrl: nil)
                }
                .buttonStyle(InkButtonStyle())
                .disabled(awb.isEmpty || saving?.value.boolValue == true)
            }
        }
    }

    @ViewBuilder
    private var tudorCard: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Tudor handover").font(.headline).foregroundStyle(Brand.ink)
                inkField("Tudor invoice number", text: $tudorInvoice)
                Button("Save Tudor invoice") {
                    vm?.submitTudorInvoice(invoiceNo: tudorInvoice)
                }
                .buttonStyle(OrangeButtonStyle())
                .disabled(tudorInvoice.isEmpty || saving?.value.boolValue == true)
            }
        }
    }

    @ViewBuilder
    private var parcelsCard: some View {
        if let list = parcels?.value, !list.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("Parcels in this manifest").font(.headline).foregroundStyle(Brand.ink).padding(.horizontal, 4)
                ForEach(list, id: \.id) { p in
                    SoftCard {
                        HStack {
                            Text(p.description_ ?? "Parcel").font(.subheadline).foregroundStyle(Brand.ink)
                            Spacer()
                            if let kg = p.chargeableKg?.doubleValue {
                                Text(String(format: "%.1f kg", kg))
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func inkField(_ placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .textFieldStyle(.plain)
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Brand.cream.opacity(0.6))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
            )
    }
}

/// Multi-select picker for attaching ready-to-consol parcels to a consolidation.
///
/// Uses explicit tap-to-toggle rows with a leading checkmark icon instead of
/// `List(selection:)` + `editMode` — the latter renders without a visible
/// selection indicator when the row body is custom-styled, which left
/// operators tapping rows that didn't appear to register.
private struct AddParcelsSheet: View {
    @Environment(\.dismiss) private var dismiss
    let available: [PackageDto]
    let onSubmit: (_ ids: [String]) -> Void

    @State private var selection: Set<String> = []
    @State private var submitted: Bool = false

    var body: some View {
        NavigationStack {
            Group {
                if available.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "tray").font(.largeTitle).foregroundStyle(.secondary)
                        Text("No parcels ready to consolidate.")
                            .font(.subheadline).foregroundStyle(.secondary)
                        Text("Receive + screen parcels at the warehouse first; they'll show up here.")
                            .font(.caption).foregroundStyle(.tertiary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 30)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        Section {
                            HStack {
                                Button(allSelected ? "Deselect all" : "Select all") {
                                    if allSelected { selection.removeAll() }
                                    else { selection = Set(available.map { $0.id }) }
                                }
                                .font(.caption.weight(.semibold))
                                Spacer()
                                Text("\(selection.count) of \(available.count) selected")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        Section {
                            ForEach(available, id: \.id) { p in
                                let isSel = selection.contains(p.id)
                                Button {
                                    if isSel { selection.remove(p.id) } else { selection.insert(p.id) }
                                } label: {
                                    HStack(alignment: .top, spacing: 12) {
                                        Image(systemName: isSel ? "checkmark.circle.fill" : "circle")
                                            .foregroundStyle(isSel ? Brand.orange : .secondary)
                                            .font(.title3)
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(p.description_ ?? "Parcel")
                                                .font(.subheadline.weight(.semibold))
                                                .foregroundStyle(Brand.ink)
                                            HStack(spacing: 8) {
                                                if let kg = p.chargeableKg?.doubleValue {
                                                    Text(String(format: "%.2f kg", kg))
                                                        .font(.caption.monospacedDigit())
                                                        .foregroundStyle(.secondary)
                                                }
                                                Text(p.status.name.lowercased().replacingOccurrences(of: "_", with: " "))
                                                    .font(.caption.weight(.semibold))
                                                    .foregroundStyle(.secondary)
                                            }
                                        }
                                        Spacer()
                                    }
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Add parcels")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(submitted ? "Adding…" : "Add (\(selection.count))") {
                        guard !submitted, !selection.isEmpty else { return }
                        submitted = true
                        onSubmit(Array(selection))
                        dismiss()
                    }
                    .disabled(submitted || selection.isEmpty)
                }
            }
        }
    }

    private var allSelected: Bool {
        !available.isEmpty && selection.count == available.count
    }
}

/// Admin-only picker for assigning a clearing agent to a consolidation.
private struct AssignAgentSheet: View {
    @Environment(\.dismiss) private var dismiss
    let currentAgentId: String?
    let onSubmit: (String?) -> Void

    @State private var agents: [AdminUserDto] = []
    @State private var loading: Bool = true
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Group {
                if loading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let msg = error {
                    VStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.red)
                        Text(msg).font(.subheadline).foregroundStyle(.secondary)
                        Button("Retry") { Task { await load() } }
                    }.frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if agents.isEmpty {
                    Text("No clearing agents found. Provision one from Admin → Users.")
                        .font(.subheadline).foregroundStyle(.secondary)
                        .padding()
                } else {
                    List {
                        if currentAgentId != nil {
                            Button {
                                onSubmit(nil)
                                dismiss()
                            } label: {
                                Label("Unassign current agent", systemImage: "person.crop.circle.badge.xmark")
                                    .foregroundStyle(.red)
                            }
                        }
                        Section("Clearing agents") {
                            ForEach(agents, id: \.id) { u in
                                Button {
                                    onSubmit(u.id)
                                    dismiss()
                                } label: {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(u.name).foregroundStyle(Brand.ink)
                                            Text(u.email).font(.caption).foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                        if u.id == currentAgentId {
                                            Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Assign clearing agent")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        loading = true
        error = nil
        do {
            let users = try await ThapsusSdk.shared.adminRepo().listUsersByRole(
                role: "clearing_agent", limit: 100
            )
            self.agents = users
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}
