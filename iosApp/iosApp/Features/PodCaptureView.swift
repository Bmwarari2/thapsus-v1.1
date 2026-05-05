// PodCaptureView.swift
// Capture POD: photo + recipient OTP + signature. Spec §4.6. Photo is shot via
// CameraPickerView, uploaded to the private `pods` bucket via a server-issued
// signed upload URL, and the resulting in-bucket *path* lands on the queued
// PodEventDto. Admin / staff views mint a signed download URL on demand from
// the path (audit B1) — the bucket has no public route.

import SwiftUI
import ThapsusShared
import UIKit

struct PodCaptureView: View {
    let run: LastMileRunDto
    /// Phase 3 — every parcel covered by this single capture. Pass a
    /// one-element array for single-parcel deliveries; pass the full
    /// user-grouped list for bundled deliveries (one photo + signature +
    /// OTP covers them all). The first id is the representative for the
    /// outbox event's `parcel_id` field.
    let parcelIds: [String]

    /// Convenience initialiser for legacy single-parcel call sites.
    init(run: LastMileRunDto, parcelId: String) {
        self.run = run
        self.parcelIds = [parcelId]
    }

    /// Phase 3 multi-parcel initialiser — used by the user-grouped
    /// stop list to pass every parcel for one recipient in one capture.
    init(run: LastMileRunDto, parcelIds: [String]) {
        self.run = run
        self.parcelIds = parcelIds
    }

    /// First parcel id — used for upload paths (photo / signature go into
    /// pod/<parcelId>/<ts>.jpg) and as the representative for the outbox
    /// event. Server fans out the capture across every id in the array.
    private var parcelId: String { parcelIds.first ?? "" }

    @Environment(\.dismiss) private var dismiss
    @Environment(AppEnvironment.self) private var env

    @State private var otp: String = ""
    @State private var recipientName: String = ""
    @State private var notes: String = ""
    @State private var photo: UIImage?
    /// Bucket path returned by the server-issued upload URL — persisted on
    /// the PodEventDto. Admin / staff views mint signed download URLs from
    /// this on demand (audit B1).
    @State private var photoPath: String?
    @State private var uploading: Bool = false
    @State private var presentingCamera = false
    @State private var cameraDenied: Bool = false
    @State private var presentingContacts = false
    @State private var presentingSignature = false
    @State private var presentingFail = false
    /// See `photoPath` — same shape for the signature PNG.
    @State private var signaturePath: String?
    @State private var signatureUploading: Bool = false
    @State private var saved = false
    @State private var captureError: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                EditorialHeader(eyebrow: "Last mile", title: "Proof of delivery", subtitle: run.zone())

                photoCard

                signatureCard

                SoftCard {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 8) {
                            inkField("Recipient name", text: $recipientName)
                            Button {
                                presentingContacts = true
                            } label: {
                                Image(systemName: "person.crop.circle.badge.plus")
                                    .font(.title3)
                                    .foregroundStyle(Brand.orange)
                                    .frame(width: 44, height: 44)
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

                        TextField("4-digit OTP", text: $otp)
                            .keyboardType(.numberPad)
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

                        TextField("Notes (optional)", text: $notes, axis: .vertical)
                            .lineLimit(2...4)
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

                Button(action: capture) {
                    if uploading || signatureUploading {
                        HStack { ProgressView().tint(Brand.cream); Text("Saving…") }
                    } else if saved {
                        Label("Queued for sync", systemImage: "checkmark")
                    } else {
                        Text("Capture POD")
                    }
                }
                .buttonStyle(InkButtonStyle())
                // Stay tappable even when the form is incomplete — gating with
                // .disabled(!isValid) made the button silently swallow taps and
                // riders had no idea WHAT was missing. Only block while an
                // upload is in flight (so we don't persist a POD without the
                // signaturePath the user is still drawing). The capture()
                // function performs the same field check and surfaces an
                // inline error message when something's missing.
                .disabled(uploading || signatureUploading)

                Button {
                    presentingFail = true
                } label: {
                    Label("Couldn't deliver", systemImage: "xmark.octagon")
                }
                .buttonStyle(.bordered)
                .tint(.red)
                .disabled(uploading || signatureUploading || saved)

                if let captureError {
                    ErrorBanner(title: "Couldn't save", message: captureError)
                }
            }
            .padding(20)
        }
        .navigationTitle("Deliver")
        .glassNavigationBar(displayMode: .inline)
        .scrollContentBackground(.hidden)
        .appBackground()
        .sheet(isPresented: $presentingContacts) {
            ContactPickerView { name, _ in
                recipientName = name ?? recipientName
                presentingContacts = false
            }
            .ignoresSafeArea()
        }
        .sheet(isPresented: $presentingCamera) {
            CameraPickerView(source: .camera) { data, failure in
                presentingCamera = false
                if let failure {
                    cameraDenied = (failure == .cameraAccessDenied)
                    return
                }
                guard let data, let img = UIImage(data: data) else { return }
                photo = img
                Task { await uploadPhoto(data: data) }
            }
            .ignoresSafeArea()
        }
        .sheet(isPresented: $presentingSignature) {
            SignaturePadView { data in
                Task { await uploadSignature(data: data) }
            }
        }
        .sheet(isPresented: $presentingFail) {
            FailDeliverySheet(
                run: run,
                parcelId: parcelId,
                photoPath: photoPath,
                onDone: { msg in
                    presentingFail = false
                    if let msg { captureError = msg } else {
                        saved = true
                        Task {
                            try? await Task.sleep(for: .milliseconds(600))
                            await MainActor.run { dismiss() }
                        }
                    }
                }
            )
        }
    }

    private var isValid: Bool {
        // Require recipient + photo + (OTP or signature). Either acceptance
        // signal is enough — high-value parcels get the signature; everyday
        // ones can clear with the 4-digit code the back office texts to the
        // recipient.
        let hasAccept = otp.count == 4 || signaturePath != nil
        return hasAccept && !recipientName.isEmpty && photo != nil
    }

    @ViewBuilder
    private var photoCard: some View {
        SoftCard {
            VStack(spacing: 12) {
                if let photo {
                    Image(uiImage: photo)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 220)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                } else {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Brand.cream.opacity(0.6))
                        .frame(height: 220)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .stroke(Brand.ink.opacity(0.08), lineWidth: 1)
                        )
                        .overlay {
                            VStack(spacing: 8) {
                                Image(systemName: "camera.aperture")
                                    .font(.largeTitle)
                                    .foregroundStyle(Brand.orange)
                                Text("Photo required").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                }
                if uploading {
                    HStack { ProgressView().tint(Brand.ink); Text("Uploading…").font(.caption) }
                } else if photoPath != nil {
                    Label("Uploaded", systemImage: "checkmark.icloud.fill")
                        .font(.caption)
                        .foregroundStyle(.green)
                }
                if cameraDenied {
                    CalloutBanner(
                        icon: "camera.badge.ellipsis",
                        title: "Camera access denied",
                        message: "Open Settings to enable camera access for Thapsus."
                    )
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                    .buttonStyle(.bordered)
                }
                Button("Take photo") { presentingCamera = true }
                    .buttonStyle(OrangeButtonStyle())
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

    private func uploadPhoto(data: Data) async {
        uploading = true
        defer { uploading = false }
        do {
            let path = try await Self.uploadViaSignedUrl(
                parcelId: parcelId,
                kind: "photo",
                contentType: "image/jpeg",
                data: data
            )
            photoPath = path
            captureError = nil
        } catch {
            // Step context (audit N2): rider sees the banner without the
            // sheet that just dismissed, so the message has to say which
            // step failed — otherwise "Upload rejected (HTTP 403)" is
            // ambiguous between the photo and signature paths.
            captureError = "Couldn't upload POD photo: \(error.localizedDescription)"
        }
    }

    private func uploadSignature(data: Data) async {
        await MainActor.run { signatureUploading = true }
        do {
            let path = try await Self.uploadViaSignedUrl(
                parcelId: parcelId,
                kind: "signature",
                contentType: "image/png",
                data: data
            )
            await MainActor.run {
                signaturePath = path
                signatureUploading = false
                presentingSignature = false
                captureError = nil
            }
        } catch {
            await MainActor.run {
                signatureUploading = false
                presentingSignature = false
                // Step context (audit N2): the signature sheet has just
                // dismissed when this banner shows, so the rider needs to
                // know which step blew up. Plain `error.localizedDescription`
                // ("Upload rejected") doesn't disambiguate from a photo
                // failure that would surface the same way.
                captureError = "Couldn't upload signature: \(error.localizedDescription)"
            }
        }
    }

    /// Asks the server for a signed upload URL, then PUTs the bytes directly
    /// via URLSession.  Bypasses the K/N byte-by-byte ByteArray bridge that
    /// froze the app on multi-MB photos (audit D19).
    ///
    /// Returns the in-bucket *path* to persist on the PodEventDto. The bucket
    /// is private (audit B1) so a public URL doesn't resolve — admin views
    /// exchange the path for a fresh signed download URL on demand.
    fileprivate static func uploadViaSignedUrl(
        parcelId: String,
        kind: String,
        contentType: String,
        data: Data
    ) async throws -> String {
        let resp = try await ThapsusSdk.shared.lastMile()
            .requestPodUploadUrl(parcelId: parcelId, kind: kind)
        guard let signed = resp.signedUrl, let url = URL(string: signed) else {
            throw NSError(
                domain: "PodCaptureView",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Server did not return a signed URL"]
            )
        }
        guard let path = resp.path, !path.isEmpty else {
            throw NSError(
                domain: "PodCaptureView",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Server did not return a bucket path"]
            )
        }
        var req = URLRequest(url: url)
        req.httpMethod = "PUT"
        req.setValue(contentType, forHTTPHeaderField: "Content-Type")
        req.setValue("3600", forHTTPHeaderField: "Cache-Control")
        let (_, httpResp) = try await URLSession.shared.upload(for: req, from: data)
        if let http = httpResp as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw NSError(
                domain: "PodCaptureView",
                code: http.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "Upload rejected (HTTP \(http.statusCode))"]
            )
        }
        return path
    }

    @ViewBuilder
    private var signatureCard: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "signature").foregroundStyle(Brand.orange)
                    Text("Recipient signature").font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    if signatureUploading {
                        ProgressView().tint(Brand.ink)
                    } else if signaturePath != nil {
                        Label("Captured", systemImage: "checkmark.icloud.fill")
                            .font(.caption).foregroundStyle(.green)
                    }
                }
                Text("Optional alternative to the OTP for high-value parcels.")
                    .font(.caption).foregroundStyle(.secondary)
                Button(signaturePath == nil ? "Capture signature" : "Re-capture") {
                    presentingSignature = true
                }
                .buttonStyle(OrangeButtonStyle())
            }
        }
    }

    private func capture() {
        // Pre-flight checks: surface exactly what's missing so the rider
        // doesn't sit there tapping a button that does nothing. Mirrors the
        // server's POD acceptance gates (audit G2): photo + recipient +
        // (OTP or signature).
        var missing: [String] = []
        if recipientName.trimmingCharacters(in: .whitespaces).isEmpty {
            missing.append("recipient name")
        }
        if photoPath == nil {
            missing.append("photo upload (capture, then wait for ✓ Uploaded)")
        }
        if otp.count != 4 && signaturePath == nil {
            missing.append("4-digit OTP or signature")
        }
        if !missing.isEmpty {
            captureError = "Add: " + missing.joined(separator: ", ") + "."
            return
        }

        let pid = parcelId
        // Phase 3 — full id list when this capture covers a user-group.
        // Falls back to a single-element list for the legacy single-parcel
        // call site so the wire payload is uniform.
        let pids = parcelIds.isEmpty ? [pid] : parcelIds
        let rid = run.id
        let timestamp = ISO8601DateFormatter().string(from: Date())
        let photo = photoPath
        let otpValue = otp
        let recipient = recipientName
        let notesValue: String? = notes.isEmpty ? nil : notes
        // riderId / capturedBy stamp the POD audit trail. If the session has
        // expired between opening this sheet and tapping capture, refuse the
        // save rather than attribute it to a literal "rider" id which fails
        // any downstream "did this rider deliver this parcel" query.
        guard let actor = env.currentUserID else {
            saved = false
            captureError = "Your session has expired. Sign in again before capturing POD."
            // Best-effort cleanup so the orphaned uploads don't sit in the
            // pods bucket forever (audit T19). The capture is bailing because
            // we have no rider to attribute the POD to, but the photo and
            // signature were already pushed to Supabase by the time the user
            // tapped "Capture POD".
            let stalePaths = [photoPath, signaturePath].compactMap { $0 }
            Task.detached(priority: .background) {
                let storage = ThapsusSdk.shared.storage()
                for path in stalePaths {
                    try? await storage.deletePodAsset(path: path)
                }
            }
            photoPath = nil
            signaturePath = nil
            return
        }

        let signature = signaturePath

        Task {
            let event = PodEventDto(
                id: UUID().uuidString,
                parcelId: pid,
                // Phase 3 — full id list of every parcel covered by this
                // capture. Server fans out N pod_events rows + N status
                // flips inside one transaction, sharing the same photo,
                // signature, and OTP.
                parcelIds: pids,
                runId: rid,
                capturedAt: timestamp,
                // Send paths only — server prefers paths over URLs and the
                // bucket is private so URLs would be stale by the time admin
                // views render them anyway (audit B1).
                photoUrl: nil,
                signatureUrl: nil,
                photoPath: photo,
                signaturePath: signature,
                otpUsed: otpValue,
                recipientName: recipient,
                recipientPhone: nil,
                result: "delivered",
                riderId: actor,
                notes: notesValue,
                latitude: nil,
                longitude: nil,
                capturedBy: actor
            )
            // capturePod returns a Result via SKIE — surface a real error to
            // the user if the outbox enqueue fails (e.g. cache lock, disk
            // full). Previously the failure was swallowed with `try?` and
            // the rider saw "Queued for sync" on a write that never happened.
            do {
                _ = try await ThapsusSdk.shared.lastMile().capturePod(event: event)
                // capturePod only enqueues into the outbox — nothing
                // auto-flushes the queue, so without this push the parcel
                // sits as "Queued" and never flips to delivered until the
                // rider opens the Outbox tab and hits Flush. Awaiting the
                // flush also means a 4xx (OTP mismatch, duplicate POD)
                // gets recorded as an outbox failure before we dismiss,
                // so the inline retry banner can surface it.
                _ = try? await ThapsusSdk.shared.lastMile().flushOutbox(maxBatch: 20)
                // Inspect the outbox failures table for THIS parcel right
                // after the flush. If the server rejected the POD with a
                // 4xx (OTP not issued, parcel not on run, duplicate), the
                // outbox worker swallowed the throw and recorded the
                // failure — the previous code path didn't notice and
                // showed "Queued for sync" on a write that never landed.
                // Audit G2: surface the real reason.
                let failures = ThapsusSdk.shared.lastMile().outboxFailuresForParcel(parcelId: pid)
                if let firstFailure = failures.first {
                    let serverMsg = firstFailure.errorMessage ?? "Server rejected POD (HTTP \(firstFailure.errorStatus))."
                    await MainActor.run {
                        saved = false
                        captureError = serverMsg
                    }
                    return
                }
                await MainActor.run { saved = true; captureError = nil }
                try? await Task.sleep(for: .milliseconds(600))
                await MainActor.run { dismiss() }
            } catch {
                // Outbox enqueue failed — the bytes already landed in the
                // private pods bucket but no PodEventDto will ever flush
                // for them. Best-effort cleanup so we don't accumulate
                // orphans, then reset the path state so a retry re-uploads
                // (audit M1).
                let stalePaths = [photo, signature].compactMap { $0 }
                Task.detached(priority: .background) {
                    let storage = ThapsusSdk.shared.storage()
                    for path in stalePaths {
                        try? await storage.deletePodAsset(path: path)
                    }
                }
                await MainActor.run {
                    photoPath = nil
                    signaturePath = nil
                    captureError = "Couldn't save POD locally — please retry."
                }
            }
        }
    }
}

/// "Couldn't deliver" capture sheet. Lets the rider pick a reason ("recipient
/// absent", "refused", "wrong address", "damaged"), optionally attach a free-
/// text note, and enqueues a `PodEventDto` with `result: "failed"` so the
/// outbox flush hits `/last-mile/rider/runs/:runId/fail`. The server applies
/// the two-fails-then-hold rule and returns the parcel to the queue.
private struct FailDeliverySheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppEnvironment.self) private var env

    let run: LastMileRunDto
    let parcelId: String
    /// In-bucket path for an already-uploaded delivery photo (if any). Stored
    /// as a path post-B1 because the pods bucket is private; admin views
    /// mint signed download URLs on demand.
    let photoPath: String?
    /// nil on success, a message on failure.
    let onDone: (String?) -> Void

    @State private var reason: String = "recipient_absent"
    @State private var note: String = ""
    @State private var submitting = false
    @State private var inlineError: String?

    private let reasons: [(value: String, label: String)] = [
        ("recipient_absent", "Recipient not at address"),
        ("refused", "Recipient refused parcel"),
        ("wrong_address", "Address is wrong / can't find"),
        ("damaged", "Parcel damaged in transit"),
        ("other", "Other (see note)")
    ]

    var body: some View {
        NavigationStack {
            Form {
                Section("What happened?") {
                    Picker("Reason", selection: $reason) {
                        ForEach(reasons, id: \.value) { r in
                            Text(r.label).tag(r.value)
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                Section("Note (optional)") {
                    TextField("Anything ops should know", text: $note, axis: .vertical)
                        .lineLimit(2...5)
                }
                if let inlineError {
                    Section {
                        Label(inlineError, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Couldn't deliver")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(submitting ? "Saving…" : "Submit") { submit() }
                        .disabled(submitting)
                }
            }
        }
    }

    private func submit() {
        // Stamping the audit row with the actual rider id matters for the
        // two-fails-then-hold rule (server counts failed PODs by rider).
        guard let actor = env.currentUserID else {
            inlineError = "You're signed out. Please sign in again."
            return
        }
        submitting = true
        let pid = parcelId
        let rid = run.id
        let timestamp = ISO8601DateFormatter().string(from: Date())
        // Server reads the reason from `notes`; pack the chosen reason key plus
        // any free-text into a single string the ops queue can grep on.
        let chosenReason = reasons.first(where: { $0.value == reason })?.label ?? reason
        let body = note.isEmpty ? chosenReason : "\(chosenReason): \(note)"

        Task {
            let event = PodEventDto(
                id: UUID().uuidString,
                parcelId: pid,
                // Fail-delivery is single-parcel by design — pass an empty
                // array so the server falls back to the singleton parcelId.
                parcelIds: [],
                runId: rid,
                capturedAt: timestamp,
                photoUrl: nil,
                signatureUrl: nil,
                photoPath: photoPath,
                signaturePath: nil,
                otpUsed: nil,
                recipientName: nil,
                recipientPhone: nil,
                result: "failed",
                riderId: actor,
                notes: body,
                latitude: nil,
                longitude: nil,
                capturedBy: actor
            )
            do {
                _ = try await ThapsusSdk.shared.lastMile().capturePod(event: event)
                // Same as the success path: enqueue alone won't drain
                // the outbox, so push immediately. The two-fails-then-hold
                // server rule won't apply until the POST actually lands.
                _ = try? await ThapsusSdk.shared.lastMile().flushOutbox(maxBatch: 20)
                await MainActor.run {
                    submitting = false
                    onDone(nil)
                }
            } catch {
                let msg = error.localizedDescription
                await MainActor.run {
                    submitting = false
                    inlineError = msg
                    onDone(msg)
                }
            }
        }
    }
}

extension LastMileRunDto: @retroactive Identifiable {}
