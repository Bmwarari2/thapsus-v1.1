// PendingActionsView.swift
// Unified pending-actions list — every unpaid item the customer has
// across the three "invoice" surfaces, grouped by kind:
//
//   - Buy-for-me quotes (status='quoted')  — pre-accept, billed but
//     not yet approved.
//   - Buy-for-me pending payments           — post-accept, mid-payment.
//   - Shipping/consolidation invoices       — status='invoiced'.
//
// CustomerDashboardView's `pendingActionsArea` collapses its inline
// invoice sections into a summary card once the total crosses 1;
// tapping that card pushes this view. We re-use the existing card
// structs (`CustomerInvoiceCard`, `BfmQuotedInvoiceCard`,
// `BfmPendingPaymentCard`) so the carry-over from home is seamless.
//
// Data is passed in from the host rather than re-subscribed here. The
// host already has the three lists wired to live StateFlows; passing
// snapshots keeps this view minimal and avoids a second VM instance.
// If the customer pays an item while on this screen, the payTarget
// sheet on the host updates the underlying StateFlows; SwiftUI
// recomputes the host's `pendingActionsArea` and re-renders this view
// with fresh inputs via the navigation stack.

import SwiftUI
import ThapsusShared

struct PendingActionsView: View {
    let consolidations: [CustomerConsolidationDto]
    let bfmQuoted: [BuyForMeOrderDto]
    let bfmPending: [PaymentDto]

    /// Tap handlers — the host owns the `payTarget` sheet binding and
    /// the corresponding `PayTarget.from…` factories.
    let onPickConsolidation: (CustomerConsolidationDto) -> Void
    let onPickBfmQuote: (BuyForMeOrderDto) -> Void
    let onPickBfmPending: (PaymentDto) -> Void

    private var total: Int {
        consolidations.count + bfmQuoted.count + bfmPending.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header

                if !bfmQuoted.isEmpty || !bfmPending.isEmpty {
                    bfmGroup
                }
                if !consolidations.isEmpty {
                    shippingGroup
                }
                if total == 0 {
                    emptyState
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 100)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollContentBackground(.hidden)
        .background(LiquidGlassBackground())
        .navigationTitle("Pending actions")
        .glassNavigationBar()
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(total == 0
                 ? "You're all caught up."
                 : "You have \(total) invoice\(total == 1 ? "" : "s") to settle.")
                .font(.body(15, weight: .medium))
                .foregroundStyle(LG.fg3)
        }
        .padding(.top, 8)
    }

    private var bfmGroup: some View {
        let bfmTotal = bfmQuoted.count + bfmPending.count
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                LGEyebrow(text: bfmTotal == 1
                          ? "Buy-for-me invoice due"
                          : "\(bfmTotal) buy-for-me invoices due")
                Spacer()
                LGPill(text: "Action", tone: .accent)
            }
            .padding(.leading, 4)

            ForEach(bfmQuoted, id: \.id) { order in
                BfmQuotedInvoiceCard(order: order) { onPickBfmQuote(order) }
            }
            ForEach(bfmPending, id: \.id) { payment in
                BfmPendingPaymentCard(payment: payment) { onPickBfmPending(payment) }
            }
        }
    }

    private var shippingGroup: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                LGEyebrow(text: consolidations.count == 1
                          ? "Shipping invoice due"
                          : "\(consolidations.count) shipping invoices due")
                Spacer()
                LGPill(text: "Action", tone: .accent)
            }
            .padding(.leading, 4)

            ForEach(consolidations, id: \.id) { c in
                CustomerInvoiceCard(consolidation: c) { onPickConsolidation(c) }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(Brand.orange)
            Text("All settled")
                .font(.headline)
                .foregroundStyle(Brand.ink)
            Text("Nothing waiting on you right now — check back when you have an invoice.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }
}
