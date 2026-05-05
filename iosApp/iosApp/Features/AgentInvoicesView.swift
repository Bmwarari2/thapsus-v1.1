// AgentInvoicesView.swift
// Clearing-agent invoice submission and status list.

import SwiftUI
import ThapsusShared
import UniformTypeIdentifiers

struct AgentInvoicesView: View {
    @State private var vm: AgentInvoicesViewModel? = nil
    @State private var stateObs: StateFlowObserver<AgentInvoicesViewModelUiState>? = nil
    @State private var actionObs: StateFlowObserver<AgentInvoicesViewModelActionState>? = nil
    @State private var showCreate: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Clearing agent", systemImage: "doc.text.fill")
                EditorialHeader(title: "Invoices",
                                subtitle: "Submit invoices for the consolidations you cleared.")

                Button(action: { showCreate = true }) {
                    HStack(spacing: 8) {
                        Image(systemName: "plus.circle.fill")
                        Text("New invoice")
                    }
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

                actionBanner
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Invoices")
        .glassNavigationBar()
        .sheet(isPresented: $showCreate) {
            CreateAgentInvoiceSheet(
                vm: vm,
                actionObs: actionObs,
                onClose: { showCreate = false }
            )
        }
        .task { bootstrap() }
    }

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case let done as AgentInvoicesViewModelActionStateDone:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Done", message: done.message)
        case let err as AgentInvoicesViewModelActionStateError:
            ErrorBanner(title: "Couldn't submit", message: err.message)
        case is AgentInvoicesViewModelActionStateInFlight:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch stateObs?.value {
        case let loaded as AgentInvoicesViewModelUiStateLoaded:
            if loaded.invoices.isEmpty {
                CrystalCard {
                    Text("No invoices submitted yet.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            } else {
                ForEach(loaded.invoices, id: \.id) { invoice in
                    invoiceRow(invoice)
                }
            }
        case is AgentInvoicesViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
        case let err as AgentInvoicesViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func invoiceRow(_ invoice: AgentInvoiceDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(invoice.invoiceNo ?? invoice.id.prefix(8).description)
                        .font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    statusBadge(invoice.status)
                }
                HStack {
                    Text("Amount").font(.caption).foregroundStyle(.secondary)
                    Spacer()
                    Text("KES \(format(invoice.amountKes))")
                        .font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                }
                if let consolidationId = invoice.consolidationId {
                    Text("Consolidation: \(consolidationId)")
                        .font(.caption2).foregroundStyle(.secondary)
                }
                if invoice.docUrl != nil {
                    // Bucket is private as of migration 005 — public URLs no
                    // longer resolve. Always go through the server to mint a
                    // 5-minute signed URL on tap, then open it.
                    AgentInvoiceDocumentLink(invoiceId: invoice.id)
                }
            }
        }
    }

    private func statusBadge(_ status: String) -> some View {
        let normalised = status.lowercased()
        let known = ["submitted", "approved", "paid", "rejected"]
        let color: Color
        let label: String
        switch normalised {
        case "submitted": color = .orange; label = "SUBMITTED"
        case "approved":  color = .blue;   label = "APPROVED"
        case "paid":      color = .green;  label = "PAID"
        case "rejected":  color = .red;    label = "REJECTED"
        default:
            color = .gray
            label = known.contains(normalised) ? normalised.uppercased() : "—"
        }
        return Text(label)
            .font(.system(size: 9, weight: .heavy)).tracking(2)
            .foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Capsule().fill(color.opacity(0.16)))
    }

    private func format(_ amount: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal
        return f.string(from: NSNumber(value: amount)) ?? "\(Int(amount))"
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.agentInvoicesViewModel()
        vm = model
        model.load()
        stateObs = StateFlowObserver(initial: model.state.value) { model.state }
        actionObs = StateFlowObserver(initial: model.action.value) { model.action }
    }
}

/// Tappable "View document" affordance that fetches a 5-minute signed URL
/// from the server, then opens it in Safari / the default PDF handler. The
/// agent-invoices bucket is private (migration 005), so a stored doc_url
/// path can't be opened directly.
private struct AgentInvoiceDocumentLink: View {
    let invoiceId: String
    @State private var loading = false
    @State private var error: String?
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button {
                fetchAndOpen()
            } label: {
                if loading {
                    ProgressView().controlSize(.small).tint(Brand.orange)
                } else {
                    Text("View document")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Brand.orange)
                }
            }
            .disabled(loading)
            if let error {
                InlineFieldError(message: error)
            }
        }
    }

    private func fetchAndOpen() {
        loading = true
        error = nil
        Task {
            do {
                let resp = try await ThapsusSdk.shared.agentInvoices()
                    .requestDocumentUrl(invoiceId: invoiceId)
                if let url = URL(string: resp.signedUrl) {
                    await MainActor.run {
                        openURL(url)
                        loading = false
                    }
                } else {
                    await MainActor.run {
                        error = "Server returned an invalid URL"
                        loading = false
                    }
                }
            } catch {
                await MainActor.run {
                    self.error = "Couldn't open: \(error.localizedDescription)"
                    self.loading = false
                }
            }
        }
    }
}

private struct CreateAgentInvoiceSheet: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.dismiss) private var dismiss
    let vm: AgentInvoicesViewModel?
    let actionObs: StateFlowObserver<AgentInvoicesViewModelActionState>?
    let onClose: () -> Void

    @State private var consolidationId: String = ""
    @State private var invoiceNo: String = ""
    @State private var amount: String = ""
    /// In-bucket path returned by the signed-upload PUT. We store this (not
    /// the signed URL — that's a one-shot 5-minute thing) and pass it to
    /// the server as `doc_url`. If the user dismisses the sheet without
    /// submitting, or submit fails, we DELETE the orphan via
    /// `StorageRepository.deleteAgentInvoiceAsset` (audit M3).
    @State private var docPath: String = ""
    @State private var notes: String = ""
    @State private var pdfPickerOpen: Bool = false
    @State private var uploading: Bool = false
    /// True between tapping Submit and the action state resolving Done /
    /// Error. Disables the Submit button so a double-tap can't re-fire.
    @State private var submitting: Bool = false
    /// Set to true when the action state observed Done. We use it to skip
    /// the orphan cleanup in onDisappear (the path is now bound to a real
    /// invoice row).
    @State private var submitted: Bool = false
    @State private var pdfFileName: String?
    @State private var uploadError: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Invoice") {
                    TextField("Invoice no", text: $invoiceNo)
                    TextField("Amount (KES)", text: $amount).keyboardType(.decimalPad)
                }
                Section("Consolidation (optional)") {
                    TextField("Consolidation ID", text: $consolidationId)
                }
                Section("Document") {
                    documentRow
                    if let msg = uploadError {
                        InlineFieldError(message: msg)
                    }
                }
                Section("Notes") {
                    TextEditor(text: $notes).frame(minHeight: 100)
                }
            }
            .navigationTitle("New invoice")
            .fileImporter(
                isPresented: $pdfPickerOpen,
                allowedContentTypes: [UTType.pdf],
                allowsMultipleSelection: false
            ) { result in
                handlePicked(result: result)
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .disabled(submitting)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(submitting ? "Submitting…" : "Submit") {
                        submit()
                    }
                    .disabled((Double(amount) ?? 0) <= 0 || uploading || submitting)
                }
            }
            .interactiveDismissDisabled(submitting)
            // Track action state so we can dismiss on success and clean up
            // on failure. The parent view's banner still surfaces the result
            // — we just need to know which terminal state landed (M3).
            .onChange(of: actionStateKind) { _, kind in
                guard submitting else { return }
                switch kind {
                case "done":
                    submitting = false
                    submitted = true
                    onClose()
                    dismiss()
                case "error":
                    submitting = false
                    let stale = docPath
                    if !stale.isEmpty {
                        Task.detached(priority: .background) {
                            try? await ThapsusSdk.shared.storage().deleteAgentInvoiceAsset(path: stale)
                        }
                        docPath = ""
                        pdfFileName = nil
                    }
                default: break
                }
            }
            .onDisappear {
                // User dismissed without submitting (or before the request
                // resolved) — best-effort delete the orphan PDF so it doesn't
                // sit in the private bucket forever (M3).
                if !submitted, !docPath.isEmpty {
                    let stale = docPath
                    Task.detached(priority: .background) {
                        try? await ThapsusSdk.shared.storage().deleteAgentInvoiceAsset(path: stale)
                    }
                }
            }
        }
    }

    /// String key for the current action state so SwiftUI's onChange has a
    /// hashable value to compare. The SKIE-bridged sealed-interface cases
    /// don't conform to Equatable.
    private var actionStateKind: String {
        switch actionObs?.value {
        case is AgentInvoicesViewModelActionStateDone: return "done"
        case is AgentInvoicesViewModelActionStateError: return "error"
        case is AgentInvoicesViewModelActionStateInFlight: return "inflight"
        default: return "idle"
        }
    }

    private func submit() {
        guard let vm else { return }
        submitting = true
        vm.submit(
            consolidationId: consolidationId.isEmpty ? nil : consolidationId,
            invoiceNo: invoiceNo.isEmpty ? nil : invoiceNo,
            amountKes: Double(amount) ?? 0,
            docUrl: docPath.isEmpty ? nil : docPath,
            notes: notes.isEmpty ? nil : notes
        )
    }

    @ViewBuilder
    private var documentRow: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Invoice PDF").font(.subheadline)
                if uploading {
                    Text("Uploading…").font(.caption).foregroundStyle(.secondary)
                } else if !docPath.isEmpty, let name = pdfFileName {
                    Text(name).font(.caption).foregroundStyle(Brand.orange)
                } else if !docPath.isEmpty {
                    Text("Uploaded").font(.caption).foregroundStyle(Brand.orange)
                } else {
                    Text("No file selected").font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            Button(docPath.isEmpty ? "Choose PDF" : "Replace") {
                uploadError = nil
                pdfPickerOpen = true
            }
            .buttonStyle(.bordered)
            .disabled(uploading || submitting)
        }
    }

    private func handlePicked(result: Result<[URL], Error>) {
        switch result {
        case .failure(let err):
            uploadError = err.localizedDescription
        case .success(let urls):
            guard let url = urls.first else { return }
            uploadPdf(at: url)
        }
    }

    private func uploadPdf(at url: URL) {
        uploadError = nil
        // Auth is enforced by the server endpoint (clearing_agent only); the
        // client just needs to be signed in for the api call to carry an
        // Authorization header. Surface a clear error if not.
        guard env.currentUserID != nil else {
            uploadError = "You're signed out. Please sign in again to upload."
            return
        }
        // Replacing an already-uploaded PDF? Clean up the previous orphan
        // before we overwrite the docPath (otherwise the prior path leaks).
        if !docPath.isEmpty {
            let stale = docPath
            Task.detached(priority: .background) {
                try? await ThapsusSdk.shared.storage().deleteAgentInvoiceAsset(path: stale)
            }
        }
        uploading = true
        let needsScope = url.startAccessingSecurityScopedResource()
        let filename = url.lastPathComponent
        Task.detached(priority: .userInitiated) {
            defer { if needsScope { url.stopAccessingSecurityScopedResource() } }
            do {
                let data = try Data(contentsOf: url)
                let result = try await Self.uploadViaSignedUrl(filename: filename, data: data)
                await MainActor.run {
                    // Store the canonical bucket path (not the signed URL —
                    // that's a 5-minute one-shot). Server will re-issue
                    // download URLs on demand from this path.
                    self.docPath = result
                    self.pdfFileName = filename
                    self.uploading = false
                }
            } catch {
                await MainActor.run {
                    self.uploadError = "Couldn't upload: \(error.localizedDescription)"
                    self.uploading = false
                }
            }
        }
    }

    /// Two-step upload through the server-issued signed URL. Replaces the
    /// previous direct-Storage POST that 403'd against the 2026-04-30 RLS
    /// lockdown (memory: agent_invoice_pdf_upload_debug). Server endpoint is
    /// `POST /api/agent-invoices/upload-url` (Swiftcargo-main PR #35); it
    /// returns a 5-min signed URL we PUT the bytes to with no auth header.
    /// Returns the in-bucket path so the caller can pass it as `doc_url`.
    private static func uploadViaSignedUrl(filename: String, data: Data) async throws -> String {
        let signed = try await ThapsusSdk.shared.agentInvoices().requestUploadUrl(filename: filename)
        guard let target = URL(string: signed.signedUrl) else {
            throw NSError(domain: "AgentInvoiceUpload", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Server returned an invalid signed URL"
            ])
        }
        var req = URLRequest(url: target)
        req.httpMethod = "PUT"
        req.addValue("application/pdf", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 60
        let (respData, resp) = try await URLSession.shared.upload(for: req, from: data)
        guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let code = (resp as? HTTPURLResponse)?.statusCode ?? -1
            let body = String(data: respData, encoding: .utf8) ?? "<no body>"
            throw NSError(domain: "AgentInvoiceUpload", code: code, userInfo: [
                NSLocalizedDescriptionKey: "Signed-PUT \(code): \(body.prefix(200))"
            ])
        }
        return signed.path
    }
}
