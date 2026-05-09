// AdminPaymentsView.swift
// M-Pesa payments review queue (server PR #61 / migration 028). Stripe
// payments don't appear here — they auto-flip via the webhook. M-Pesa
// payments arrive as 'awaiting_review' once the customer pastes the
// confirmation SMS in PayInvoiceView; admin verifies the parsed
// reference + amount, then approves or rejects with a reason.

import SwiftUI
import ThapsusShared

struct AdminPaymentsView: View {
    @State private var vm: AdminPaymentsViewModel? = nil
    @State private var stateObs: StateFlowObserver<AdminPaymentsViewModelUiState>? = nil
    @State private var actionObs: StateFlowObserver<AdminPaymentsViewModelActionState>? = nil
    @State private var expandedSms: Set<String> = []
    @State private var rejectTarget: PaymentDto? = nil
    // Audit P1.2: when claimed < due, Verify opens this sheet instead of
    // approving directly. The override reason is sent to the server as
    // `override_reason` (required >=10 chars) and persisted on the row's
    // `approval_override_reason`.
    @State private var overrideTarget: PaymentDto? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EyebrowPill(label: "Admin", systemImage: "creditcard.fill")
                EditorialHeader(
                    title: "M-Pesa review",
                    subtitle: "Verify customer-submitted M-Pesa SMS proofs. Approve to mark the payment paid, reject with a reason if it doesn't match."
                )
                actionBanner
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Payments")
        .glassNavigationBar()
        .sheet(item: $rejectTarget) { p in
            RejectPaymentSheet(payment: p) { reason in
                vm?.reject(id: p.id, reason: reason)
                rejectTarget = nil
            }
        }
        .sheet(item: $overrideTarget) { p in
            OverridePaymentSheet(payment: p) { reason in
                vm?.approve(id: p.id, overrideReason: reason)
                overrideTarget = nil
            }
        }
        .task { bootstrap() }
    }

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case let done as AdminPaymentsViewModelActionStateDone:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Done", message: done.message)
        case let err as AdminPaymentsViewModelActionStateError:
            ErrorBanner(title: "Couldn't update", message: err.message)
        case is AdminPaymentsViewModelActionStateInFlight:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch stateObs?.value {
        case let loaded as AdminPaymentsViewModelUiStateLoaded:
            if loaded.pending.isEmpty {
                CrystalCard {
                    Text("No payments waiting for review.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            } else {
                ForEach(loaded.pending, id: \.id) { p in
                    paymentCard(p)
                }
            }
        case is AdminPaymentsViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        case let err as AdminPaymentsViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func paymentCard(_ p: PaymentDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline) {
                    Text(p.userName ?? p.userEmail ?? p.userId)
                        .font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    Text("KES \(formatKes(p.amountDueKes))")
                        .font(.title3.weight(.heavy)).foregroundStyle(Brand.orange)
                }
                if let email = p.userEmail {
                    Text(email).font(.caption).foregroundStyle(.secondary)
                }
                Text("\(targetLabel(p)) · \(p.id.prefix(14))…")
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)

                Divider().background(Brand.ink.opacity(0.08))

                HStack(spacing: 8) {
                    pill(label: "REF", value: p.mpesaReference ?? "—")
                    if let claimedBoxed = p.mpesaMessageAmountKes {
                        let claimed = claimedBoxed.int64Value
                        let due     = p.amountDueKes
                        let mismatch = claimed != due
                        pill(
                            label: "CLAIMED",
                            value: "KES \(formatKes(claimed))",
                            tint: mismatch ? .red : .green
                        )
                    }
                    if let phone = p.mpesaPhone {
                        pill(label: "PHONE", value: phone)
                    }
                }

                Button {
                    if expandedSms.contains(p.id) { expandedSms.remove(p.id) }
                    else { expandedSms.insert(p.id) }
                } label: {
                    Label(
                        expandedSms.contains(p.id) ? "Hide raw SMS" : "Show raw SMS",
                        systemImage: expandedSms.contains(p.id) ? "chevron.up" : "chevron.down"
                    )
                    .font(.caption.weight(.semibold))
                }
                .tint(Brand.ink)

                if expandedSms.contains(p.id), let raw = p.mpesaMessageRaw {
                    Text(raw)
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Brand.cream.opacity(0.4))
                        )
                }

                HStack(spacing: 10) {
                    let isShort = isShortPayment(p)
                    Button {
                        if isShort {
                            overrideTarget = p
                        } else {
                            vm?.approve(id: p.id, overrideReason: nil)
                        }
                    } label: {
                        Label(isShort ? "Verify w/ override" : "Approve",
                              systemImage: "checkmark.circle.fill")
                            .frame(maxWidth: .infinity)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(GlassSheenButtonStyle(
                        fill: isShort ? Brand.gold : Brand.orange,
                        foreground: .white
                    ))

                    Button {
                        rejectTarget = p
                    } label: {
                        Label("Reject", systemImage: "xmark.circle")
                            .frame(maxWidth: .infinity)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(GlassSheenButtonStyle(
                        fill: Color.red.opacity(0.14),
                        foreground: .red
                    ))
                }
            }
        }
    }

    private func pill(label: String, value: String, tint: Color = Brand.ink) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2.weight(.heavy)).tracking(2)
                .foregroundStyle(.secondary)
            Text(value).font(.caption.monospaced()).foregroundStyle(tint)
        }
        .padding(.horizontal, 10).padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(tint.opacity(0.10))
        )
    }

    private func targetLabel(_ p: PaymentDto) -> String {
        switch p.targetKind {
        case "buy_for_me":    return "Buy-for-me"
        case "consolidation": return "Consolidation"
        case "order":         return "Order"
        default:              return p.targetKind.uppercased()
        }
    }

    private func formatKes(_ value: Int64) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.maximumFractionDigits = 0
        return f.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    /// Audit P1.2: a payment is "short" when the customer-claimed amount
    /// is strictly less than the invoice. Equal or greater is not blocked
    /// (the customer over-paid; admin can refund the difference offline).
    /// Rows with no claimed amount yet are not shown the override path —
    /// the server 409s with `no SMS on file` instead.
    private func isShortPayment(_ p: PaymentDto) -> Bool {
        guard let boxed = p.mpesaMessageAmountKes else { return false }
        return boxed.int64Value < p.amountDueKes
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.adminPaymentsViewModel()
        vm = model
        model.load()
        stateObs = StateFlowObserver(initial: model.state.value) { model.state }
        actionObs = StateFlowObserver(initial: model.action.value) { model.action }
    }
}

extension PaymentDto: @retroactive Identifiable {}

/// Audit P1.2: shown when an admin taps Verify on a row whose
/// `mpesaMessageAmountKes < amountDueKes`. Captures `override_reason`
/// (>=10 chars) which the server requires to settle a short payment.
private struct OverridePaymentSheet: View {
    @Environment(\.dismiss) private var dismiss
    let payment: PaymentDto
    let onSubmit: (String) -> Void
    @State private var reason: String = ""

    private var trimmed: String {
        reason.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(alignment: .firstTextBaseline) {
                        Text("Customer claimed").font(.caption).foregroundStyle(.secondary)
                        Spacer()
                        Text("KES \(payment.mpesaMessageAmountKes?.int64Value ?? 0)")
                            .font(.headline).foregroundStyle(.red)
                    }
                    HStack(alignment: .firstTextBaseline) {
                        Text("Invoice due").font(.caption).foregroundStyle(.secondary)
                        Spacer()
                        Text("KES \(payment.amountDueKes)")
                            .font(.headline).foregroundStyle(Brand.ink)
                    }
                } header: { Text("Amount mismatch") } footer: {
                    Text("M-Pesa SMS shows less than the invoice. Provide a written reason to approve anyway. The note is recorded on the payment for audit.")
                }
                Section("Reason (min 10 characters)") {
                    TextEditor(text: $reason).frame(minHeight: 120)
                }
            }
            .navigationTitle("Approve with override")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Approve") { onSubmit(trimmed) }
                        .disabled(trimmed.count < 10)
                }
            }
        }
    }
}

private struct RejectPaymentSheet: View {
    @Environment(\.dismiss) private var dismiss
    let payment: PaymentDto
    let onSubmit: (String) -> Void
    @State private var reason: String = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(payment.userName ?? payment.userEmail ?? payment.userId)
                        .font(.headline)
                    Text("Amount due: KES \(payment.amountDueKes)")
                        .font(.caption).foregroundStyle(.secondary)
                    if let claimed = payment.mpesaMessageAmountKes {
                        Text("Customer claimed: KES \(claimed.int64Value)")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                Section("Why reject?") {
                    TextEditor(text: $reason).frame(minHeight: 120)
                }
                Section {
                    Text("The customer can resubmit a fresh M-Pesa SMS for the same payment.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Reject payment")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Reject") {
                        onSubmit(reason.trimmingCharacters(in: .whitespacesAndNewlines))
                    }
                    .disabled(reason.trimmingCharacters(in: .whitespacesAndNewlines).count < 3)
                }
            }
        }
    }
}
