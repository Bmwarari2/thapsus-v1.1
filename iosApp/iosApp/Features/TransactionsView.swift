// TransactionsView.swift
// Customer transaction history — paginated payments + credit ledger.
// Backed by TransactionsViewModel + new server endpoints in PR 2:
//   GET /api/payments?status=…&limit=…&offset=…
//   GET /api/payments/me/credit/ledger?limit=…&offset=…
//
// Two tabs in one view (Payments / Credit). Linked from CreditCenterView.

import SwiftUI
import ThapsusShared

struct TransactionsView: View {
    @State private var vm: TransactionsViewModel? = nil
    @State private var paymentsObs: StateFlowObserver<PagedPaymentsState>? = nil
    @State private var creditObs:   StateFlowObserver<PagedCreditState>? = nil
    @State private var tab: Tab = .payments

    enum Tab: Hashable { case payments, credit }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EyebrowPill(label: "History", systemImage: "list.bullet.rectangle")
                EditorialHeader(
                    title: "Transactions",
                    subtitle: "Every card or M-Pesa payment, plus your credit activity."
                )
                tabPicker
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Transactions")
        .glassNavigationBar()
        .refreshable { reloadCurrent() }
        .task { bootstrap() }
    }

    // MARK: - Tab picker

    private var tabPicker: some View {
        Picker("Tab", selection: $tab) {
            Text("Payments").tag(Tab.payments)
            Text("Credit").tag(Tab.credit)
        }
        .pickerStyle(.segmented)
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        switch tab {
        case .payments: paymentsList
        case .credit:   creditList
        }
    }

    @ViewBuilder
    private var paymentsList: some View {
        let state = paymentsObs?.value
        let items = state?.items ?? []
        if !items.isEmpty {
            VStack(spacing: 12) {
                ForEach(items, id: \.id) { p in PaymentRow(payment: p) }
                if state?.done == false {
                    loadMoreButton(loading: state?.loading == true) {
                        vm?.loadPayments(reset: false, status: nil)
                    }
                }
            }
        } else if state?.loading == true {
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        } else if let err = state?.error {
            ErrorBanner(title: "Couldn't load", message: err)
        } else {
            emptyState(
                icon: "tray",
                title: "No payments yet",
                body: "Once you pay an invoice or accept a Buy-for-me quote, it'll show up here."
            )
        }
    }

    @ViewBuilder
    private var creditList: some View {
        let state = creditObs?.value
        let items = state?.items ?? []
        if !items.isEmpty {
            VStack(spacing: 12) {
                ForEach(items, id: \.id) { e in CreditRow(entry: e) }
                if state?.done == false {
                    loadMoreButton(loading: state?.loading == true) {
                        vm?.loadCredit(reset: false)
                    }
                }
            }
        } else if state?.loading == true {
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        } else if let err = state?.error {
            ErrorBanner(title: "Couldn't load", message: err)
        } else {
            emptyState(
                icon: "gift",
                title: "No credit activity yet",
                body: "Refer a friend to earn KES 50 of credit on their first paid order."
            )
        }
    }

    private func loadMoreButton(loading: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            if loading {
                ProgressView()
            } else {
                Text("Load more").font(.subheadline.weight(.semibold))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .disabled(loading)
    }

    private func emptyState(icon: String, title: String, body: String) -> some View {
        CrystalCard {
            VStack(spacing: 8) {
                Image(systemName: icon).font(.title2).foregroundStyle(.secondary)
                Text(title).font(.headline)
                Text(body).font(.subheadline).foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
        }
    }

    // MARK: - Wiring

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.transactionsViewModel()
        vm = model
        paymentsObs = StateFlowObserver(initial: model.paymentsState.value) { model.paymentsState }
        creditObs   = StateFlowObserver(initial: model.creditState.value)   { model.creditState }
        model.loadPayments(reset: true, status: nil)
        model.loadCredit(reset: true)
    }

    private func reloadCurrent() {
        switch tab {
        case .payments: vm?.loadPayments(reset: true, status: nil)
        case .credit:   vm?.loadCredit(reset: true)
        }
    }
}

// MARK: - Rows

private struct PaymentRow: View {
    let payment: PaymentDto

    var body: some View {
        CrystalCard {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: payment.method == "stripe" ? "creditcard.fill" : "iphone.gen3")
                    .font(.title3)
                    .foregroundStyle(Brand.ink)
                    .frame(width: 36, height: 36)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .firstTextBaseline) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(targetTitle(payment.targetKind) + " · " + methodLabel(payment.method))
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.secondary)
                                .textCase(.uppercase)
                            Text(payment.targetLabel ?? payment.targetId)
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 4) {
                            Text("KES \(formatKes(payment.amountDueKes))")
                                .font(.subheadline.weight(.heavy))
                            statusPill(payment.status)
                        }
                    }
                    HStack(spacing: 10) {
                        Text(formatDate(payment.paidAt ?? payment.createdAt))
                            .font(.caption2).foregroundStyle(.secondary)
                        if payment.amountCreditKes > 0 {
                            Text("− KES \(formatKes(payment.amountCreditKes)) credit")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.green)
                        }
                        if let pence = payment.stripeAmountPenceGbp?.int64Value, pence > 0 {
                            Text("£\(String(format: "%.2f", Double(pence) / 100.0))")
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                    if let ref = payment.method == "stripe" ? payment.stripePaymentIntentId : payment.mpesaReference {
                        Text(ref).font(.caption2.monospaced()).foregroundStyle(.secondary).lineLimit(1)
                    }
                    if let count = payment.attemptsCount?.intValue, count > 1 {
                        Text("+\(count - 1) earlier attempt\(count - 1 == 1 ? "" : "s")")
                            .font(.caption2.weight(.bold))
                            .textCase(.uppercase)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(Color.gray.opacity(0.12), in: Capsule())
                            .foregroundStyle(.secondary)
                    }
                    if let r = payment.rejectionReason, !r.isEmpty, payment.status != "cancelled" {
                        Text("Rejected: \(r)").font(.caption.weight(.semibold)).foregroundStyle(.red)
                    }
                }
            }
        }
    }

    private func targetTitle(_ kind: String) -> String {
        switch kind {
        case "order":         return "Order"
        case "consolidation": return "Shipping invoice"
        case "buy_for_me":    return "Buy-for-me"
        default:              return kind
        }
    }

    private func methodLabel(_ method: String) -> String {
        method == "stripe" ? "Card" : "M-Pesa"
    }

    private func statusPill(_ status: String) -> some View {
        let (label, color): (String, Color) = {
            switch status {
            case "paid":            return ("Paid", .green)
            case "pending":         return ("Pending", .orange)
            case "awaiting_review": return ("Awaiting review", .orange)
            case "failed":          return ("Failed", .red)
            case "rejected":        return ("Rejected", .red)
            case "cancelled":       return ("Cancelled", .gray)
            default:                return (status, .gray)
            }
        }()
        return Text(label)
            .font(.caption2.weight(.bold))
            .textCase(.uppercase)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(color.opacity(0.12), in: Capsule())
            .foregroundStyle(color)
    }
}

private struct CreditRow: View {
    let entry: CreditLedgerEntryDto

    var body: some View {
        let positive = entry.deltaKes > 0
        return CrystalCard {
            HStack(alignment: .top, spacing: 12) {
                Group {
                    if positive {
                        Image(systemName: iconName(for: entry.reason))
                            .font(.title3)
                            .foregroundStyle(Color.green)
                            .frame(width: 36, height: 36)
                            .background(Color.green.opacity(0.12),
                                        in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    } else {
                        Image(systemName: iconName(for: entry.reason))
                            .font(.title3)
                            .foregroundStyle(Brand.ink)
                            .frame(width: 36, height: 36)
                            .background(.regularMaterial,
                                        in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(reasonLabel(entry.reason))
                            .font(.subheadline.weight(.semibold))
                        Spacer()
                        Text("\(positive ? "+" : "−") KES \(formatKes(abs(entry.deltaKes)))")
                            .font(.subheadline.weight(.heavy))
                            .foregroundStyle(positive ? Color.green : Brand.ink)
                    }
                    if let note = entry.note, !note.isEmpty {
                        Text(note).font(.caption).foregroundStyle(.secondary)
                    }
                    Text(formatDate(entry.createdAt))
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
    }

    private func iconName(for reason: String) -> String {
        switch reason {
        case "referral":         return "gift.fill"
        case "consumed_payment": return "creditcard.fill"
        case "refund":           return "arrow.uturn.backward"
        case "manual":           return "square.and.pencil"
        default:                 return "circle.dashed"
        }
    }

    private func reasonLabel(_ reason: String) -> String {
        switch reason {
        case "referral":         return "Referral reward"
        case "consumed_payment": return "Applied to payment"
        case "refund":           return "Refund"
        case "manual":           return "Manual adjustment"
        default:                 return reason
        }
    }
}

// MARK: - Helpers

private func formatKes(_ value: Int64) -> String {
    let f = NumberFormatter(); f.numberStyle = .decimal; f.maximumFractionDigits = 0
    return f.string(from: NSNumber(value: value)) ?? "\(value)"
}

private func formatDate(_ iso: String?) -> String {
    guard let iso, let date = ISO8601DateFormatter.thapsusFlexible().date(from: iso) else { return "—" }
    let f = DateFormatter()
    f.dateStyle = .medium
    f.timeStyle = .short
    return f.string(from: date)
}

private extension ISO8601DateFormatter {
    static func thapsusFlexible() -> ISO8601DateFormatter {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }
}
