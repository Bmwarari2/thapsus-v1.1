// TicketsView.swift
// Customer support inbox: list tickets, open one, post replies.

import SwiftUI
import ThapsusShared
import UniformTypeIdentifiers

struct TicketsListView: View {
    var asAdmin: Bool = false

    @Environment(AppEnvironment.self) private var env

    @State private var vm: TicketsListViewModel? = nil
    @State private var observer: StateFlowObserver<TicketsListViewModelUiState>? = nil
    @State private var showCreate: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: asAdmin ? "Admin queue" : "Support",
                            systemImage: "questionmark.bubble.fill")
                EditorialHeader(
                    title: asAdmin ? "Tickets" : "Help & support",
                    subtitle: asAdmin
                        ? "Every ticket across the system. Tap to reply."
                        : "Open a ticket and our team will respond."
                )

                if !asAdmin {
                    Button(action: { showCreate = true }) {
                        HStack(spacing: 8) {
                            Image(systemName: "plus.circle.fill")
                            Text("New ticket")
                        }
                    }
                    .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

                    WhatsAppSupportButton(label: "Or chat us on WhatsApp")
                }

                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Support")
        .glassNavigationBar()
        .sheet(isPresented: $showCreate) {
            CreateTicketSheet { subject, description in
                vm?.create(subject: subject, description: description)
                showCreate = false
            }
        }
        .task { bootstrap() }
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as TicketsListViewModelUiStateLoaded:
            if loaded.items.isEmpty {
                CrystalCard {
                    Text("No tickets yet. Tap “New ticket” to get help.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            } else {
                ForEach(loaded.items, id: \.id) { ticket in
                    NavigationLink {
                        TicketDetailView(ticketId: ticket.id, subject: ticket.subject, asAdmin: asAdmin)
                    } label: {
                        ticketRow(ticket)
                    }
                    .buttonStyle(.plain)
                }
            }
        case is TicketsListViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 32)
        case let err as TicketsListViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func ticketRow(_ t: TicketDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(t.subject).font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    statusBadge(t.status)
                }
                Text(t.description)
                    .font(.footnote).foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
    }

    private func statusBadge(_ status: String) -> some View {
        let color: Color = status == "resolved" || status == "closed" ? .green : .orange
        return Text(status.replacingOccurrences(of: "_", with: " ").uppercased())
            .font(.caption2.weight(.heavy)).tracking(2)
            .foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Capsule().fill(color.opacity(0.16)))
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model: TicketsListViewModel
        if asAdmin {
            model = ThapsusSdk.shared.adminTicketsListViewModel()
        } else {
            guard let userId = env.currentUserID else { return }
            model = ThapsusSdk.shared.ticketsListViewModel(userId: userId)
        }
        vm = model
        observer = StateFlowObserver(initial: model.state.value) {
            model.state
        }
    }
}

private struct CreateTicketSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var subject: String = ""
    @State private var description: String = ""
    let onSubmit: (String, String) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("What's it about?") {
                    TextField("Subject", text: $subject)
                }
                Section("Tell us more") {
                    TextEditor(text: $description).frame(minHeight: 140)
                }
            }
            .navigationTitle("New ticket")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Submit") { onSubmit(subject, description) }
                        .disabled(subject.isEmpty || description.isEmpty)
                }
            }
        }
    }
}

struct TicketDetailView: View {
    let ticketId: String
    let subject: String
    /// True when the current user is acting as admin (admin's own messages
    /// align right; customer messages align left). Defaults to customer view.
    var asAdmin: Bool = false

    @State private var vm: TicketDetailViewModel? = nil
    @State private var observer: StateFlowObserver<TicketDetailViewModelUiState>? = nil
    @State private var reply: String = ""
    @State private var attachmentPath: String?
    @State private var attachmentFileName: String?
    @State private var attachmentUploading: Bool = false
    @State private var attachmentError: String?
    @State private var pickerOpen: Bool = false

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 4) {
                        content
                        Color.clear.frame(height: 8).id("bottom")
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 12)
                    .padding(.bottom, 12)
                }
                .onChange(of: messageCount) { _, _ in
                    withAnimation { proxy.scrollTo("bottom", anchor: .bottom) }
                }
            }
            replyComposer
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle(subject)
        .glassNavigationBar()
        .task { bootstrap() }
    }

    private var messageCount: Int {
        (observer?.value as? TicketDetailViewModelUiStateLoaded)?.messages.count ?? 0
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as TicketDetailViewModelUiStateLoaded:
            // Group consecutive messages by sender so we can hide the timestamp
            // on all but the last bubble in a run — matches iMessage rhythm.
            let messages = loaded.messages
            ForEach(Array(messages.enumerated()), id: \.element.id) { idx, msg in
                let nextHasSameAuthor = (idx + 1 < messages.count) &&
                    isOutgoing(messages[idx + 1]) == isOutgoing(msg)
                bubbleRow(msg, showTimestamp: !nextHasSameAuthor)
            }
        case is TicketDetailViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        case let err as TicketDetailViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func bubbleRow(_ msg: TicketMessageDto, showTimestamp: Bool) -> some View {
        let outgoing = isOutgoing(msg)
        return VStack(alignment: outgoing ? .trailing : .leading, spacing: 2) {
            if !msg.message.isEmpty {
                HStack(alignment: .bottom, spacing: 6) {
                    if outgoing { Spacer(minLength: 60) }
                    bubble(msg.message, outgoing: outgoing)
                    if !outgoing { Spacer(minLength: 60) }
                }
            }
            if msg.attachmentUrl != nil {
                HStack {
                    if outgoing { Spacer(minLength: 60) }
                    AttachmentBubble(messageId: msg.id, outgoing: outgoing)
                    if !outgoing { Spacer(minLength: 60) }
                }
            }
            if showTimestamp {
                HStack(spacing: 6) {
                    if outgoing { Spacer() }
                    Text(senderLabel(msg))
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.secondary)
                    if let created = msg.createdAt {
                        Text("·").foregroundStyle(.tertiary)
                        Text(formattedTime(created))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    if !outgoing { Spacer() }
                }
                .padding(.horizontal, 10)
                .padding(.bottom, 6)
            }
        }
    }

    private func bubble(_ text: String, outgoing: Bool) -> some View {
        Text(text)
            .font(.body)
            .foregroundStyle(outgoing ? Color.white : Brand.ink)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                Group {
                    if outgoing {
                        RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Brand.orange)
                    } else {
                        RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.ultraThinMaterial)
                    }
                }
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(outgoing ? Color.clear : Color.white.opacity(0.5), lineWidth: 1)
            )
            .frame(maxWidth: 290, alignment: outgoing ? .trailing : .leading)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// "Outgoing" = current user's own message. For customer view, that's
    /// any non-admin sender; for admin view, only admin messages.
    private func isOutgoing(_ msg: TicketMessageDto) -> Bool {
        let role = msg.role?.lowercased()
        return asAdmin ? (role == "admin") : (role != "admin")
    }

    private func senderLabel(_ msg: TicketMessageDto) -> String {
        if isOutgoing(msg) { return "You" }
        if msg.role?.lowercased() == "admin" { return "Support" }
        return msg.name ?? msg.email ?? "Support"
    }

    private func formattedTime(_ raw: String) -> String {
        // Server sends ISO8601 — format to a short local time.
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = iso.date(from: raw) ?? ISO8601DateFormatter().date(from: raw)
        guard let date else { return raw }
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .short
        return f.string(from: date)
    }

    @ViewBuilder
    private var replyComposer: some View {
        VStack(spacing: 6) {
            if attachmentUploading || attachmentPath != nil || attachmentError != nil {
                attachmentChip
            }
            replyComposerRow
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(.regularMaterial)
    }

    @ViewBuilder
    private var attachmentChip: some View {
        HStack(spacing: 8) {
            Image(systemName: attachmentUploading
                  ? "arrow.up.circle"
                  : (attachmentError != nil ? "exclamationmark.triangle.fill" : "paperclip"))
                .foregroundStyle(attachmentError != nil ? .red : Brand.orange)
            VStack(alignment: .leading, spacing: 0) {
                if attachmentUploading {
                    Text("Uploading…").font(.caption)
                } else if let error = attachmentError {
                    InlineFieldError(message: error)
                } else if let name = attachmentFileName {
                    Text(name).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if attachmentPath != nil {
                Button("Remove") {
                    attachmentPath = nil
                    attachmentFileName = nil
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(.red)
            }
        }
        .padding(8)
        .background(RoundedRectangle(cornerRadius: 12).fill(.ultraThinMaterial))
    }

    private var replyComposerRow: some View {
        HStack(spacing: 10) {
            Button {
                attachmentError = nil
                pickerOpen = true
            } label: {
                Image(systemName: "paperclip")
                    .font(.headline)
                    .foregroundStyle(attachmentUploading ? Brand.ink.opacity(0.3) : Brand.orange)
                    .frame(width: 38, height: 38)
            }
            .disabled(attachmentUploading)
            .fileImporter(
                isPresented: $pickerOpen,
                allowedContentTypes: [UTType.image, UTType.pdf],
                allowsMultipleSelection: false
            ) { result in handlePickedAttachment(result: result) }

            TextField("Type a reply…", text: $reply, axis: .vertical)
                .textFieldStyle(.plain)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(.ultraThinMaterial)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(.white.opacity(0.45), lineWidth: 1)
                )
            Button(action: send) {
                Image(systemName: "arrow.up")
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(
                        Circle().fill(canSend ? Brand.orange : Brand.ink.opacity(0.25))
                    )
            }
            .disabled(!canSend)
        }
    }

    private var canSend: Bool {
        if attachmentUploading { return false }
        let hasText = !reply.trimmingCharacters(in: .whitespaces).isEmpty
        let hasAttachment = attachmentPath != nil
        return hasText || hasAttachment
    }

    private func send() {
        let text = reply.trimmingCharacters(in: .whitespaces)
        let path = attachmentPath
        // Server allows attachment-only messages; only bail when both empty.
        if text.isEmpty && path == nil { return }
        vm?.reply(message: text, attachmentUrl: path)
        reply = ""
        attachmentPath = nil
        attachmentFileName = nil
    }

    private func handlePickedAttachment(result: Result<[URL], Error>) {
        switch result {
        case .failure(let err):
            attachmentError = err.localizedDescription
        case .success(let urls):
            guard let url = urls.first else { return }
            uploadAttachment(at: url)
        }
    }

    private func uploadAttachment(at url: URL) {
        attachmentError = nil
        attachmentUploading = true
        let needsScope = url.startAccessingSecurityScopedResource()
        let filename = url.lastPathComponent
        Task.detached(priority: .userInitiated) {
            defer { if needsScope { url.stopAccessingSecurityScopedResource() } }
            do {
                let data = try Data(contentsOf: url)
                let signed = try await ThapsusSdk.shared.tickets()
                    .requestAttachmentUploadUrl(filename: filename)
                guard let target = URL(string: signed.signedUrl) else {
                    throw NSError(domain: "TicketAttachUpload", code: 1, userInfo: [
                        NSLocalizedDescriptionKey: "Invalid signed URL"
                    ])
                }
                var req = URLRequest(url: target)
                req.httpMethod = "PUT"
                req.addValue(url.pathExtension.lowercased() == "pdf" ? "application/pdf" : "image/jpeg",
                             forHTTPHeaderField: "Content-Type")
                req.timeoutInterval = 60
                let (respData, resp) = try await URLSession.shared.upload(for: req, from: data)
                guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                    let body = String(data: respData, encoding: .utf8) ?? "<no body>"
                    let code = (resp as? HTTPURLResponse)?.statusCode ?? -1
                    throw NSError(domain: "TicketAttachUpload", code: code, userInfo: [
                        NSLocalizedDescriptionKey: "Signed-PUT \(code): \(body.prefix(200))"
                    ])
                }
                await MainActor.run {
                    attachmentPath = signed.path
                    attachmentFileName = filename
                    attachmentUploading = false
                }
            } catch {
                await MainActor.run {
                    attachmentError = "Couldn't attach: \(error.localizedDescription)"
                    attachmentUploading = false
                }
            }
        }
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.ticketDetailViewModel(ticketId: ticketId)
        vm = model
        observer = StateFlowObserver(initial: model.state.value) {
            model.state
        }
    }
}

/// Tappable attachment bubble — fetches a 5-min signed URL on tap and
/// hands it off to the OS-default handler (Safari/Preview/Photos).
private struct AttachmentBubble: View {
    let messageId: String
    let outgoing: Bool
    @State private var loading = false
    @State private var error: String?
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button(action: open) {
            HStack(spacing: 8) {
                if loading {
                    ProgressView().controlSize(.small)
                } else {
                    Image(systemName: "paperclip")
                        .foregroundStyle(outgoing ? Color.white : Brand.orange)
                }
                Text(error ?? "Attachment")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(error == nil ? (outgoing ? Color.white : Brand.ink) : .red)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(outgoing ? Brand.orange.opacity(0.7) : Color.white.opacity(0.9))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(outgoing ? Color.clear : Brand.orange.opacity(0.4), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(loading)
    }

    private func open() {
        loading = true
        error = nil
        Task {
            do {
                let resp = try await ThapsusSdk.shared.tickets()
                    .requestAttachmentDownloadUrl(messageId: messageId)
                if let url = URL(string: resp.signedUrl) {
                    await MainActor.run { openURL(url); loading = false }
                } else {
                    await MainActor.run { error = "Bad URL"; loading = false }
                }
            } catch {
                await MainActor.run {
                    self.error = "Couldn't open"
                    self.loading = false
                }
            }
        }
    }
}
