// CustomsListView.swift
// Spec §3.4 /partner/agent + §4.5. Clearing-agent landing screen. Lists
// consolidations assigned to the agent; tapping one opens the entry form.

import SwiftUI
import ThapsusShared
import UniformTypeIdentifiers

struct CustomsListView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var vm: CustomsAgentViewModel?
    @State private var consolidations: StateFlowObserver<[ConsolidationDto]>?
    @State private var entries: StateFlowObserver<[CustomsEntryDto]>?
    @State private var selectedId: StateFlowObserver<String?>?
    @State private var errorObs: StateFlowObserver<String?>?
    @State private var parcels: StateFlowObserver<[AgentParcelRow]>?

    @State private var presentingEntryForParcel: PackageDto?
    @State private var showNewEntry: Bool = false

    var body: some View {
        ScrollView {
            GlassEffectContainer(spacing: 16) {
                VStack(spacing: 16) {
                    SectionHeader(
                        title: "Customs",
                        subtitle: "Submit IDF and KRA entry numbers"
                    )

                    if let msg = errorObs?.value, !msg.isEmpty {
                        ErrorBanner(title: "Couldn't load", message: msg)
                            .onTapGesture { vm?.dismissError() }
                    }
                    consolidationPicker
                    if selectedId?.value != nil {
                        Button(action: { showNewEntry = true }) {
                            HStack(spacing: 8) {
                                Image(systemName: "plus.circle.fill")
                                Text("New customs entry")
                            }
                        }
                        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                    }
                    entriesList
                }
                .padding(20)
            }
        }
        .navigationTitle("Customs")
        .glassNavigationBar()
        .sheet(isPresented: $showNewEntry) {
            // Only parcels without an existing customs entry — the DB has a
            // UNIQUE(parcel_id) on customs_entries (migration 021).
            NewCustomsEntrySheet(
                parcels: (parcels?.value ?? []).filter { $0.entryId == nil },
                onSubmitParcel: { parcelId, idf, entry, cif, duty, vat, idfFee, rdl, docUrl in
                    vm?.submitEntry(
                        parcelId: parcelId,
                        idfNo: idf,
                        entryNo: entry,
                        cifKes: cif,
                        dutyKes: duty,
                        vatKes: vat,
                        idfKes: idfFee,
                        rdlKes: rdl,
                        docUrl: docUrl
                    )
                    showNewEntry = false
                },
                onSubmitCustomerBundle: { userId, idf, entry, cif, duty, vat, idfFee, rdl, docUrl in
                    vm?.submitBulkEntries(
                        userId: userId,
                        idfNo: idf,
                        entryNo: entry,
                        cifKes: cif,
                        dutyKes: duty,
                        vatKes: vat,
                        idfKes: idfFee,
                        rdlKes: rdl,
                        docUrl: docUrl
                    )
                    showNewEntry = false
                }
            )
        }
        .task(id: env.currentUserID) {
            guard vm == nil else { return }
            // Customs entries are scoped to the signed-in clearing agent's
            // user id (writes go through Express which checks role + identity).
            // RootTabView only routes here for an authenticated agent, so
            // currentUserID being nil here means the session expired between
            // tab render and this task firing — defer rather than stamp
            // entries with a literal "agent" string.
            guard let agentId = env.currentUserID else { return }
            let v = ThapsusSdk.shared.customsAgentViewModel(agentId: agentId)
            self.vm = v
            self.consolidations = StateFlowObserver(initial: []) { v.assignedConsolidations }
            self.entries = StateFlowObserver(initial: []) { v.entries }
            self.selectedId = StateFlowObserver(initial: nil) { v.selectedId }
            self.errorObs = StateFlowObserver(initial: nil) { v.error }
            self.parcels = StateFlowObserver(initial: []) { v.parcels }
        }
        .onDisappear {
            vm?.clear(); vm = nil
            consolidations = nil; entries = nil; selectedId = nil; errorObs = nil; parcels = nil
        }
    }

    @ViewBuilder
    private var consolidationPicker: some View {
        let rows = consolidations?.value ?? []
        VStack(alignment: .leading, spacing: 8) {
            Text("Assigned consolidations").font(.headline).padding(.horizontal, 4)
            if rows.isEmpty {
                GlassCard {
                    Text("No consolidations assigned to you yet.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            } else {
                ForEach(rows, id: \.id) { c in
                    Button {
                        vm?.select(consolidationId: c.id)
                    } label: {
                        GlassCard(tint: selectedId?.value == c.id ? Brand.gold.opacity(0.2) : nil) {
                            HStack {
                                Text("Week \(weekLabel(c.weekStart))").font(.headline)
                                Spacer()
                                if let awb = c.masterAwbNo { Text("AWB \(awb)").font(.caption).foregroundStyle(.secondary) }
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private var entriesList: some View {
        if let list = entries?.value, !list.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("Submitted entries").font(.headline).padding(.horizontal, 4)
                ForEach(list, id: \.id) { e in
                    GlassCard {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Parcel \(e.parcelId.prefix(8))").font(.subheadline.weight(.semibold))
                            HStack(spacing: 12) {
                                if let idf = e.idfNo { Text("IDF \(idf)").font(.caption.monospaced()) }
                                if let entry = e.entryNo { Text("Entry \(entry)").font(.caption.monospaced()) }
                            }
                            HStack {
                                Text(String(format: "Duty KES %.0f", e.dutyKes?.doubleValue ?? 0))
                                    .font(.caption)
                                Spacer()
                                Text(String(format: "VAT KES %.0f", e.vatKes?.doubleValue ?? 0))
                                    .font(.caption)
                            }
                            .foregroundStyle(.secondary)
                            GlassChip(title: friendly(e.status), systemImage: "checkmark.shield")
                            statusActions(for: e)
                        }
                    }
                }
            }
        }
    }

    /// Forward-only status progression buttons. Each step is gated on the
    /// current status so the agent can't skip from `entry_filed` straight
    /// to `released`. Audit P2.1: marking an entry `released` no longer
    /// flips orders.status to out_for_delivery — the parcel stays at
    /// `customs` until an operator schedules a run (which is when
    /// activateRunDispatch flips it + mints the POD OTP).
    @ViewBuilder
    private func statusActions(for e: CustomsEntryDto) -> some View {
        let next: (label: String, status: CustomsStatus, tint: Color)? = {
            switch e.status {
            case .idfSubmitted: return ("Mark entry filed", .entryFiled, Brand.orange)
            case .entryFiled:   return ("Mark duty assessed", .dutyAssessed, Brand.orange)
            case .dutyAssessed: return ("Mark duty paid", .dutyPaid, .green)
            case .dutyPaid:     return ("Mark released",   .released, .green)
            default:            return nil
            }
        }()
        if let next {
            HStack {
                Spacer()
                Button(next.label) {
                    vm?.advanceEntryStatus(entryId: e.id, newStatus: next.status)
                }
                .font(.caption.weight(.semibold))
                .buttonStyle(.bordered)
                .tint(next.tint)
            }
        }
    }

    /// Server returns ISO timestamps for `week_start`. Render just the date.
    private func weekLabel(_ raw: String) -> String {
        let head = raw.prefix(10)
        return head.count == 10 ? String(head) : raw
    }

    private func friendly(_ s: CustomsStatus) -> String {
        switch s {
        case .preAlert: return "Pre-alert"
        case .idfSubmitted: return "IDF submitted"
        case .entryFiled: return "Entry filed"
        case .dutyAssessed: return "Duty assessed"
        case .dutyPaid: return "Duty paid"
        case .released: return "Released"
        case .rejected: return "Rejected"
        @unknown default: return "—"
        }
    }
}

private struct NewCustomsEntrySheet: View {
    @Environment(\.dismiss) private var dismiss
    let parcels: [AgentParcelRow]
    /// onSubmit args: parcelId, idf, entry, cif, duty, vat, idfFee, rdl, docUrl?.
    /// KES amounts are full units (not cents). docUrl is the in-bucket path
    /// returned by the signed-upload flow; nil if the agent didn't attach one.
    let onSubmitParcel: (String, String, String, Double, Double, Double, Double, Double, String?) -> Void
    /// Customer-bundle submit. Args: userId, idf, entry, cif, duty, vat,
    /// idfFee, rdl, docUrl. Server fans out the totals across every
    /// un-entered parcel for that customer in the consolidation.
    let onSubmitCustomerBundle: (String, String, String, Double, Double, Double, Double, Double, String?) -> Void

    /// Two filing modes. "By parcel" is the legacy 1-form-per-parcel flow.
    /// "By customer" hits the bulk endpoint with a single set of duty +
    /// VAT figures that the server splits across the customer's parcels.
    enum Mode: String, CaseIterable, Identifiable {
        case parcel
        case customer
        var id: String { rawValue }
        var label: String { self == .parcel ? "By parcel" : "By customer" }
    }

    @State private var mode: Mode = .customer
    @State private var parcelId: String = ""
    @State private var customerUserId: String = ""
    @State private var idfNo: String = ""
    @State private var entryNo: String = ""
    @State private var cif: String = ""
    @State private var duty: String = ""
    @State private var vat: String = ""
    @State private var idfFee: String = ""
    @State private var rdl: String = ""
    @State private var docUrl: String = ""
    @State private var docFileName: String?
    @State private var docPickerOpen: Bool = false
    @State private var uploading: Bool = false
    @State private var uploadError: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Mode") {
                    Picker("Filing mode", selection: $mode) {
                        ForEach(Mode.allCases) { m in Text(m.label).tag(m) }
                    }
                    .pickerStyle(.segmented)
                    Text(mode == .customer
                         ? "Files one set of charges across every un-entered parcel for the chosen customer in this consolidation. Duty/VAT/CIF are split server-side by chargeable weight."
                         : "Files a single customs entry against one parcel.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Section(mode == .customer ? "Customer" : "Parcel") {
                    if parcels.isEmpty {
                        Text("No parcels in this consolidation yet.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    } else if mode == .customer {
                        Picker("Customer", selection: $customerUserId) {
                            Text("Choose a customer…").tag("")
                            ForEach(customerGroups, id: \.userId) { g in
                                Text(customerLabel(group: g)).tag(g.userId)
                            }
                        }
                        if !customerUserId.isEmpty,
                           let g = customerGroups.first(where: { $0.userId == customerUserId }) {
                            Text("\(g.parcels.count) un-entered parcels: " +
                                 g.parcels.compactMap { $0.trackingNumber ?? String($0.id.prefix(8)) }.joined(separator: ", "))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    } else {
                        Picker("Parcel", selection: $parcelId) {
                            Text("Choose a parcel…").tag("")
                            ForEach(parcels, id: \.id) { p in
                                Text(parcelLabel(p)).tag(p.id)
                            }
                        }
                    }
                }
                Section("Documents") {
                    TextField("IDF number", text: $idfNo)
                    TextField("KRA entry number", text: $entryNo)
                }
                Section("Attachment") {
                    documentRow
                    if let msg = uploadError {
                        InlineFieldError(message: msg)
                    }
                }
                Section("Charges (KES)") {
                    HStack { Text("CIF"); Spacer(); TextField("0", text: $cif).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                    HStack { Text("Duty"); Spacer(); TextField("0", text: $duty).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                    HStack { Text("VAT"); Spacer(); TextField("0", text: $vat).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                    HStack { Text("IDF fee"); Spacer(); TextField("0", text: $idfFee).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                    HStack { Text("RDL"); Spacer(); TextField("0", text: $rdl).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                }
            }
            .navigationTitle("Customs entry")
            .fileImporter(
                isPresented: $docPickerOpen,
                allowedContentTypes: [UTType.pdf, UTType.image],
                allowsMultipleSelection: false
            ) { result in handlePicked(result: result) }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("File") {
                        let cifD  = Double(cif) ?? 0
                        let dutyD = Double(duty) ?? 0
                        let vatD  = Double(vat) ?? 0
                        let idfD  = Double(idfFee) ?? 0
                        let rdlD  = Double(rdl) ?? 0
                        let attachment = docUrl.isEmpty ? nil : docUrl
                        if mode == .customer {
                            onSubmitCustomerBundle(
                                customerUserId, idfNo, entryNo,
                                cifD, dutyD, vatD, idfD, rdlD, attachment
                            )
                        } else {
                            onSubmitParcel(
                                parcelId, idfNo, entryNo,
                                cifD, dutyD, vatD, idfD, rdlD, attachment
                            )
                        }
                    }
                    .disabled(submitDisabled)
                }
            }
        }
    }

    private var submitDisabled: Bool {
        if uploading || idfNo.isEmpty || entryNo.isEmpty { return true }
        return mode == .customer ? customerUserId.isEmpty : parcelId.isEmpty
    }

    /// A single customer's un-entered parcels in this consolidation.
    private struct CustomerGroup {
        let userId: String
        let consigneeName: String?
        let parcels: [AgentParcelRow]
    }

    /// Group the un-entered parcels by their owning customer. Skips any
    /// rows the server didn't stamp with a user_id (defensive — every
    /// production row has one, but a stale client serialisation could
    /// drop it).
    private var customerGroups: [CustomerGroup] {
        let withUser = parcels.compactMap { p -> (String, AgentParcelRow)? in
            guard let uid = p.userId, !uid.isEmpty else { return nil }
            return (uid, p)
        }
        let byUser = Dictionary(grouping: withUser, by: { $0.0 })
        return byUser
            .map { (uid, items) in
                CustomerGroup(
                    userId: uid,
                    consigneeName: items.first?.1.consigneeName,
                    parcels: items.map { $0.1 }
                )
            }
            .sorted { ($0.consigneeName ?? "") < ($1.consigneeName ?? "") }
    }

    private func customerLabel(group g: CustomerGroup) -> String {
        let name = g.consigneeName?.isEmpty == false ? g.consigneeName! : String(g.userId.prefix(8))
        return "\(name) — \(g.parcels.count) parcel\(g.parcels.count == 1 ? "" : "s")"
    }

    @ViewBuilder
    private var documentRow: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("IDF / KRA document").font(.subheadline)
                if uploading {
                    Text("Uploading…").font(.caption).foregroundStyle(.secondary)
                } else if !docUrl.isEmpty, let name = docFileName {
                    Text(name).font(.caption).foregroundStyle(Brand.orange)
                } else if !docUrl.isEmpty {
                    Text("Uploaded").font(.caption).foregroundStyle(Brand.orange)
                } else {
                    Text("Optional. PDF or photo.").font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            Button(docUrl.isEmpty ? "Choose…" : "Replace") {
                uploadError = nil
                docPickerOpen = true
            }
            .buttonStyle(.bordered)
            .disabled(uploading)
        }
    }

    private func handlePicked(result: Result<[URL], Error>) {
        switch result {
        case .failure(let err):
            uploadError = err.localizedDescription
        case .success(let urls):
            guard let url = urls.first else { return }
            uploadDoc(at: url)
        }
    }

    private func uploadDoc(at url: URL) {
        uploadError = nil
        uploading = true
        let needsScope = url.startAccessingSecurityScopedResource()
        let filename = url.lastPathComponent
        Task.detached(priority: .userInitiated) {
            defer { if needsScope { url.stopAccessingSecurityScopedResource() } }
            do {
                let data = try Data(contentsOf: url)
                // Reuse the agent-invoices signed-upload endpoint — same
                // private bucket, already scoped to clearing-agent only.
                let signed = try await ThapsusSdk.shared.customs()
                    .requestEntryDocUploadUrl(filename: filename)
                guard let target = URL(string: signed.signedUrl) else {
                    throw NSError(domain: "CustomsDocUpload", code: 1, userInfo: [
                        NSLocalizedDescriptionKey: "Invalid signed URL"
                    ])
                }
                var req = URLRequest(url: target)
                req.httpMethod = "PUT"
                req.addValue(url.pathExtension.lowercased() == "pdf" ? "application/pdf" : "application/octet-stream",
                             forHTTPHeaderField: "Content-Type")
                req.timeoutInterval = 60
                let (respData, resp) = try await URLSession.shared.upload(for: req, from: data)
                guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                    let body = String(data: respData, encoding: .utf8) ?? "<no body>"
                    let code = (resp as? HTTPURLResponse)?.statusCode ?? -1
                    throw NSError(domain: "CustomsDocUpload", code: code, userInfo: [
                        NSLocalizedDescriptionKey: "Signed-PUT \(code): \(body.prefix(200))"
                    ])
                }
                await MainActor.run {
                    docUrl = signed.path
                    docFileName = filename
                    uploading = false
                }
            } catch {
                await MainActor.run {
                    uploadError = "Couldn't upload: \(error.localizedDescription)"
                    uploading = false
                }
            }
        }
    }

    private func parcelLabel(_ p: AgentParcelRow) -> String {
        let head = p.trackingNumber ?? String(p.id.prefix(8))
        let consignee = p.consigneeName.map { " — \($0)" } ?? ""
        return head + consignee
    }
}
