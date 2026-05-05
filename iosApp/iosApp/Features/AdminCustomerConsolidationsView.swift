// AdminCustomerConsolidationsView.swift
// Admin tool for the Phase 2 two-tier consolidation lifecycle. Lists every
// customer-consolidation (filterable by status) and surfaces the next
// admin action inline:
//   pending  → "Set invoice"   (prompts for amount)
//   invoiced → "Mark paid"
//   paid     → "Attach to shipping"  (prompts for shipping consolidation id)
//   shipped  → no action
//
// Creation flow (selecting a user's parcels and minting a new
// customer-consolidation) is intentionally not in the iOS admin yet —
// admin can drive POST /api/customer-consolidations from the webapp or
// curl while this screen handles the existing-row lifecycle. A richer
// "create from received parcels" sheet is a Phase 2.1 follow-up.

import SwiftUI
import ThapsusShared

struct AdminCustomerConsolidationsView: View {
    @State private var rows: [CustomerConsolidationDto] = []
    @State private var statusFilter: String? = nil
    @State private var loading: Bool = false
    @State private var errorMessage: String?

    @State private var pendingAction: PendingAction?
    @State private var amountText: String = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                EditorialHeader(
                    eyebrow: "Admin · Phase 2",
                    title: "Customer\nconsolidations",
                    subtitle: "Per-user invoices and the bridge to shipping batches."
                )

                statusPicker

                if loading {
                    ProgressView().frame(maxWidth: .infinity).padding(.vertical, 24)
                } else if let errorMessage {
                    ErrorBanner(title: "Couldn't load", message: errorMessage)
                } else if rows.isEmpty {
                    SoftCard {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("No customer consolidations yet").font(.headline).foregroundStyle(Brand.ink)
                            Text("Use POST /api/customer-consolidations to mint one. They show up here for invoice + shipping management.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                } else {
                    ForEach(rows, id: \.id) { row in
                        AdminConsolidationCard(
                            row: row,
                            onSetInvoice: { pendingAction = .invoice(row) },
                            onMarkPaid: { run { try await markPaid(row.id) } },
                            onAttachShipping: { pendingAction = .attach(row) }
                        )
                    }
                }
            }
            .padding(20)
        }
        .navigationTitle("Customer Consolidations")
        .glassNavigationBar(displayMode: .inline)
        .scrollContentBackground(.hidden)
        .appBackground()
        .refreshable { await reload() }
        .task { await reload() }
        .sheet(item: $pendingAction) { action in
            switch action {
            case .invoice(let row):
                AmountInputSheet(
                    title: "Set invoice",
                    label: "Amount (\(row.invoiceCurrency))",
                    placeholder: "5000",
                    consolidationId: row.id,
                    onSubmit: { amount in
                        pendingAction = nil
                        run { try await setInvoice(row.id, amount: amount) }
                    },
                    onCancel: { pendingAction = nil }
                )
            case .attach(let row):
                ShippingIdInputSheet(
                    title: "Attach to shipping",
                    onSubmit: { shippingId in
                        pendingAction = nil
                        run { try await attachToShipping(row.id, shippingId: shippingId) }
                    },
                    onCancel: { pendingAction = nil }
                )
            }
        }
    }

    @ViewBuilder
    private var statusPicker: some View {
        let options: [(String?, String)] = [
            (nil, "All"),
            ("pending", "Pending"),
            ("invoiced", "Invoiced"),
            ("paid", "Paid"),
            ("shipped", "Shipped"),
        ]
        HStack {
            ForEach(options, id: \.1) { (key, label) in
                Button {
                    statusFilter = key
                    Task { await reload() }
                } label: {
                    Text(label)
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(
                            Capsule().fill(statusFilter == key ? Brand.orange : Brand.cream.opacity(0.6))
                        )
                        .foregroundStyle(statusFilter == key ? .white : Brand.ink)
                }
                .buttonStyle(.plain)
            }
            Spacer()
        }
    }

    @MainActor
    private func reload() async {
        loading = true
        errorMessage = nil
        do {
            let repo = ThapsusSdk.shared.customerConsolidations()
            // SKIE doesn't bridge Kotlin defaults — every parameter
            // must be supplied explicitly. Only `status` is filterable
            // from this screen; user/shipping filters are nil.
            rows = try await repo.listForAdmin(
                userId: nil,
                status: statusFilter,
                shippingConsolidationId: nil
            )
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }

    private func run(_ block: @escaping () async throws -> Void) {
        Task {
            do {
                try await block()
                await reload()
            } catch {
                await MainActor.run { errorMessage = error.localizedDescription }
            }
        }
    }

    private func setInvoice(_ id: String, amount: Double) async throws {
        _ = try await ThapsusSdk.shared.customerConsolidations()
            .setInvoice(id: id, amount: amount, currency: nil)
    }

    private func markPaid(_ id: String) async throws {
        _ = try await ThapsusSdk.shared.customerConsolidations().markPaid(id: id)
    }

    private func attachToShipping(_ ccId: String, shippingId: String) async throws {
        _ = try await ThapsusSdk.shared.customerConsolidations()
            .attachToShipping(
                shippingConsolidationId: shippingId,
                customerConsolidationIds: [ccId]
            )
    }
}

private enum PendingAction: Identifiable {
    case invoice(CustomerConsolidationDto)
    case attach(CustomerConsolidationDto)

    var id: String {
        switch self {
        case .invoice(let r): return "invoice-\(r.id)"
        case .attach(let r):  return "attach-\(r.id)"
        }
    }
}

private struct AdminConsolidationCard: View {
    let row: CustomerConsolidationDto
    let onSetInvoice: () -> Void
    let onMarkPaid: () -> Void
    let onAttachShipping: () -> Void

    var body: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(row.userName ?? row.userEmail ?? row.userId)
                        .font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    statusBadge
                }
                if let email = row.userEmail, row.userName != nil {
                    Text(email).font(.caption).foregroundStyle(.secondary)
                }
                if let boxedCount = row.parcelCount {
                    // SKIE bridges Kotlin Int? as KotlinInt? — unwrap to
                    // a Swift Int via Int(truncating:) before comparing
                    // or interpolating (KotlinInt doesn't conform to
                    // BinaryInteger under Swift 6 strict mode).
                    let count = Int(truncating: boxedCount)
                    Text("\(count) parcel\(count == 1 ? "" : "s")")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if let amount = row.invoiceAmount {
                    // KotlinDouble? → Swift Double via .doubleValue;
                    // formatAmount(_:) takes a Swift primitive.
                    Text("\(row.invoiceCurrency) \(formatAmount(amount.doubleValue))")
                        .font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                }
                actionButton
            }
        }
    }

    @ViewBuilder
    private var actionButton: some View {
        switch row.status {
        case "pending":
            Button("Set invoice", action: onSetInvoice)
                .buttonStyle(.borderedProminent).tint(Brand.orange)
        case "invoiced":
            Button("Mark paid", action: onMarkPaid)
                .buttonStyle(.borderedProminent).tint(.green)
        case "paid":
            Button("Attach to shipping", action: onAttachShipping)
                .buttonStyle(.borderedProminent).tint(Brand.ink)
        default:
            EmptyView()
        }
    }

    @ViewBuilder
    private var statusBadge: some View {
        let (label, color): (String, Color) = {
            switch row.status {
            case "pending":  return ("PENDING", .gray)
            case "invoiced": return ("INVOICED", .orange)
            case "paid":     return ("PAID", .green)
            case "shipped":  return ("SHIPPED", .blue)
            default:         return (row.status.uppercased(), .gray)
            }
        }()
        Text(label)
            .font(.system(size: 9, weight: .heavy)).tracking(2)
            .foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Capsule().fill(color.opacity(0.16)))
    }

    private func formatAmount(_ value: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal
        return f.string(from: NSNumber(value: value)) ?? "\(Int(value))"
    }
}

private struct AmountInputSheet: View {
    let title: String
    let label: String
    let placeholder: String
    /// Customer-consolidation id — when supplied, the sheet hits
    /// /suggested-invoice on appear and prefills `text` with the
    /// computed total so the admin doesn't have to add it up by hand.
    let consolidationId: String?
    let onSubmit: (Double) -> Void
    let onCancel: () -> Void

    @State private var text: String = ""
    @State private var loadingSuggestion: Bool = false
    @State private var suggestion: SuggestedInvoiceResponse?

    var body: some View {
        NavigationStack {
            Form {
                if loadingSuggestion {
                    Section { ProgressView().frame(maxWidth: .infinity) }
                } else if let suggestion, !suggestion.breakdown.isEmpty {
                    Section("Suggested total") {
                        HStack {
                            Text("\(suggestion.currency) \(formatAmount(suggestion.total))")
                                .font(.title3.weight(.bold))
                            Spacer()
                            Button("Use") {
                                text = String(format: "%.0f", suggestion.total)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(Brand.orange)
                        }
                        Text("Sum of (chargeable kg × shipping rate) + customs duty across \(suggestion.breakdown.count) parcel\(suggestion.breakdown.count == 1 ? "" : "s"). Confirm before issuing.")
                            .font(.caption2).foregroundStyle(.secondary)
                    }
                }
                Section(label) {
                    TextField(placeholder, text: $text)
                        .keyboardType(.decimalPad)
                }
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Submit") {
                        if let value = Double(text), value > 0 { onSubmit(value) }
                    }
                    .disabled(Double(text).map { $0 <= 0 } ?? true)
                }
            }
            .task {
                await loadSuggestion()
            }
        }
    }

    @MainActor
    private func loadSuggestion() async {
        guard let id = consolidationId, suggestion == nil, !loadingSuggestion else { return }
        loadingSuggestion = true
        do {
            let resp = try await ThapsusSdk.shared.customerConsolidations()
                .suggestedInvoice(id: id)
            suggestion = resp
            // Prefill the text field too — the "Use" button is then
            // optional unless the admin wants to override.
            if text.isEmpty, resp.total > 0 {
                text = String(format: "%.0f", resp.total)
            }
        } catch {
            // Suggestion is best-effort. A failure leaves the input
            // blank for the admin to enter manually.
        }
        loadingSuggestion = false
    }

    private func formatAmount(_ value: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal
        return f.string(from: NSNumber(value: value)) ?? "\(Int(value))"
    }
}

private struct ShippingIdInputSheet: View {
    let title: String
    let onSubmit: (String) -> Void
    let onCancel: () -> Void

    @State private var text: String = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Shipping consolidation ID") {
                    TextField("uuid", text: $text)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }
                Section {
                    Text("Find the id from /api/consolidations or the operator board.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Attach") {
                        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty { onSubmit(trimmed) }
                    }
                    .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
