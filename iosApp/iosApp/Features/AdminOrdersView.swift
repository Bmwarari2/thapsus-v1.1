// AdminOrdersView.swift
// Admin order management: list, bulk update, edit, cancel, request payment.

import SwiftUI
import ThapsusShared

/// Strongly-typed payload for "Create order for client". Replaces the 10-positional-
/// arg closure that previously let `market` and `description` get transposed —
/// the server's CHECK constraint then rejected the row with a cryptic 23514.
struct CreateOrderPayload {
    var customerEmail: String?
    var customerName: String?
    var retailer: String
    var market: String   // always "UK" — the field still exists on the
                         // server DTO until the column is dropped in the
                         // backend strip PR.
    var description: String
    var weightKg: Double?
    var shippingSpeed: String  // "economy" or "express"
    var insurance: Bool
    var declaredValue: Double
    var electronicsItem: String?  // nil | "phone" | "laptop" | "tv_monitor"
    var hsTier: String?           // HS tier key, see HsCategory below
}

/// HS tier the admin/customer picks at create time. Server defaults to
/// 'general' (or 'electronics' when an electronics_item is set), so the
/// `nil` case is fine for backwards compatibility.
struct HsCategory: Identifiable, Hashable {
    let key: String
    let label: String
    let note: String
    var id: String { key }
}

let hsCategories: [HsCategory] = [
    .init(key: "general",            label: "General goods",        note: "Default 25% duty band"),
    .init(key: "electronics",        label: "Consumer electronics", note: "0% duty, 16% VAT"),
    .init(key: "clothing_textiles",  label: "Clothing & textiles",  note: "25% duty band"),
    .init(key: "food_processed",     label: "Processed food",       note: "35% sensitive list"),
    .init(key: "raw_materials",      label: "Raw materials",        note: "0% duty"),
    .init(key: "books_media",        label: "Books / media",        note: "Zero-rated"),
    .init(key: "zero_rated",         label: "Medical / exempt",     note: "Zero-rated, gazetted"),
]

struct AdminOrdersView: View {
    @State private var vm: AdminOrdersViewModel? = nil
    @State private var stateObs: StateFlowObserver<AdminOrdersViewModelUiState>? = nil
    @State private var actionObs: StateFlowObserver<AdminOrdersViewModelActionState>? = nil
    @State private var filtersObs: StateFlowObserver<AdminOrdersViewModel.Filters>? = nil
    @State private var selectedIds: Set<String> = []
    @State private var bulkStatus: String = "received_at_warehouse"
    @State private var editTarget: AdminOrderRow? = nil
    @State private var paymentTarget: AdminOrderRow? = nil
    @State private var reminderTarget: AdminOrderRow? = nil
    @State private var cancelTarget: AdminOrderRow? = nil
    @State private var showCreate: Bool = false
    @State private var showFilters: Bool = false
    @State private var statusFilter: String = ""
    @State private var startDate: Date = Calendar.current.date(byAdding: .day, value: -30, to: Date()) ?? Date()
    @State private var endDate: Date = Date()
    @State private var dateRangeEnabled: Bool = false

    private let bulkOptions = [
        "received_at_warehouse", "consolidating", "in_transit",
        "customs", "out_for_delivery", "delivered", "cancelled"
    ]

    private let statusOptions = [
        "", "pending", "received_at_warehouse", "consolidating",
        "in_transit", "customs", "out_for_delivery", "delivered", "cancelled"
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EyebrowPill(label: "Admin", systemImage: "shippingbox.fill")
                EditorialHeader(title: "Orders", subtitle: "Create, bulk update, edit metadata, request payment.")

                HStack(spacing: 10) {
                    Button(action: { showCreate = true }) {
                        HStack(spacing: 8) {
                            Image(systemName: "plus.circle.fill")
                            Text("Create order for client")
                        }
                    }
                    .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

                    Button {
                        withAnimation { showFilters.toggle() }
                    } label: {
                        Label(activeFilterCount > 0 ? "Filters · \(activeFilterCount)" : "Filters",
                              systemImage: "line.3.horizontal.decrease.circle")
                    }
                    .buttonStyle(.bordered)
                    .tint(activeFilterCount > 0 ? Brand.orange : .gray)
                }

                if showFilters { filterBar }

                if !selectedIds.isEmpty {
                    bulkBar
                }

                actionBanner
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Orders")
        .glassNavigationBar()
        .sheet(item: $editTarget) { order in
            EditOrderSheet(order: order) { weight, cost, duty, status, description, electronics, notes in
                vm?.editOrder(
                    id: order.id,
                    weightKg: weight.map { KotlinDouble(value: $0) },
                    actualCost: cost.map { KotlinDouble(value: $0) },
                    customsDuty: duty.map { KotlinDouble(value: $0) },
                    status: status,
                    description: description,
                    electronicsItem: electronics,
                    notes: notes
                )
                editTarget = nil
            }
        }
        .sheet(item: $paymentTarget) { order in
            RequestPaymentSheet(order: order, kind: .request) { amount, notes in
                vm?.requestPayment(id: order.id, amount: amount, notes: notes)
                paymentTarget = nil
            }
        }
        .sheet(item: $reminderTarget) { order in
            RequestPaymentSheet(order: order, kind: .reminder) { amount, notes in
                vm?.sendReminder(id: order.id, amount: amount, notes: notes)
                reminderTarget = nil
            }
        }
        .sheet(item: $cancelTarget) { order in
            CancelOrderSheet(order: order) { reason in
                vm?.cancel(id: order.id, reason: reason)
                cancelTarget = nil
            }
        }
        .sheet(isPresented: $showCreate, onDismiss: { vm?.clearCustomerSearch() }) {
            if let model = vm {
                CreateOrderForClientSheet(model: model) { payload in
                    assert(payload.market == "UK",
                           "market must be UK — got '\(payload.market)'")
                    model.createForClient(
                        customerEmail: payload.customerEmail,
                        customerName: payload.customerName,
                        retailer: payload.retailer,
                        market: payload.market,
                        description: payload.description,
                        weightKg: payload.weightKg.map { KotlinDouble(value: $0) },
                        shippingSpeed: payload.shippingSpeed,
                        insurance: payload.insurance,
                        declaredValue: payload.declaredValue,
                        electronicsItem: payload.electronicsItem,
                        hsTier: payload.hsTier
                    )
                    model.clearCustomerSearch()
                    showCreate = false
                }
            }
        }
        .task { bootstrap() }
    }

    private var activeFilterCount: Int {
        var n = 0
        if !statusFilter.isEmpty { n += 1 }
        if dateRangeEnabled { n += 1 }
        return n
    }

    private static let isoDate: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withFullDate]
        return f
    }()

    private var filterBar: some View {
        InkFeatureCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Filters").font(.subheadline.weight(.semibold))

                Picker("Status", selection: $statusFilter) {
                    Text("Any status").tag("")
                    ForEach(statusOptions.filter { !$0.isEmpty }, id: \.self) { s in
                        Text(s.replacingOccurrences(of: "_", with: " ").capitalized).tag(s)
                    }
                }
                .pickerStyle(.menu).tint(Brand.orange)
                .onChange(of: statusFilter) { _, v in
                    vm?.setStatus(status: v.isEmpty ? nil : v)
                }

                Toggle("Filter by date", isOn: $dateRangeEnabled)
                    .onChange(of: dateRangeEnabled) { _, enabled in
                        if enabled {
                            applyDateFilter()
                        } else {
                            vm?.setDateRange(startDate: nil, endDate: nil)
                        }
                    }
                if dateRangeEnabled {
                    DatePicker("From", selection: $startDate, displayedComponents: .date)
                        .onChange(of: startDate) { _, _ in applyDateFilter() }
                    DatePicker("To", selection: $endDate, displayedComponents: .date)
                        .onChange(of: endDate) { _, _ in applyDateFilter() }
                }

                if activeFilterCount > 0 {
                    Button("Clear all") {
                        statusFilter = ""; dateRangeEnabled = false
                        vm?.clearFilters()
                    }
                    .buttonStyle(.bordered).tint(.red)
                }
            }
        }
    }

    private func applyDateFilter() {
        let s = AdminOrdersView.isoDate.string(from: startDate)
        let e = AdminOrdersView.isoDate.string(from: endDate)
        vm?.setDateRange(startDate: s, endDate: e)
    }

    private var bulkBar: some View {
        InkFeatureCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("\(selectedIds.count) selected").font(.subheadline.weight(.semibold))
                Picker("Status", selection: $bulkStatus) {
                    ForEach(bulkOptions, id: \.self) { Text($0.replacingOccurrences(of: "_", with: " ").capitalized).tag($0) }
                }
                .tint(Brand.orange)
                HStack(spacing: 10) {
                    Button("Apply") {
                        vm?.bulkUpdate(ids: Array(selectedIds), status: bulkStatus)
                        selectedIds.removeAll()
                    }
                    .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                    Button("Clear") { selectedIds.removeAll() }
                        .buttonStyle(.bordered).tint(.gray)
                }
            }
        }
    }

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case let done as AdminOrdersViewModelActionStateDone:
            CalloutBanner(icon: "checkmark.circle.fill", title: "Done", message: done.message)
        case let err as AdminOrdersViewModelActionStateError:
            ErrorBanner(title: "Couldn't complete", message: err.message)
        case is AdminOrdersViewModelActionStateInFlight:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch stateObs?.value {
        case let loaded as AdminOrdersViewModelUiStateLoaded:
            if loaded.orders.isEmpty {
                CrystalCard {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("No orders match these filters.").font(.subheadline)
                        if activeFilterCount > 0 {
                            Text("Tap Clear all to reset.").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            } else {
                Text("Showing \(loaded.orders.count) of \(loaded.total)")
                    .font(.caption).foregroundStyle(.secondary)
                ForEach(loaded.orders, id: \.id) { order in
                    orderRow(order)
                }
                if loaded.hasMore {
                    Button {
                        vm?.loadMore()
                    } label: {
                        if loaded.loadingMore {
                            ProgressView()
                        } else {
                            Label("Load more", systemImage: "arrow.down.circle")
                        }
                    }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                    .disabled(loaded.loadingMore)
                }
            }
        case is AdminOrdersViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
        case let err as AdminOrdersViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func orderRow(_ order: AdminOrderRow) -> some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Toggle("Select", isOn: Binding(
                        get: { selectedIds.contains(order.id) },
                        set: { if $0 { selectedIds.insert(order.id) } else { selectedIds.remove(order.id) } }
                    ))
                    .labelsHidden()
                    NavigationLink(destination: AdminOrderDetailView(orderId: order.id)) {
                        HStack {
                            Text(order.trackingNumber ?? String(order.id.prefix(8)))
                                .font(.headline.monospaced())
                                .foregroundStyle(Brand.ink)
                            Image(systemName: "chevron.right")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                    }
                    .buttonStyle(.plain)
                    Spacer()
                    statusBadge(order.status)
                }
                if let name = order.customerName {
                    Text(name).font(.caption).foregroundStyle(.secondary)
                }
                if let retailer = order.retailer {
                    Text(retailer).font(.caption).foregroundStyle(.tertiary)
                }
                HStack(spacing: 10) {
                    Button("Edit") { editTarget = order }
                        .buttonStyle(.bordered).tint(.blue)
                    Menu("Payment") {
                        Button("Request payment") { paymentTarget = order }
                        Button("Send reminder") { reminderTarget = order }
                    }
                    .buttonStyle(.bordered).tint(.green)
                    Button(role: .destructive) {
                        cancelTarget = order
                    } label: { Text("Cancel") }
                        .buttonStyle(.bordered).tint(.red)
                }
            }
        }
    }

    private func statusBadge(_ status: String) -> some View {
        let color: Color
        switch status {
        case "delivered": color = .green
        case "cancelled": color = .red
        case "out_for_delivery": color = .cyan
        case "in_transit", "consolidating", "manifested": color = .blue
        case "customs", "awaiting_duty_payment": color = .orange
        default: color = .gray
        }
        return Text(status.replacingOccurrences(of: "_", with: " ").uppercased())
            .font(.caption2.weight(.heavy)).tracking(2)
            .foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Capsule().fill(color.opacity(0.16)))
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.adminOrdersViewModel()
        vm = model
        model.load()
        stateObs = StateFlowObserver(initial: model.state.value) { model.state }
        actionObs = StateFlowObserver(initial: model.action.value) { model.action }
        filtersObs = StateFlowObserver(initial: model.filters.value) { model.filters }
    }
}

extension AdminOrderRow: @retroactive Identifiable {}

/// Webapp ELECTRONICS_HANDLING keys (utils/pricing.js). Server rejects anything else.
private let electronicsOptions: [(label: String, value: String?)] = [
    ("None", nil),
    ("Phone", "phone"),
    ("Laptop / Accessories", "laptop"),
    ("TV / Screen / Monitor", "tv_monitor"),
]

private struct CreateOrderForClientSheet: View {
    @Environment(\.dismiss) private var dismiss
    let model: AdminOrdersViewModel
    let onSubmit: (CreateOrderPayload) -> Void

    @State private var email: String = ""
    @State private var name: String = ""
    @State private var retailer: String = ""
    @State private var market: String = "UK"
    @State private var description: String = ""
    @State private var weight: String = ""
    @State private var speed: String = "economy"
    @State private var electronics: String = ""  // "" = none, else "phone"|"laptop"|"tv_monitor"
    @State private var hsTier: String = "general"
    @State private var pickedCustomer: AdminUserDto? = nil
    @State private var submitted: Bool = false

    /// Sheet owns its own observer so a StateFlow emission re-renders the Menu
    /// every time without depending on the parent body re-evaluating.
    @State private var hitsObs: StateFlowObserver<[AdminUserDto]>? = nil

    private var hits: [AdminUserDto] { hitsObs?.value ?? [] }

    var body: some View {
        NavigationStack {
            Form {
                Section("Customer") {
                    HStack {
                        TextField("Email (preferred)", text: $email)
                            .keyboardType(.emailAddress).textInputAutocapitalization(.never)
                            .onChange(of: email) { _, newValue in
                                if pickedCustomer?.email != newValue { pickedCustomer = nil }
                                model.searchCustomers(query: newValue.isEmpty ? name : newValue)
                            }
                        if !hits.isEmpty && pickedCustomer == nil {
                            customerMenu
                        }
                    }
                    TextField("…or name", text: $name)
                        .onChange(of: name) { _, newValue in
                            if pickedCustomer?.name != newValue { pickedCustomer = nil }
                            model.searchCustomers(query: email.isEmpty ? newValue : email)
                        }
                    if let picked = pickedCustomer {
                        Label("Selected \(picked.name)", systemImage: "checkmark.circle.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.green)
                    } else if !hits.isEmpty {
                        Text("\(hits.count) match\(hits.count == 1 ? "" : "es") — tap the menu above to pick.")
                            .font(.caption).foregroundStyle(.secondary)
                    } else if (email.count >= 2 || name.count >= 2) && pickedCustomer == nil {
                        Text("No matching customer.")
                            .font(.caption).foregroundStyle(.tertiary)
                    }
                }
                Section("Order") {
                    TextField("Retailer (e.g. Amazon)", text: $retailer)
                    TextField("Description", text: $description, axis: .vertical)
                        .lineLimit(2...5)
                }
                Section("Optional") {
                    HStack { Text("Weight (kg)"); Spacer(); TextField("0", text: $weight).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                    Picker("Speed", selection: $speed) {
                        Text("Economy").tag("economy")
                        Text("Express").tag("express")
                    }
                    Picker("Electronics", selection: $electronics) {
                        ForEach(electronicsOptions, id: \.value) { opt in
                            Text(opt.label).tag(opt.value ?? "")
                        }
                    }
                }
                Section {
                    Picker("HS category", selection: $hsTier) {
                        ForEach(hsCategories) { cat in
                            Text(cat.label).tag(cat.key)
                        }
                    }
                    if let active = hsCategories.first(where: { $0.key == hsTier }) {
                        Text(active.note)
                            .font(.caption).foregroundStyle(.secondary)
                    }
                } header: {
                    Text("Customs")
                } footer: {
                    Text("Customs estimate uses the duty rate for this category. Final amount is set by KRA at clearing.")
                }
            }
            .navigationTitle("New order for client")
            .onChange(of: electronics) { _, newValue in
                // Default to electronics tier when an electronics item is selected
                // (and the user hasn't already moved off the default category).
                if !newValue.isEmpty && hsTier == "general" {
                    hsTier = "electronics"
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(submitted ? "Sending…" : "Create") {
                        guard !submitted else { return }
                        submitted = true
                        onSubmit(CreateOrderPayload(
                            customerEmail: email.isEmpty ? nil : email,
                            customerName: name.isEmpty ? nil : name,
                            retailer: retailer.trimmingCharacters(in: .whitespacesAndNewlines),
                            market: market,
                            description: description.trimmingCharacters(in: .whitespacesAndNewlines),
                            weightKg: Double(weight),
                            shippingSpeed: speed,
                            // Insurance offering removed 2026-04-30 — admin
                            // create-for-client always submits with no cover.
                            // Shared `createForClient` still accepts the args.
                            insurance: false,
                            declaredValue: 0,
                            electronicsItem: electronics.isEmpty ? nil : electronics,
                            hsTier: hsTier
                        ))
                    }
                    .disabled(submitted || retailer.isEmpty || description.isEmpty || (email.isEmpty && name.isEmpty))
                }
            }
            .task {
                if hitsObs == nil {
                    hitsObs = StateFlowObserver(initial: []) { model.customerHits }
                }
            }
        }
    }

    /// Renders the live hits as a Menu. iOS 18 reliably refreshes Menu content
    /// on observable state change; nested Form rows do not.
    private var customerMenu: some View {
        Menu {
            ForEach(hits, id: \.id) { hit in
                Button {
                    email = hit.email
                    name = hit.name
                    pickedCustomer = hit
                    model.clearCustomerSearch()
                } label: {
                    VStack(alignment: .leading) {
                        Text(hit.name)
                        Text(hit.email).font(.caption)
                        if let warehouse = hit.warehouseId, !warehouse.isEmpty {
                            Text(warehouse).font(.caption2.monospaced())
                        }
                    }
                }
            }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "person.2.fill")
                Text("\(hits.count)")
                    .font(.caption.weight(.bold))
            }
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Capsule().fill(Brand.orange.opacity(0.18)))
            .foregroundStyle(Brand.orange)
        }
    }
}

private struct EditOrderSheet: View {
    let order: AdminOrderRow

    @Environment(\.dismiss) private var dismiss
    @State private var weight: String = ""
    @State private var cost: String = ""
    @State private var duty: String = ""
    @State private var status: String
    @State private var description: String = ""
    @State private var electronics: String = ""  // "" = none, else "phone"|"laptop"|"tv_monitor"
    @State private var notes: String = ""

    /// (weight?, cost?, duty?, status?, description?, electronicsItem?, notes?)
    let onSubmit: (Double?, Double?, Double?, String?, String?, String?, String?) -> Void

    init(order: AdminOrderRow, onSubmit: @escaping (Double?, Double?, Double?, String?, String?, String?, String?) -> Void) {
        self.order = order
        self.onSubmit = onSubmit
        _weight = State(initialValue: order.weightKg.map { "\($0)" } ?? "")
        _cost = State(initialValue: order.actualCost.map { "\($0)" } ?? "")
        _duty = State(initialValue: order.customsDuty.map { "\($0)" } ?? "")
        _status = State(initialValue: order.status)
        _description = State(initialValue: order.description_ ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Status") {
                    Picker("Status", selection: $status) {
                        Text("Pending").tag("pending")
                        Text("Received").tag("received_at_warehouse")
                        Text("Consolidating").tag("consolidating")
                        Text("In transit").tag("in_transit")
                        Text("Customs").tag("customs")
                        Text("Out for delivery").tag("out_for_delivery")
                        Text("Delivered").tag("delivered")
                        Text("Cancelled").tag("cancelled")
                    }
                }
                // GBP because the orders pricing pipeline normalises every
                // entry into pounds before storing — KRA's KES-denominated
                // duty assessment is converted server-side using the live
                // FX rate. See `pricing_schema.md` for the full bridge.
                Section {
                    HStack { Text("Weight (kg)"); Spacer(); TextField("0", text: $weight).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                    HStack { Text("Actual cost (£)"); Spacer(); TextField("0", text: $cost).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                    HStack { Text("Customs duty (£)"); Spacer(); TextField("0", text: $duty).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                } header: {
                    Text("Measurements")
                } footer: {
                    Text("Amounts in GBP. Customs duty is the converted value of the KRA assessment at clearing.")
                }
                Section("Description") {
                    TextEditor(text: $description).frame(minHeight: 80)
                }
                Section("Electronics") {
                    Picker("Electronics", selection: $electronics) {
                        ForEach(electronicsOptions, id: \.value) { opt in
                            Text(opt.label).tag(opt.value ?? "")
                        }
                    }
                }
                Section("Internal notes") {
                    TextEditor(text: $notes).frame(minHeight: 80)
                }
            }
            .navigationTitle("Edit order")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSubmit(
                            Double(weight),
                            Double(cost),
                            Double(duty),
                            status,
                            description.isEmpty ? nil : description,
                            electronics.isEmpty ? nil : electronics,
                            notes.isEmpty ? nil : notes
                        )
                    }
                }
            }
        }
    }
}

private struct RequestPaymentSheet: View {
    enum Kind {
        case request
        case reminder
        var title: String { self == .request ? "Request payment" : "Send reminder" }
        var confirm: String { self == .request ? "Send" : "Remind" }
    }

    let order: AdminOrderRow
    let kind: Kind

    @Environment(\.dismiss) private var dismiss
    @State private var amount: String = ""
    @State private var notes: String = ""

    let onSubmit: (Double, String?) -> Void

    init(order: AdminOrderRow, kind: Kind, onSubmit: @escaping (Double, String?) -> Void) {
        self.order = order
        self.kind = kind
        self.onSubmit = onSubmit
        _amount = State(initialValue: order.estimatedCost.map { "\(Int(truncating: $0))" } ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Order") {
                    LabeledContent("Tracking", value: order.trackingNumber ?? order.id)
                }
                Section("Amount (KES)") {
                    TextField("0", text: $amount).keyboardType(.decimalPad)
                }
                Section("Notes") {
                    TextEditor(text: $notes).frame(minHeight: 80)
                }
            }
            .navigationTitle(kind.title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(kind.confirm) {
                        onSubmit(Double(amount) ?? 0, notes.isEmpty ? nil : notes)
                    }
                    .disabled((Double(amount) ?? 0) <= 0)
                }
            }
        }
    }
}

private struct CancelOrderSheet: View {
    let order: AdminOrderRow
    let onSubmit: (String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var reason: String = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Order") {
                    LabeledContent("Tracking", value: order.trackingNumber ?? order.id)
                    if let r = order.retailer { LabeledContent("Retailer", value: r) }
                }
                Section("Reason (optional)") {
                    TextEditor(text: $reason).frame(minHeight: 100)
                    Text("Logged on the customer's order timeline + admin_logs.")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Cancel order")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Back") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Cancel order", role: .destructive) {
                        onSubmit(reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : reason)
                    }
                }
            }
        }
    }
}
