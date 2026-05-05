// AdminIssueInvoiceView.swift (PR 5)
// Admin form for issuing a one-off "standalone" invoice to a customer.
// Picks a customer, sets a KES amount + description; the customer receives
// an email + can pay via the regular PayInvoiceView flow with
// target_kind='consolidation'.

import SwiftUI
import ThapsusShared

struct AdminIssueInvoiceView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var vm: AdminIssueInvoiceViewModel? = nil
    @State private var usersObs:  StateFlowObserver<[AdminUserDto]>? = nil
    @State private var actionObs: StateFlowObserver<AdminIssueInvoiceViewModelActionState>? = nil

    @State private var search: String = ""
    @State private var selectedUserId: String? = nil
    @State private var amount: String = ""
    @State private var description: String = ""
    @State private var notes: String = ""
    @State private var pendingAutoClose: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EyebrowPill(label: "Admin", systemImage: "doc.text.fill")
                EditorialHeader(
                    title: "Issue invoice",
                    subtitle: "One-off charge — customer pays via card or M-Pesa from their Orders tab."
                )
                actionBanner
                customerCard
                invoiceCard
                submitButton
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Issue invoice")
        .glassNavigationBar()
        .task { bootstrap() }
    }

    // MARK: - Banner

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case let done as AdminIssueInvoiceViewModelActionStateDone:
            issuedReceipt(done)
        case let err as AdminIssueInvoiceViewModelActionStateError:
            ErrorBanner(title: "Couldn't issue", message: err.message)
        case is AdminIssueInvoiceViewModelActionStateSubmitting:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    /// Clean structured receipt — replaces the previous single-line
    /// CalloutBanner that bridged `done.description` (the SKIE-renamed
    /// Kotlin toString instead of the field). Lays out customer / amount /
    /// reference clearly so the admin sees at a glance that the invoice
    /// went through and doesn't repeat the request.
    @ViewBuilder
    private func issuedReceipt(_ done: AdminIssueInvoiceViewModelActionStateDone) -> some View {
        SoftCard(tint: Color.green.opacity(0.10)) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.title2)
                        .foregroundStyle(.green)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Invoice issued").font(.headline).foregroundStyle(Brand.ink)
                        Text("Email sent to the customer · closing in a moment…")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                }
                Divider().opacity(0.4)
                receiptRow(label: "Customer", value: done.customerEmail ?? "—")
                // SKIE renames `description` → `description_` because of an
                // NSObject collision (see feedback_skie_description_field.md).
                receiptRow(label: "For", value: done.description_)
                receiptRow(label: "Amount", value: "KES \(formatKes(done.amountKes))", emphasis: true)
                HStack(spacing: 10) {
                    Button("Issue another") {
                        pendingAutoClose = false
                        vm?.resetAction()
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    Spacer()
                    Button("Done") { dismiss() }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                        .tint(Brand.orange)
                }
            }
        }
        // Auto-dismiss the sheet after the success banner has been visible
        // long enough for the admin to register that the invoice landed.
        // Without this the admin sees only the inline confirmation and
        // doesn't realise the form is reusable / needs explicit closing.
        // "Issue another" clears `pendingAutoClose` so the timer doesn't
        // surprise an admin in the middle of a second invoice.
        .task(id: ObjectIdentifier(done)) {
            pendingAutoClose = true
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if pendingAutoClose { dismiss() }
        }
    }

    private func receiptRow(label: String, value: String, emphasis: Bool = false) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label.uppercased())
                .font(.caption2.weight(.heavy)).tracking(2)
                .foregroundStyle(Brand.ink.opacity(0.5))
                .frame(width: 80, alignment: .leading)
            Text(value)
                .font(emphasis ? .subheadline.weight(.heavy) : .subheadline.weight(.semibold))
                .foregroundStyle(emphasis ? Brand.ink : Brand.ink.opacity(0.85))
            Spacer()
        }
    }

    // MARK: - Customer picker

    @ViewBuilder
    private var customerCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Customer").font(.headline).foregroundStyle(Brand.ink)

                TextField("Search by email, name, or warehouse ID", text: $search)
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled(true)
                    .textInputAutocapitalization(.never)

                let users = filteredUsers
                if users.isEmpty {
                    Text("No matching customers.")
                        .font(.caption).foregroundStyle(.secondary)
                } else {
                    VStack(spacing: 0) {
                        ForEach(users.prefix(8), id: \.id) { u in
                            Button {
                                selectedUserId = u.id
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(u.name.isEmpty ? u.email : u.name).font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                                        Text(u.email).font(.caption2).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    if selectedUserId == u.id {
                                        Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                                    }
                                }
                                .padding(8)
                                .background(selectedUserId == u.id ? Color.orange.opacity(0.08) : Color.clear,
                                            in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            Divider()
                        }
                    }
                }
            }
        }
    }

    private var filteredUsers: [AdminUserDto] {
        let all = usersObs?.value ?? []
        let q = search.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return Array(all.prefix(25)) }
        return all.filter { u in
            u.email.lowercased().contains(q) ||
            u.name.lowercased().contains(q) ||
            u.id.lowercased().contains(q)
        }
    }

    // MARK: - Invoice fields

    @ViewBuilder
    private var invoiceCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("Invoice").font(.headline).foregroundStyle(Brand.ink)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Amount (KES)".uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2).foregroundStyle(Brand.ink.opacity(0.5))
                    TextField("e.g. 1500", text: $amount)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(.roundedBorder)
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Description (visible to customer)".uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2).foregroundStyle(Brand.ink.opacity(0.5))
                    TextField("e.g. Customs penalty for parcel TC-…", text: $description)
                        .textFieldStyle(.roundedBorder)
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Internal notes (admin only)".uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2).foregroundStyle(Brand.ink.opacity(0.5))
                    TextEditor(text: $notes)
                        .frame(minHeight: 80)
                        .padding(6)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
                        )
                }
            }
        }
    }

    private var submitButton: some View {
        Button {
            guard let userId = selectedUserId,
                  let amountValue = Double(amount), amountValue > 0,
                  !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
            vm?.issue(
                userId: userId,
                amountKes: amountValue,
                description: description.trimmingCharacters(in: .whitespacesAndNewlines),
                notes: notes.isEmpty ? nil : notes
            )
            // Clear amount/description for the next entry; keep customer
            // selected in case the admin issues several charges in a row.
            amount = ""
            description = ""
            notes = ""
        } label: {
            Label("Issue invoice + email customer", systemImage: "paperplane.fill")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(GlassSheenButtonStyle(fill: Brand.ink, foreground: .white))
        .disabled(!canSubmit)
    }

    private var canSubmit: Bool {
        // Block re-submit while the success banner is still showing —
        // admin must explicitly tap "Issue another" first.
        if actionObs?.value is AdminIssueInvoiceViewModelActionStateDone { return false }
        if actionObs?.value is AdminIssueInvoiceViewModelActionStateSubmitting { return false }
        guard selectedUserId != nil else { return false }
        guard let n = Double(amount), n > 0 else { return false }
        return !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    // MARK: - Wiring

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.adminIssueInvoiceViewModel()
        vm = model
        usersObs  = StateFlowObserver(initial: model.users.value)  { model.users }
        actionObs = StateFlowObserver(initial: model.action.value) { model.action }
        model.loadUsers()
    }

    private func formatKes(_ value: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.maximumFractionDigits = 0
        return f.string(from: NSNumber(value: value)) ?? "\(Int(value))"
    }
}
