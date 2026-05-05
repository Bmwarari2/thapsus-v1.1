// OpsBuyForMeQueueView.swift
// Operator-facing queue for Buy-for-me concierge requests. Mirrors the
// webapp /ops/buy-for-me page: lists pending_quote / quoted / paid /
// rejected orders, lets the operator open a Send-quote sheet that calls
// `POST /api/buy-for-me/:id/quote` (server fans out the customer's
// "quote ready" email automatically).

import SwiftUI
import ThapsusShared

struct OpsBuyForMeQueueView: View {
    @State private var vm: OpsBuyForMeViewModel?
    @State private var ordersObs: StateFlowObserver<[BuyForMeOrderDto]>?
    @State private var actionObs: StateFlowObserver<OpsBuyForMeViewModelActionState>?

    @State private var quotingFor: BuyForMeOrderDto?
    @State private var draftEstimate: String = ""
    @State private var draftMarkup:   String = "10"
    @State private var draftNotes:    String = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                EyebrowPill(label: "Operations", systemImage: "wand.and.stars")
                EditorialHeader(title: "Buy-for-me queue",
                                subtitle: "Quote concierge requests · accepts pay from wallet.")

                actionBanner

                if let orders = ordersObs?.value, !orders.isEmpty {
                    ForEach(orders, id: \.id) { o in
                        orderCard(o)
                    }
                } else {
                    CrystalCard {
                        Text("No pending requests.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Buy-for-me")
        .glassNavigationBar()
        .sheet(isPresented: Binding(
            get: { quotingFor != nil },
            set: { if !$0 { quotingFor = nil } }
        )) {
            if let order = quotingFor {
                quoteSheet(for: order)
            }
        }
        .task { bootstrap() }
    }

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case let done as OpsBuyForMeViewModelActionStateDone:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Done", message: done.message)
        case let err as OpsBuyForMeViewModelActionStateError:
            ErrorBanner(title: "Couldn't send", message: err.message)
        case is OpsBuyForMeViewModelActionStateInFlight:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    private func orderCard(_ o: BuyForMeOrderDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    statusBadge(o.status)
                    Spacer()
                    if let g = o.estimateGbp?.doubleValue {
                        Text("£\(String(format: "%.2f", g)) · \(Int(o.markupPct))%")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Brand.ink)
                    }
                }
                Text(o.itemName).font(.headline).foregroundStyle(Brand.ink)
                retailerLink(o.retailerUrl)
                if let s = o.size, !s.isEmpty {
                    Text("Size: \(s) · Qty: \(o.qty)")
                        .font(.caption).foregroundStyle(.secondary)
                } else {
                    Text("Qty: \(o.qty)").font(.caption).foregroundStyle(.secondary)
                }
                if let name = o.name ?? o.email {
                    Text(name).font(.caption.weight(.semibold)).foregroundStyle(Brand.ink)
                }
                if let n = o.notes, !n.isEmpty {
                    Text("\"\(n)\"").font(.caption).italic().foregroundStyle(.secondary)
                }
                if o.status == "rejected", let reason = o.customerDecisionReason, !reason.isEmpty {
                    Text("Customer rejected: \(reason)")
                        .font(.caption).foregroundStyle(.red)
                }
                if o.status == "paid", let tn = o.parcelTrackingNumber {
                    HStack(spacing: 6) {
                        Image(systemName: "shippingbox.fill")
                            .font(.caption2).foregroundStyle(.green)
                        Text("Pre-registered as ").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                            + Text(tn).font(.caption.monospaced().weight(.bold)).foregroundStyle(Brand.ink)
                    }
                    .padding(.vertical, 4).padding(.horizontal, 8)
                    .background(Capsule().fill(Color.green.opacity(0.1)))
                }
                Button {
                    quotingFor = o
                    if let g = o.estimateGbp?.doubleValue {
                        draftEstimate = String(format: "%.2f", g)
                    } else {
                        draftEstimate = ""
                    }
                    draftMarkup = String(Int(o.markupPct))
                    draftNotes  = o.notes ?? ""
                } label: {
                    Label(buttonLabel(for: o.status), systemImage: "paperplane.fill")
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.ink, foreground: .white))
            }
        }
    }

    private func quoteSheet(for order: BuyForMeOrderDto) -> some View {
        NavigationStack {
            Form {
                Section {
                    Text(order.itemName).font(.headline)
                    Text(order.email ?? "—").font(.caption).foregroundStyle(.secondary)
                }
                Section("Quote") {
                    HStack {
                        Text("Estimate (GBP)")
                        Spacer()
                        TextField("0.00", text: $draftEstimate)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 110)
                    }
                    HStack {
                        Text("Service markup %")
                        Spacer()
                        TextField("10", text: $draftMarkup)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 80)
                    }
                }
                Section("Note for customer") {
                    TextEditor(text: $draftNotes).frame(minHeight: 100)
                }
            }
            .navigationTitle("Send quote")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { quotingFor = nil }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Send") { submitQuote(for: order) }
                        .disabled(Double(draftEstimate) == nil)
                }
            }
        }
    }

    private func submitQuote(for order: BuyForMeOrderDto) {
        guard let estimate = Double(draftEstimate), estimate > 0 else { return }
        let markup = Double(draftMarkup) ?? 10
        vm?.submitQuote(
            id: order.id,
            estimateGbp: estimate,
            markupPct: markup,
            notes: draftNotes.isEmpty ? nil : draftNotes
        )
        quotingFor = nil
    }

    private func buttonLabel(for status: String) -> String {
        switch status {
        case "rejected": return "Re-quote"
        case "quoted":   return "Edit quote"
        case "paid":     return "View"
        default:         return "Send quote"
        }
    }

    /// Render the customer's retailer URL as a tappable link when it
    /// parses as a valid URL; fall back to plain monospaced text
    /// otherwise so a malformed URL (or empty string) still displays
    /// without breaking the row layout. Tapping opens the page in the
    /// default browser via `Link` — the URL stays visible inline so
    /// the operator can still see what was submitted.
    @ViewBuilder
    private func retailerLink(_ raw: String) -> some View {
        if let url = URL(string: raw), url.scheme?.hasPrefix("http") == true {
            Link(destination: url) {
                HStack(spacing: 4) {
                    Image(systemName: "link")
                        .font(.caption2)
                    Text(raw)
                        .font(.caption2.monospaced())
                        .lineLimit(1).truncationMode(.middle)
                }
                .foregroundStyle(Brand.orange)
            }
        } else {
            Text(raw)
                .font(.caption2.monospaced()).foregroundStyle(.secondary)
                .lineLimit(1).truncationMode(.middle)
        }
    }

    private func statusBadge(_ status: String) -> some View {
        let map: [String: Color] = [
            "pending_quote": .orange, "quoted": .blue, "paid": .green,
            "purchased": .purple, "received": .teal, "shipped": .green,
            "cancelled": .gray, "rejected": .red,
        ]
        let color = map[status] ?? .secondary
        return Text(status.replacingOccurrences(of: "_", with: " ").uppercased())
            .font(.system(size: 9, weight: .heavy)).tracking(2)
            .foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Capsule().fill(color.opacity(0.16)))
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.opsBuyForMeViewModel()
        vm = model
        ordersObs = StateFlowObserver(initial: model.orders.value) { model.orders }
        actionObs = StateFlowObserver(initial: model.action.value) { model.action }
    }
}
