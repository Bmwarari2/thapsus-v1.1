// LipanaStkSheet.swift
// Drives the Lipana M-Pesa STK Push flow (server PR feat/lipana-mpesa-stk).
// Replaces the manual MpesaSubmitSheet for environments where the
// server reports `mpesa.provider == "lipana"`.
//
// Three stages inside one glassSheet:
//   1. .phone     — customer types their M-Pesa number; we kick off
//                   the STK on tap. Required field, no "(optional)".
//   2. .sending   — VM is in ActionState.Creating; show a spinner.
//   3. .awaiting  — VM is in ActionState.LipanaStkInflight; the
//                   customer's phone is showing the PIN prompt.
//                   The KMP poller drives the rest — we just show
//                   the waiting copy + a "Pay manually instead"
//                   link that calls fallbackToManualMpesa() on the VM.
//
// Success / failure leave this sheet via the parent's
// PaymentConfirmationOverlay (.received variant) or ErrorBanner.

import SwiftUI
import ThapsusShared

struct LipanaStkSheet: View {
    @Environment(\.dismiss) private var dismiss

    /// Pay-flow context — same as MpesaSubmitSheet so PayInvoiceView can
    /// swap between the two without rewiring callers.
    let targetKind: String
    let targetId: String
    let amountKesGross: Int64
    /// Pre-filled when the AccountSnapshot has a phone on file.
    let prefilledPhone: String?
    /// Live action state from the parent's VM — drives the stage switch.
    let actionState: PaymentsViewModelActionState?
    /// Server-reported Till number for the manual-fallback hand-off.
    let mpesaTillNumber: String

    let onInitiate: (String) -> Void
    let onFallbackManual: (String) -> Void
    let onCancel: () -> Void

    @State private var phone: String = ""

    private enum Stage { case phone, sending, awaiting }

    private var stage: Stage {
        switch actionState {
        case is PaymentsViewModelActionStateCreating:        return .sending
        case is PaymentsViewModelActionStateLipanaStkInflight: return .awaiting
        default: return .phone
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    EyebrowPill(label: "M-Pesa STK", systemImage: "phone.fill")
                    EditorialHeader(
                        title: stage == .awaiting ? "Check your phone" : "Pay with M-Pesa",
                        subtitle: subtitle
                    )

                    summaryCard

                    switch stage {
                    case .phone:    phoneCard
                    case .sending:  sendingCard
                    case .awaiting: awaitingCard
                    }

                    if stage == .awaiting {
                        manualFallbackLink
                    }
                }
                .padding(20)
            }
            .scrollContentBackground(.hidden)
            .liquidBackdrop()
            .navigationTitle("M-Pesa")
            .glassNavigationBar(displayMode: .inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { onCancel(); dismiss() }
                }
            }
            .onAppear {
                if phone.isEmpty, let p = prefilledPhone, !p.isEmpty {
                    phone = formatForDisplay(p)
                }
            }
        }
        .glassSheet(detents: [.large])
    }

    // MARK: - Subviews

    private var subtitle: String {
        switch stage {
        case .phone:    return "We'll send a payment prompt to your phone — no SMS to paste."
        case .sending:  return "Sending request to your phone…"
        case .awaiting: return "Open the M-Pesa prompt on your phone and enter your PIN to complete KES \(formatKes(amountKesGross))."
        }
    }

    private var summaryCard: some View {
        SoftCard {
            HStack {
                Text("Amount due").font(.subheadline)
                Spacer()
                Text("KES \(formatKes(amountKesGross))")
                    .font(.title3.weight(.heavy))
                    .foregroundStyle(Brand.orange)
            }
        }
    }

    private var phoneCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 12) {
                LGTextField(
                    label: "M-Pesa number",
                    placeholder: "0712 345 678",
                    text: $phone,
                    keyboard: .numberPad,
                    capitalization: .never,
                    leadingIcon: "phone.fill"
                )
                Text("Use the Safaricom number registered to your M-Pesa account.")
                    .font(.caption2).foregroundStyle(.secondary)

                Button {
                    onInitiate(phone.trimmingCharacters(in: .whitespacesAndNewlines))
                } label: {
                    Label("Send STK push", systemImage: "paperplane.fill")
                        .frame(maxWidth: .infinity)
                        .contentShape(Rectangle())
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                .disabled(!isPhoneValid)
            }
        }
    }

    private var sendingCard: some View {
        CrystalCard {
            VStack(spacing: 14) {
                ProgressView().controlSize(.large)
                Text("Sending STK push…")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
        }
    }

    private var awaitingCard: some View {
        CrystalCard {
            VStack(spacing: 16) {
                Image(systemName: "iphone.gen3.radiowaves.left.and.right")
                    .font(.system(size: 44, weight: .light))
                    .foregroundStyle(Brand.orange)
                    .symbolEffect(.pulse, options: .repeating)
                VStack(spacing: 6) {
                    Text("Awaiting M-Pesa PIN")
                        .font(.headline)
                        .foregroundStyle(Brand.ink)
                    Text("Don't close this screen — it'll update automatically once the payment lands.")
                        .font(.caption)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                }
                ProgressView().controlSize(.small)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 22)
        }
    }

    private var manualFallbackLink: some View {
        Button {
            onFallbackManual(mpesaTillNumber)
        } label: {
            Label("Didn't get the prompt? Pay manually instead",
                  systemImage: "arrow.uturn.right")
                .font(.footnote.weight(.semibold))
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(Brand.orange)
        .padding(.top, 4)
    }

    // MARK: - Helpers

    /// Conservative client-side gate: 9–13 digits after stripping
    /// formatting. Server is the source of truth — it normalises and
    /// rejects with `invalid_phone` if the format is wrong.
    private var isPhoneValid: Bool {
        let digits = phone.filter { $0.isNumber }
        return digits.count >= 9 && digits.count <= 13
    }

    private func formatForDisplay(_ raw: String) -> String {
        // 254712345678 → 0712 345 678 for display. We send the raw
        // string back to the server, which normalises again.
        var digits = raw.filter { $0.isNumber }
        if digits.hasPrefix("254"), digits.count == 12 { digits = "0" + digits.dropFirst(3) }
        guard digits.count == 10, digits.hasPrefix("0") else { return raw }
        let a = digits.prefix(4)
        let b = digits.dropFirst(4).prefix(3)
        let c = digits.dropFirst(7)
        return "\(a) \(b) \(c)"
    }

    private func formatKes(_ value: Int64) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.maximumFractionDigits = 0
        return f.string(from: NSNumber(value: value)) ?? "\(value)"
    }
}
