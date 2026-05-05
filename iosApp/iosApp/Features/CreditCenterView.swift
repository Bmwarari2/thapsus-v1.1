// CreditCenterView.swift
// Replaces "Wallet & top-up" on the customer dashboard. Shows the
// running KES credit balance + a tip about how it gets earned (referrals)
// and applied (auto-deduct on next payment).
//
// Credit balance reads from /api/payments/me/credit. The list of credit
// transactions (credit_ledger) isn't surfaced here yet — keep it simple:
// users just need to know the balance + how to earn more.

import SwiftUI
import ThapsusShared

struct CreditCenterView: View {
    @State private var vm: PaymentsViewModel? = nil
    @State private var stateObs: StateFlowObserver<PaymentsViewModelUiState>? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EyebrowPill(label: "Credit", systemImage: "gift.fill")
                EditorialHeader(
                    title: "My credit",
                    subtitle: "Earned from referrals — auto-applied to your next payment."
                )
                balanceCard
                viewTransactionsLink
                howItWorks
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Credit")
        .glassNavigationBar()
        .refreshable { vm?.bootstrap() }
        .task { bootstrap() }
    }

    private var balanceCard: some View {
        let balance = creditKes
        return SoftCard(tint: Brand.orange.opacity(0.08)) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Available credit").font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("KES")
                        .font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                    Text(formatKes(balance))
                        .font(.system(size: 44, weight: .heavy))
                        .foregroundStyle(Brand.ink)
                }
                if balance == 0 {
                    Text("Refer a friend to earn KES 50.")
                        .font(.caption).foregroundStyle(.secondary)
                } else {
                    Text("Will be deducted from your next invoice.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private var viewTransactionsLink: some View {
        NavigationLink {
            TransactionsView()
        } label: {
            HStack {
                Label("View transactions", systemImage: "list.bullet.rectangle")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Brand.ink)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.secondary)
            }
            .padding(16)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var howItWorks: some View {
        ProcessStepsCard(
            title: "How credit works",
            steps: [
                ("1", "Earn", "Refer someone with your code → both of you get KES 50 on their first paid order."),
                ("2", "Apply", "On any payment, your full credit balance is auto-applied first."),
                ("3", "Pay the rest", "If credit fully covers it, you're done. Otherwise, pay the remainder via card or M-Pesa."),
            ]
        )
    }

    private var creditKes: Int64 {
        guard let s = stateObs?.value as? PaymentsViewModelUiStateReady else { return 0 }
        return s.creditBalanceKes
    }

    private func formatKes(_ value: Int64) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.maximumFractionDigits = 0
        return f.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.paymentsViewModel()
        vm = model
        model.bootstrap()
        stateObs = StateFlowObserver(initial: model.state.value) { model.state }
    }
}
