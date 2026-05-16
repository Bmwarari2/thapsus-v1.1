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
    /// Lipana STK flow: when true, the LipanaStkSheet is mounted. Tap
    /// of "Pay via M-Pesa" with provider='lipana' flips this; the sheet
    /// handles the phone-entry → STK init → awaiting-PIN stages itself
    /// by observing the live action state passed in.
    @State private var presentingLipanaStk: Bool = false
    /// Drives the celebratory overlay when the action transitions to
    /// `Done`. Set on the same code path that produces the Done state
    /// so we know whether money has actually moved (.received) or the
    /// customer has just reported a manual transfer that admins still
    /// need to review (.submitted) — the two cases share an
    /// `ActionState.Done` on the Kotlin side but need different copy
    /// + tint + haptic on iOS so the customer's mental model stays
    /// accurate.
    @State private var pendingConfirmationVariant: PaymentConfirmationOverlay.Variant? = nil
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
                        // M-Pesa submission produces an `ActionState.Done`
                        // whose meaning is "report received, awaiting admin
                        // review" — explicitly NOT money in. Tag the
                        // pending variant BEFORE the action transitions so
                        // the overlay renders the amber clock copy when it
                        // appears (the action change handler reads this
                        // variant to decide which overlay to present).
                        pendingConfirmationVariant = .submitted
                        vm?.submitMpesaConfirmation(paymentId: payment.id, messageRaw: sms)
                        presentingMpesaSubmit = nil
                    }
                )
            }
            .sheet(isPresented: $presentingLipanaStk) {
                LipanaStkSheet(
                    targetKind:      targetKind,
                    targetId:        targetId,
                    amountKesGross:  amountKesGross,
                    prefilledPhone:  prefilledPhone,
                    actionState:     actionObs?.value,
                    mpesaTillNumber: methodMpesaTill,
                    onInitiate: { phone in
                        // STK flow ends in real money in once the webhook
                        // settles, so tag .received now — the overlay
                        // condition (variant != nil && action is Done)
                        // means it stays dormant until the poll/webhook
                        // flips status to paid.
                        pendingConfirmationVariant = .received
                        vm?.create(
                            targetKind:  targetKind,
                            targetId:    targetId,
                            method:      "mpesa",
                            applyCredit: true,
                            phone:       phone
                        )
                    },
                    onFallbackManual: { till in
                        // Customer couldn't complete the PIN prompt —
                        // hand off to the legacy paste-the-SMS sheet.
                        // Switch the variant since the manual flow lands
                        // in 'awaiting_review', not 'paid'.
                        pendingConfirmationVariant = .submitted
                        vm?.fallbackToManualMpesa(tillNumber: till)
                        presentingLipanaStk = false
                    },
                    onCancel: {
                        vm?.resetAction()
                        pendingConfirmationVariant = nil
                    }
                )
            }
            .task { bootstrap() }
            .onChange(of: actionStateKey) { _, _ in handleActionChange() }
        }
        .glassSheet(detents: [.fraction(0.85), .large])
        .overlay {
            // Overlay sits OUTSIDE the NavigationStack so it covers the
            // sheet's full chrome (including the nav bar) — that's the
            // whole point of the redesign, the customer must not be left
            // looking at the same Pay screen wondering if the tap landed.
            if let variant = pendingConfirmationVariant,
               actionObs?.value is PaymentsViewModelActionStateDone {
                PaymentConfirmationOverlay(
                    variant: variant,
                    subtitle: confirmationSubtitle(for: variant),
                    amountKesGross: amountKesGross,
                    onDismiss: {
                        // Reset the VM action so a re-presentation of
                        // this sheet from elsewhere doesn't immediately
                        // re-show the overlay; then close the sheet so
                        // the caller (BFM accept, consolidation invoice
                        // card, …) refreshes its own state.
                        vm?.resetAction()
                        pendingConfirmationVariant = nil
                        dismiss()
                    }
                )
                .transition(.opacity)
                .zIndex(1)
            }
        }
        .animation(.easeOut(duration: 0.25), value: pendingConfirmationVariant != nil
                   && actionObs?.value is PaymentsViewModelActionStateDone)
    }

    /// Server-supplied message lives in `ActionState.Done.message` and is
    /// already customer-friendly ("Payment received. Thanks!" /
    /// "Submitted for review." / "Covered by your credit. Thanks!").
    /// Fall back to a sensible default per variant if the message ever
    /// arrives empty so the overlay never shows blank space.
    private func confirmationSubtitle(for variant: PaymentConfirmationOverlay.Variant) -> String {
        if let done = actionObs?.value as? PaymentsViewModelActionStateDone,
           !done.message.isEmpty {
            return done.message
        }
        switch variant {
        case .received:
            return "Your payment has cleared. The invoice has been settled."
        case .submitted:
            return "We've got your M-Pesa confirmation. An admin will review and clear the invoice shortly."
        }
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
        case let err as PaymentsViewModelActionStateError:
            ErrorBanner(title: "Couldn't proceed", message: err.message)
        default:
            // ActionState.Done is handled by the full-screen
            // PaymentConfirmationOverlay rendered by the parent — the
            // small inline banner used to live here, but customers
            // were missing it on the busy Pay screen and re-paying.
            EmptyView()
        }
    }

    @ViewBuilder
    private var methodChooser: some View {
        let due = amountKesGross - min(creditBalanceKes, amountKesGross)
        if due == 0 {
            // Credit fully covers — single button to confirm. The
            // server short-circuits to ActionState.Done immediately
            // (no Stripe round-trip, no M-Pesa ticket), so this is a
            // real settlement path → received variant.
            Button {
                pendingConfirmationVariant = .received
                vm?.create(targetKind: targetKind, targetId: targetId, method: "stripe", applyCredit: true, phone: nil)
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
                        vm?.create(targetKind: targetKind, targetId: targetId, method: "stripe", applyCredit: true, phone: nil)
                    } label: {
                        methodLabel(icon: "creditcard.fill",
                                    title: "Pay with card",
                                    subtitle: "Stripe · KES → GBP at checkout")
                    }
                    .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                }
                if methodMpesaEnabled {
                    if methodMpesaProvider == "lipana" {
                        Button {
                            // STK flow: open the LipanaStkSheet which
                            // captures the phone, fires create() with
                            // it, and listens to the action state to
                            // drive the awaiting-PIN stage.
                            presentingLipanaStk = true
                        } label: {
                            methodLabel(icon: "phone.fill",
                                        title: "Pay via M-Pesa",
                                        subtitle: "STK push · enter PIN on your phone")
                        }
                        .buttonStyle(GlassSheenButtonStyle(
                            fill: Color.green.opacity(0.18),
                            foreground: .green
                        ))
                    } else {
                        Button {
                            vm?.create(targetKind: targetKind, targetId: targetId, method: "mpesa", applyCredit: true, phone: nil)
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
            // Could be a fresh manual-mpesa create, or the customer
            // tapping "Pay manually instead" inside LipanaStkSheet —
            // either way, hand off to MpesaSubmitSheet.
            presentingLipanaStk = false
            presentingMpesaSubmit = mpesa.payment
        case is PaymentsViewModelActionStateDone where presentingLipanaStk:
            // STK landed paid → close the sheet so the parent's
            // PaymentConfirmationOverlay (.received) can show.
            presentingLipanaStk = false
        case is PaymentsViewModelActionStateError where presentingLipanaStk:
            // Poll timed out / failed — close the STK sheet, the
            // parent's actionBanner renders the error.
            presentingLipanaStk = false
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
                // Stripe is real-money settlement → received variant.
                // Tag BEFORE marking the VM done so the overlay's
                // .onChange-driven render reads a non-nil variant on
                // the same tick the Done state lands.
                pendingConfirmationVariant = .received
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
    /// Migration 038: 'manual' (paste-the-SMS) or 'lipana' (STK Push).
    /// Drives which M-Pesa sheet PayInvoiceView mounts. Older servers
    /// (no provider field) report 'manual' so the button still works.
    private var methodMpesaProvider: String {
        (stateObs?.value as? PaymentsViewModelUiStateReady)?.mpesaProvider ?? "manual"
    }
    /// Phone to seed LipanaStkSheet with. Currently nil — we don't yet
    /// pull the user's saved phone into PaymentsViewModel state. The
    /// sheet's input field is the canonical capture point.
    private var prefilledPhone: String? { nil }

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
