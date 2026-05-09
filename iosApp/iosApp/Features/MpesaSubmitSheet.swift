// MpesaSubmitSheet.swift
// Customer pastes their M-Pesa confirmation SMS after paying via the
// Till. Server parses the 10-char reference + Ksh amount + sender
// phone (regex in utils/mpesaParser.js) and flips the payment row to
// 'awaiting_review'. Admin then approves via AdminPaymentsView.
//
// Until the customer pastes the SMS, the payment sits in 'pending' —
// we stash the till + reference here so they can copy them out.

import SwiftUI
import ThapsusShared

struct MpesaSubmitSheet: View {
    @Environment(\.dismiss) private var dismiss
    let payment: PaymentDto
    let onSubmit: (String) -> Void

    @State private var sms: String = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    EyebrowPill(label: "M-Pesa", systemImage: "phone.fill")
                    EditorialHeader(
                        title: "Pay then paste",
                        subtitle: "Send to Till 5530500 with your payment reference, then paste the M-Pesa confirmation SMS."
                    )

                    instructionsCard
                    smsCard
                    submitButton
                }
                .padding(20)
            }
            .scrollContentBackground(.hidden)
            .liquidBackdrop()
            .navigationTitle("M-Pesa")
            .glassNavigationBar(displayMode: .inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
        .glassSheet(detents: [.large])
    }

    private var instructionsCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 12) {
                row("Till", "5530500", canCopy: true)
                row("Reference", payment.id, canCopy: true)
                row("Amount", "KES \(payment.amountDueKes)")
                Divider().background(Brand.ink.opacity(0.08))
                Text("On your phone: Lipa na M-Pesa → Buy Goods and Services → Till **5530500** → enter the amount above → put the reference in the account number field.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    @ScaledMetric(relativeTo: .caption2) private var rowLabelSize: CGFloat = 9

    private func row(_ label: String, _ value: String, canCopy: Bool = false) -> some View {
        HStack {
            Text(label.uppercased()).font(.system(size: rowLabelSize, weight: .heavy)).tracking(2)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value).font(.subheadline.monospaced().weight(.semibold))
                .foregroundStyle(Brand.ink)
            if canCopy {
                Button {
                    UIPasteboard.general.string = value
                } label: {
                    Image(systemName: "doc.on.doc").font(.caption)
                }
                .tint(Brand.orange)
                .accessibilityLabel("Copy \(label.lowercased())")
            }
        }
    }

    private var smsCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("M-Pesa SMS confirmation").font(.headline).foregroundStyle(Brand.ink)
                Text("Paste the full SMS — must include the 10-character reference (e.g. TI82A4XYZ1) and the Ksh amount.")
                    .font(.caption2).foregroundStyle(.secondary)
                TextEditor(text: $sms)
                    .frame(minHeight: 140)
                    .padding(8)
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
    }

    private var submitButton: some View {
        Button {
            onSubmit(sms.trimmingCharacters(in: .whitespacesAndNewlines))
        } label: {
            Label("Submit for review", systemImage: "paperplane.fill")
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
        }
        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
        .disabled(sms.trimmingCharacters(in: .whitespacesAndNewlines).count < 20)
    }
}
