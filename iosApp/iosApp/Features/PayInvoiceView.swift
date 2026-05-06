// PayInvoiceView.swift
// Customer payment flow that replaces the wallet (server PR #61, migration
// 028). Sheet presented from any "pay" CTA — BFM accept, customer-
// consolidation invoice card, future order pay.
//
// Flow:
//   1. Show the gross amount, the credit that will be applied, and the
//      net amount due (in KES).
//   2. Customer picks Card (Stripe) or M-Pesa.
//   3a. Card → server creates a PaymentIntent → Stripe PaymentSheet opens
//       → on .completed, the webhook will flip the target on its own.
//   3b. M-Pesa → server returns Till + reference + amount → customer
//       pays in their M-Pesa app, then pastes the confirmation SMS into
//       MpesaSubmitSheet → status → 'awaiting_review'.
//
// Stripe iOS SDK (StripePaymentSheet) is required; add it via Xcode →
// File → Add Package Dependencies → https://github.com/stripe/stripe-ios-spm
// → tick StripePaymentSheet. Without it, the Card path returns a clear
// "Stripe SDK not installed" message and the M-Pesa flow continues to work.

import SwiftUI
import ThapsusShared
#if canImport(StripePaymentSheet)
// @preconcurrency: stripe-ios-spm 23.x isn't yet annotated for Swift 6
// strict concurrency. Without it, StripeAPI.defaultPublishableKey
// (an @objc class var) is flagged "not concurrency-safe because it
// involves shared mutable state" even when set from a @MainActor
// context. The whole module compiles under Swift 5 concurrency rules
// while the rest of the app stays on Swift 6.
@preconcurrency import StripePaymentSheet
#endif

struct PayInvoiceView: View {
    /// What's being paid for. Mirrors the payments.target_kind enum on
    /// the server: 'order' | 'consolidation' | 'buy_for_me'.
    let targetKind: String
    let targetId: String
    let targetTitle: String
    let amountKesGross: Int64

    @Environment(\.dismiss) private var dismiss

    @State private var vm: PaymentsViewModel? = nil
    @State private var stateObs: StateFlowObserver<PaymentsViewModelUiState>? = nil
    @State private var actionObs: StateFlowObserver<PaymentsViewModelActionState>? = nil

    @State private var presentingMpesaSubmit: PaymentDto? = nil
    #if canImport(StripePaymentSheet)
    @State private var paymentSheet: PaymentSheet? = nil
    @State private var paymentSheetResult: PaymentSheetResult? = nil
    #endif

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    EyebrowPill(label: "Pay", systemImage: "creditcard.fill")
                    EditorialHeader(
                        title: "Pay your invoice",
                        subtitle: targetTitle
                    )

                    summaryCard

                    actionBanner

                    methodChooser

                    Text("Card payments process in GBP using today's GBP→KES rate. M-Pesa payments are reviewed by an admin before your invoice clears.")
                        .font(.caption2).foregroundStyle(.secondary)
                }
                .padding(20)
            }
            .scrollContentBackground(.hidden)
            .liquidBackdrop()
            .navigationTitle("Pay")
            .glassNavigationBar(displayMode: .inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
            }
            .sheet(item: $presentingMpesaSubmit) { payment in
                MpesaSubmitSheet(
                    payment: payment,
                    onSubmit: { sms in
                        vm?.submitMpesaConfirmation(paymentId: payment.id, messageRaw: sms)
                        presentingMpesaSubmit = nil
                    }
                )
            }
            .task { bootstrap() }
            .onChange(of: actionStateKey) { _, _ in handleActionChange() }
        }
        .glassSheet(detents: [.large, .medium])
    }

    // MARK: - Subviews

    private var summaryCard: some View {
        let credit = creditBalanceKes
        let willApply = min(credit, amountKesGross)
        let due = amountKesGross - willApply
        return SoftCard {
            VStack(alignment: .leading, spacing: 10) {
                row("Invoice", "KES \(formatKes(amountKesGross))", emphasis: .ink)
                if credit > 0 {
                    row("Your credit",  "− KES \(formatKes(willApply))", emphasis: .green)
                    row("Remaining credit after",
                        "KES \(formatKes(credit - willApply))", emphasis: .secondary)
                    Divider().background(Brand.ink.opacity(0.08))
                }
                row("Amount due now", "KES \(formatKes(due))", emphasis: .orange, bold: true)
            }
        }
    }

    private func row(_ label: String, _ value: String, emphasis: RowEmphasis = .ink, bold: Bool = false) -> some View {
        HStack {
            Text(label).font(.subheadline).foregroundStyle(emphasis == .secondary ? .secondary : .primary)
            Spacer()
            Text(value)
                .font(bold ? .title3.weight(.heavy) : .subheadline.weight(.semibold))
                .foregroundStyle(emphasis.color)
        }
    }

    private enum RowEmphasis { case ink, orange, green, secondary
        var color: Color {
            switch self {
            case .ink: return Brand.ink
            case .orange: return Brand.orange
            case .green: return .green
            case .secondary: return .secondary
            }
        }
    }

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case is PaymentsViewModelActionStateCreating, is PaymentsViewModelActionStateSubmitting:
            ProgressView().frame(maxWidth: .infinity)
        case let done as PaymentsViewModelActionStateDone:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Done", message: done.message)
        case let err as PaymentsViewModelActionStateError:
            ErrorBanner(title: "Couldn't proceed", message: err.message)
        default:
            EmptyView()
        }
    }

    @ViewBuilder
    private var methodChooser: some View {
        let due = amountKesGross - min(creditBalanceKes, amountKesGross)
        if due == 0 {
            // Credit fully covers — single button to confirm.
            Button {
                vm?.create(targetKind: targetKind, targetId: targetId, method: "stripe", applyCredit: true)
            } label: {
                Label("Apply credit & confirm", systemImage: "checkmark.circle.fill")
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
        } else {
            VStack(spacing: 10) {
                // PR F: per-environment kill-switches via /api/payments/methods.
                // Hide a button entirely when its method is disabled in this env.
                // If both end up disabled (misconfig), surface a clear note.
                if !methodStripeEnabled && !methodMpesaEnabled {
                    CalloutBanner(
                        tint: Color.red.opacity(0.10),
                        icon: "exclamationmark.triangle.fill",
                        title: "Payments unavailable",
                        message: "No payment methods are enabled right now. Please contact support."
                    )
                }
                if methodStripeEnabled {
                    Button {
                        vm?.create(targetKind: targetKind, targetId: targetId, method: "stripe", applyCredit: true)
                    } label: {
                        methodLabel(icon: "creditcard.fill",
                                    title: "Pay with card",
                                    subtitle: "Stripe · KES → GBP at checkout")
                    }
                    .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                }
                if methodMpesaEnabled {
                    Button {
                        vm?.create(targetKind: targetKind, targetId: targetId, method: "mpesa", applyCredit: true)
                    } label: {
                        methodLabel(icon: "phone.fill",
                                    title: "Pay via M-Pesa",
                                    subtitle: "Till \(methodMpesaTill) · admin review")
                    }
                    .buttonStyle(GlassSheenButtonStyle(
                        fill: Color.green.opacity(0.18),
                        foreground: .green
                    ))
                }
            }
        }
    }

    private func methodLabel(icon: String, title: String, subtitle: String) -> some View {
        // Bumped vertical padding so the cards have proper tap-target size
        // and don't feel cramped against the corner radius (the Glass
        // sheen button style only adds horizontal inset by default).
        HStack(spacing: 14) {
            Image(systemName: icon).font(.title2)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.subheadline.weight(.heavy)).tracking(1)
                Text(subtitle).font(.caption2).opacity(0.85)
            }
            Spacer()
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 6)
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
    }

    // MARK: - Action handling

    /// Hashable key for .onChange — the action sealed-class instance changes
    /// type when state transitions, which is enough to drive a re-render.
    private var actionStateKey: String {
        guard let v = actionObs?.value else { return "nil" }
        return String(describing: type(of: v))
    }

    private func handleActionChange() {
        guard let action = actionObs?.value else { return }
        switch action {
        #if canImport(StripePaymentSheet)
        case let stripeReady as PaymentsViewModelActionStateStripeReady:
            presentStripeSheet(clientSecret: stripeReady.clientSecret, payment: stripeReady.payment)
        #endif
        case let mpesa as PaymentsViewModelActionStateMpesaReady:
            presentingMpesaSubmit = mpesa.payment
        default:
            break
        }
    }

    #if canImport(StripePaymentSheet)
    @MainActor
    private func presentStripeSheet(clientSecret: String, payment: PaymentDto) {
        guard let pk = publishableKey, !pk.isEmpty else {
            vm?.resetAction()
            return
        }
        // Swift 6 strict concurrency: StripeAPI.defaultPublishableKey is a
        // class var (not Sendable). Setting it from a @MainActor context
        // is fine — keep all Stripe init on the main actor.
        StripeAPI.defaultPublishableKey = pk
        var config = PaymentSheet.Configuration()
        config.merchantDisplayName = "Thapsus Cargo"
        config.allowsDelayedPaymentMethods = false
        // No Apple Pay (per project decision) → leave config.applePay nil.
        // Suppress Stripe Link inline-signup. We don't integrate Link, and
        // building `LinkInlineSignupElement` was the dominant cost in the
        // 306ms PaymentSheet construction hang (report 2026-05-03 13:55).
        config.link.display = .never
        let sheet = PaymentSheet(paymentIntentClientSecret: clientSecret, configuration: config)
        self.paymentSheet = sheet

        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.windows.first?.rootViewController })
            .first else { return }
        let presenter = topMost(of: root)
        sheet.present(from: presenter) { result in
            switch result {
            case .completed:
                // SKIE doesn't bridge Kotlin default arguments
                // (feedback_skie_default_args.md) — pass the message explicitly.
                vm?.markStripeCompleted(message: "Payment received. Thanks!")
            case .canceled:
                vm?.resetAction()
            case .failed:
                vm?.resetAction()
            }
        }
    }

    private func topMost(of vc: UIViewController) -> UIViewController {
        var top = vc
        while let presented = top.presentedViewController { top = presented }
        return top
    }
    #endif

    // MARK: - Helpers

    private var creditBalanceKes: Int64 {
        guard let s = stateObs?.value as? PaymentsViewModelUiStateReady else { return 0 }
        return s.creditBalanceKes
    }

    private var publishableKey: String? {
        (stateObs?.value as? PaymentsViewModelUiStateReady)?.publishableKey
    }

    /// PR F kill-switches. Default to enabled when the state isn't loaded
    /// yet so the buttons render optimistically; the server enforces too.
    private var methodStripeEnabled: Bool {
        (stateObs?.value as? PaymentsViewModelUiStateReady)?.stripeEnabled ?? true
    }
    private var methodMpesaEnabled: Bool {
        (stateObs?.value as? PaymentsViewModelUiStateReady)?.mpesaEnabled ?? true
    }
    private var methodMpesaTill: String {
        (stateObs?.value as? PaymentsViewModelUiStateReady)?.mpesaTillNumber ?? "5530500"
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
        stateObs  = StateFlowObserver(initial: model.state.value)  { model.state }
        actionObs = StateFlowObserver(initial: model.action.value) { model.action }
    }
}
