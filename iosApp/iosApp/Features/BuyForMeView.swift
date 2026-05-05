// BuyForMeView.swift
// Concierge-purchase flow: paste a retailer link, our team quotes, you pay.

import SwiftUI
import ThapsusShared

struct BuyForMeView: View {
    @State private var vm: BuyForMeViewModel? = nil
    @State private var stateObserver: StateFlowObserver<BuyForMeViewModelUiState>? = nil
    @State private var actionObserver: StateFlowObserver<BuyForMeViewModelActionState>? = nil
    @State private var retailersObs: StateFlowObserver<[RetailerDto]>? = nil
    @State private var showCreate: Bool = false
    /// `nil` = sheet closed, otherwise the order whose quote we're rejecting.
    @State private var rejectingFor: BuyForMeOrderDto? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Concierge", systemImage: "wand.and.stars")
                EditorialHeader(title: "Buy for me",
                                subtitle: "Paste a UK retailer link, we buy and ship.")

                Button(action: { showCreate = true }) {
                    HStack(spacing: 8) {
                        Image(systemName: "plus.circle.fill")
                        Text("New request")
                    }
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

                actionBanner
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Buy for me")
        .glassNavigationBar()
        .sheet(isPresented: $showCreate) {
            CreateBuyForMeSheet(retailers: retailersObs?.value ?? []) { retailerId, url, item, size, qty, notes in
                vm?.create(
                    itemName:    item,
                    size:        size,
                    qty:         Int32(qty),
                    notes:       notes,
                    retailerId:  retailerId,
                    retailerUrl: url.isEmpty ? nil : url
                )
                showCreate = false
            }
        }
        // Reject-with-reason sheet. We carry the order in the @State so a
        // single .sheet bound to a Bool projection works without retroactively
        // conforming the Kotlin DTO to Identifiable.
        .sheet(isPresented: Binding(
            get: { rejectingFor != nil },
            set: { if !$0 { rejectingFor = nil } }
        )) {
            if let order = rejectingFor {
                RejectQuoteSheet(order: order) { reason in
                    vm?.reject(id: order.id, reason: reason)
                    rejectingFor = nil
                }
            }
        }
        .task { bootstrap() }
    }

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObserver?.value {
        case let done as BuyForMeViewModelActionStateDone:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Done", message: done.message)
        case let err as BuyForMeViewModelActionStateError:
            ErrorBanner(title: "Couldn't complete", message: err.message)
        case is BuyForMeViewModelActionStateInFlight:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch stateObserver?.value {
        case let loaded as BuyForMeViewModelUiStateLoaded:
            if loaded.orders.isEmpty {
                CrystalCard {
                    Text("Drop a retailer link and we'll buy on your behalf.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            } else {
                ForEach(loaded.orders, id: \.id) { order in
                    orderRow(order)
                }
            }
        case is BuyForMeViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity)
        case let err as BuyForMeViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func orderRow(_ order: BuyForMeOrderDto) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(order.itemName).font(.headline).foregroundStyle(Brand.ink)
                    Spacer()
                    statusBadge(order.status)
                }
                if let estimate = order.estimateGbp?.doubleValue {
                    Text("Quote: £ \(String(format: "%.2f", estimate)) + \(Int(order.markupPct))% markup")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Text(order.retailerUrl).font(.caption2).foregroundStyle(.secondary)
                    .lineLimit(1).truncationMode(.middle)

                if order.status == "quoted" {
                    HStack(spacing: 10) {
                        Button {
                            vm?.accept(id: order.id, reason: nil)
                        } label: {
                            Label("Accept & buy", systemImage: "checkmark.circle.fill")
                        }
                        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                        Button(role: .destructive) {
                            rejectingFor = order
                        } label: {
                            Label("Reject", systemImage: "xmark.circle")
                        }
                        .buttonStyle(.bordered)
                        .tint(.red)
                    }
                } else if order.status == "rejected", let reason = order.customerDecisionReason, !reason.isEmpty {
                    Text("You rejected: \(reason)")
                        .font(.caption).foregroundStyle(.red)
                } else if order.status == "pending_quote" {
                    Button("Cancel") { vm?.cancel(id: order.id) }
                        .buttonStyle(.bordered).tint(.secondary)
                }
            }
        }
    }

    private func statusBadge(_ status: String) -> some View {
        let map: [String: Color] = [
            "pending_quote": .orange, "quoted": .blue, "paid": .green,
            "purchased": .purple, "received": .teal, "shipped": .green,
            "cancelled": .red
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
        let model = ThapsusSdk.shared.buyForMeViewModel()
        vm = model
        model.load()
        model.loadRetailers()
        stateObserver = StateFlowObserver(initial: model.state.value) {
            model.state
        }
        actionObserver = StateFlowObserver(initial: model.action.value) {
            model.action
        }
        retailersObs = StateFlowObserver(initial: model.retailerCatalog.value) {
            model.retailerCatalog
        }
    }
}

private struct CreateBuyForMeSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var url: String = ""
    @State private var item: String = ""
    @State private var size: String = ""
    @State private var qty: Int = 1
    @State private var notes: String = ""
    /// PR 4: id of the picker selection. `nil` = no choice yet, `OTHER` = "Other".
    @State private var retailerId: String? = nil
    let retailers: [RetailerDto]
    /// onSubmit(retailerId?, url, item, size?, qty, notes?)
    let onSubmit: (String?, String, String, String?, Int, String?) -> Void

    private static let OTHER = "__other__"
    private var isOther: Bool { retailerId == Self.OTHER }
    private var canSubmit: Bool {
        guard !item.isEmpty, retailerId != nil else { return false }
        if isOther { return !url.isEmpty }
        return true
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    EyebrowPill(label: "Concierge", systemImage: "wand.and.stars")
                    EditorialHeader(
                        title: "New request",
                        subtitle: "Paste a UK retailer link and we'll quote within 24 hours."
                    )

                    ProcessStepsCard(
                        title: "How Buy for me works",
                        steps: [
                            ("1", "You share the link", "Paste a UK retailer URL plus a few details."),
                            ("2", "We send a quote", "Within 24 hours you'll get an email with the price."),
                            ("3", "Accept or reject", "Accept to fund from wallet, reject with a reason if it's a no."),
                            ("4", "We buy and ship", "Your item lands at our UK warehouse and joins the next flight."),
                        ]
                    )

                    retailerCard
                    itemCard
                    notesCard
                    submitButton
                }
                .padding(20)
            }
            .scrollContentBackground(.hidden)
            .liquidBackdrop()
            .navigationTitle("New request")
            .glassNavigationBar(displayMode: .inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var retailerCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Retailer").font(.headline).foregroundStyle(Brand.ink)

                fieldBox {
                    Menu {
                        ForEach(groupedRetailers(), id: \.country) { group in
                            Section(group.country) {
                                ForEach(group.items, id: \.id) { r in
                                    Button(r.name) { retailerId = r.id; url = "" }
                                }
                            }
                        }
                        Divider()
                        Button("Other (paste a URL)") { retailerId = Self.OTHER }
                    } label: {
                        HStack {
                            Text(retailerLabel())
                                .foregroundStyle(retailerId == nil ? Brand.ink.opacity(0.4) : Brand.ink)
                            Spacer()
                            Image(systemName: "chevron.up.chevron.down")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text((isOther ? "Retailer URL".uppercased() : "Item URL (optional)".uppercased()))
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(Brand.ink.opacity(0.5))
                    fieldBox {
                        TextField(
                            "",
                            text: $url,
                            prompt: Text("https://…")
                                .foregroundStyle(Brand.ink.opacity(0.4))
                        )
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .textFieldStyle(.plain)
                        .foregroundStyle(Brand.ink)
                    }
                }

                Text(isOther
                     ? "Paste the full URL — we'll quote within 24h."
                     : "Pick a retailer above. The URL field is optional unless you chose Other.")
                    .font(.caption2).foregroundStyle(.secondary)
            }
        }
    }

    private struct RetailerGroup {
        let country: String
        let items: [RetailerDto]
    }

    private func groupedRetailers() -> [RetailerGroup] {
        // Customer Buy-for-me only sources from UK suppliers — USA + China
        // are warehouse-side classifications for the operator catalog and
        // are not yet supported as customer-facing concierge origins.
        let ukOnly = retailers.filter { $0.country.uppercased() == "UK" }
        let groups = Dictionary(grouping: ukOnly, by: { $0.country })
        return groups
            .sorted { ($0.value.first?.sortOrder ?? 999) < ($1.value.first?.sortOrder ?? 999) }
            .map { RetailerGroup(country: $0.key, items: $0.value) }
    }

    private func retailerLabel() -> String {
        if let id = retailerId {
            if id == Self.OTHER { return "Other (paste a URL)" }
            return retailers.first(where: { $0.id == id })?.name ?? "Choose a retailer"
        }
        return "Choose a retailer"
    }

    private var itemCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("Item details").font(.headline).foregroundStyle(Brand.ink)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Item name".uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(Brand.ink.opacity(0.5))
                    fieldBox {
                        TextField("e.g. Blue hoodie size M", text: $item)
                            .textFieldStyle(.plain)
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Size / variant (optional)".uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(Brand.ink.opacity(0.5))
                    fieldBox {
                        TextField("e.g. M, 42, Black", text: $size)
                            .textFieldStyle(.plain)
                    }
                }

                HStack {
                    Text("Quantity")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Brand.ink)
                    Spacer()
                    Stepper("\(qty)", value: $qty, in: 1...20)
                        .labelsHidden()
                    Text("\(qty)")
                        .font(.subheadline.monospaced().weight(.bold))
                        .foregroundStyle(Brand.orange)
                        .frame(minWidth: 28, alignment: .trailing)
                }
            }
        }
    }

    private var notesCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Notes for our team").font(.headline).foregroundStyle(Brand.ink)
                fieldBox {
                    TextEditor(text: $notes)
                        .frame(minHeight: 110)
                        .scrollContentBackground(.hidden)
                }
                Text("Anything we should know — colour preferences, alternatives, deadlines.")
                    .font(.caption2).foregroundStyle(.secondary)
            }
        }
    }

    private var submitButton: some View {
        Button {
            let resolvedRetailerId: String? = (retailerId == Self.OTHER) ? nil : retailerId
            onSubmit(
                resolvedRetailerId,
                url, item,
                size.isEmpty ? nil : size,
                qty,
                notes.isEmpty ? nil : notes
            )
        } label: {
            Label("Request a quote", systemImage: "paperplane.fill")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
        .disabled(!canSubmit)
    }

    @ViewBuilder
    private func fieldBox<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content()
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Brand.cream.opacity(0.6))
            )
    }
}

/// Reject a concierge quote with a short reason. Reason is required so the
/// operator has something actionable to re-quote against.
private struct RejectQuoteSheet: View {
    @Environment(\.dismiss) private var dismiss
    let order: BuyForMeOrderDto
    let onSubmit: (String) -> Void
    @State private var reason: String = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(order.itemName).font(.headline)
                    if let g = order.estimateGbp?.doubleValue {
                        Text("Quoted: £\(String(format: "%.2f", g)) + \(Int(order.markupPct))% service")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                Section("Why are you rejecting?") {
                    TextEditor(text: $reason).frame(minHeight: 120)
                }
                Section {
                    Text("We'll get back with another option if we can.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Reject quote")
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
