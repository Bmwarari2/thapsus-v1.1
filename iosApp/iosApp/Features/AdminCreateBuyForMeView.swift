// AdminCreateBuyForMeView.swift
// Admin form for creating a Buy-for-me request on behalf of a customer
// who placed the order off-platform (WhatsApp, phone, in person).
// Optionally pre-quotes in the same submission so the customer can pay
// straight away without an operator round-trip.

import SwiftUI
import ThapsusShared

struct AdminCreateBuyForMeView: View {
    @State private var vm: AdminCreateBuyForMeViewModel? = nil
    @State private var usersObs:     StateFlowObserver<[AdminUserDto]>? = nil
    @State private var retailersObs: StateFlowObserver<[RetailerDto]>?  = nil
    @State private var actionObs:    StateFlowObserver<AdminCreateBuyForMeViewModelActionState>? = nil

    @State private var search: String = ""
    @State private var selectedUserId: String? = nil
    @State private var retailerId: String? = nil
    @State private var retailerUrl: String = ""
    @State private var itemName: String = ""
    @State private var size: String = ""
    @State private var qty: Int = 1
    @State private var notes: String = ""
    @State private var includeQuote: Bool = false
    @State private var estimateGbp: String = ""
    @State private var markupPct: String = "10"

    private static let OTHER = "__other__"
    private var isOther: Bool { retailerId == Self.OTHER }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                EyebrowPill(label: "Admin", systemImage: "wand.and.stars")
                EditorialHeader(
                    title: "Create Buy-for-me",
                    subtitle: "On behalf of a customer who messaged via WhatsApp, phone, or in person."
                )
                actionBanner
                customerCard
                itemCard
                quoteCard
                submitButton
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Create BFM")
        .glassNavigationBar()
        .task { bootstrap() }
    }

    // MARK: - Banner

    @ViewBuilder
    private var actionBanner: some View {
        switch actionObs?.value {
        case let done as AdminCreateBuyForMeViewModelActionStateDone:
            createdReceipt(done)
        case let err as AdminCreateBuyForMeViewModelActionStateError:
            ErrorBanner(title: "Couldn't create", message: err.message)
        case is AdminCreateBuyForMeViewModelActionStateSubmitting:
            ProgressView().frame(maxWidth: .infinity)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private func createdReceipt(_ done: AdminCreateBuyForMeViewModelActionStateDone) -> some View {
        SoftCard(tint: Color.green.opacity(0.10)) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.title2).foregroundStyle(.green)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Order created").font(.headline).foregroundStyle(Brand.ink)
                        Text(done.preQuoted
                             ? "Quote email sent — customer can pay now"
                             : "Awaiting operator quote in the queue")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                }
                Divider().opacity(0.4)
                receiptRow(label: "Order", value: done.orderId)
                receiptRow(label: "Item", value: done.itemName)
                if let email = done.customerEmail {
                    receiptRow(label: "Customer", value: email)
                }
                HStack {
                    Spacer()
                    Button("Create another") { vm?.resetAction() }
                        .buttonStyle(.bordered).controlSize(.small)
                }
            }
        }
    }

    private func receiptRow(label: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label.uppercased())
                .font(.caption2.weight(.heavy)).tracking(2)
                .foregroundStyle(Brand.ink.opacity(0.5))
                .frame(width: 80, alignment: .leading)
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink.opacity(0.85))
                .lineLimit(1)
            Spacer()
        }
    }

    // MARK: - Customer picker

    @ViewBuilder
    private var customerCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Customer").font(.headline).foregroundStyle(Brand.ink)
                TextField("Search by email, name, or warehouse ID", text: $search)
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled(true)
                    .textInputAutocapitalization(.never)
                let users = filteredUsers
                if users.isEmpty {
                    Text("No matching customers.").font(.caption).foregroundStyle(.secondary)
                } else {
                    VStack(spacing: 0) {
                        ForEach(users.prefix(8), id: \.id) { u in
                            Button { selectedUserId = u.id } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(u.name.isEmpty ? u.email : u.name)
                                            .font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                                        Text(u.email).font(.caption2).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    if selectedUserId == u.id {
                                        Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                                    }
                                }
                                .padding(8)
                                .background(selectedUserId == u.id ? Color.orange.opacity(0.08) : Color.clear,
                                            in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            Divider()
                        }
                    }
                }
            }
        }
    }

    private var filteredUsers: [AdminUserDto] {
        let all = usersObs?.value ?? []
        let q = search.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return Array(all.prefix(25)) }
        return all.filter { u in
            u.email.lowercased().contains(q) ||
            u.name.lowercased().contains(q) ||
            u.id.lowercased().contains(q)
        }
    }

    // MARK: - Item

    @ViewBuilder
    private var itemCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("Item").font(.headline).foregroundStyle(Brand.ink)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Retailer".uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(Brand.ink.opacity(0.5))
                    Menu {
                        ForEach(groupedRetailers(), id: \.country) { group in
                            Section(group.country) {
                                ForEach(group.items, id: \.id) { r in
                                    Button(r.name) { retailerId = r.id; retailerUrl = "" }
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
                        .padding(10)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text((isOther ? "Retailer URL" : "Item URL (optional)").uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2).foregroundStyle(Brand.ink.opacity(0.5))
                    TextField("https://…", text: $retailerUrl)
                        .textFieldStyle(.roundedBorder)
                        .keyboardType(.URL)
                        .autocorrectionDisabled(true)
                        .textInputAutocapitalization(.never)
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Item name".uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2).foregroundStyle(Brand.ink.opacity(0.5))
                    TextField("e.g. Blue hoodie size M", text: $itemName)
                        .textFieldStyle(.roundedBorder)
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Size / variant (optional)".uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2).foregroundStyle(Brand.ink.opacity(0.5))
                    TextField("e.g. M, 42, Black", text: $size)
                        .textFieldStyle(.roundedBorder)
                }

                HStack {
                    Text("Quantity").font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                    Spacer()
                    Stepper("\(qty)", value: $qty, in: 1...20).labelsHidden()
                    Text("\(qty)").font(.subheadline.monospaced().weight(.bold))
                        .foregroundStyle(Brand.orange).frame(minWidth: 28, alignment: .trailing)
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Notes (visible to customer)".uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2).foregroundStyle(Brand.ink.opacity(0.5))
                    TextEditor(text: $notes)
                        .frame(minHeight: 70)
                        .padding(6)
                        .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous)
                                    .stroke(Color.secondary.opacity(0.25), lineWidth: 1))
                }
            }
        }
    }

    // MARK: - Pre-quote

    @ViewBuilder
    private var quoteCard: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 12) {
                Toggle(isOn: $includeQuote) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Pre-quote in this submission").font(.subheadline.weight(.heavy)).foregroundStyle(Brand.ink)
                        Text("Customer gets the quote email immediately and can pay straight away.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                .tint(Brand.orange)

                if includeQuote {
                    HStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Estimate (GBP)".uppercased())
                                .font(.caption2.weight(.heavy)).tracking(2).foregroundStyle(Brand.ink.opacity(0.5))
                            TextField("e.g. 25.00", text: $estimateGbp)
                                .textFieldStyle(.roundedBorder)
                                .keyboardType(.decimalPad)
                        }
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Markup %".uppercased())
                                .font(.caption2.weight(.heavy)).tracking(2).foregroundStyle(Brand.ink.opacity(0.5))
                            TextField("10", text: $markupPct)
                                .textFieldStyle(.roundedBorder)
                                .keyboardType(.numberPad)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Submit

    private var submitButton: some View {
        Button {
            guard let userId = selectedUserId else { return }
            let resolvedRetailerId: String? = isOther ? nil : retailerId
            vm?.create(
                userId: userId,
                itemName: itemName.trimmingCharacters(in: .whitespacesAndNewlines),
                retailerId: resolvedRetailerId,
                retailerUrl: retailerUrl.isEmpty ? nil : retailerUrl,
                size: size.isEmpty ? nil : size,
                qty: Int32(qty),
                notes: notes.isEmpty ? nil : notes,
                estimateGbp: includeQuote ? KotlinDouble(value: Double(estimateGbp) ?? 0) : nil,
                markupPct:   includeQuote ? KotlinDouble(value: Double(markupPct) ?? 10) : nil
            )
            // Clear item-level fields; keep customer selected for follow-ups.
            itemName = ""; size = ""; qty = 1; notes = ""
            estimateGbp = ""; markupPct = "10"; includeQuote = false
            retailerId = nil; retailerUrl = ""
        } label: {
            Label(includeQuote ? "Create + send quote" : "Create request",
                  systemImage: "paperplane.fill")
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
        }
        .buttonStyle(GlassSheenButtonStyle(fill: Brand.ink, foreground: .white))
        .disabled(!canSubmit)
    }

    private var canSubmit: Bool {
        if actionObs?.value is AdminCreateBuyForMeViewModelActionStateDone { return false }
        if actionObs?.value is AdminCreateBuyForMeViewModelActionStateSubmitting { return false }
        guard let _ = selectedUserId else { return false }
        guard retailerId != nil else { return false }
        if isOther, retailerUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return false }
        if itemName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return false }
        if includeQuote, !((Double(estimateGbp) ?? 0) > 0) { return false }
        return true
    }

    // MARK: - Wiring

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.adminCreateBuyForMeViewModel()
        vm = model
        usersObs     = StateFlowObserver(initial: model.users.value)           { model.users }
        retailersObs = StateFlowObserver(initial: model.retailerCatalog.value) { model.retailerCatalog }
        actionObs    = StateFlowObserver(initial: model.action.value)          { model.action }
        model.bootstrap()
    }

    private struct RetailerGroup { let country: String; let items: [RetailerDto] }

    private func groupedRetailers() -> [RetailerGroup] {
        // Customer-facing BFM is UK-only; admin "create on behalf" matches.
        let ukOnly = (retailersObs?.value ?? []).filter { $0.country.uppercased() == "UK" }
        let groups = Dictionary(grouping: ukOnly, by: { $0.country })
        return groups
            .sorted { ($0.value.first?.sortOrder ?? 999) < ($1.value.first?.sortOrder ?? 999) }
            .map { RetailerGroup(country: $0.key, items: $0.value) }
    }

    private func retailerLabel() -> String {
        if let id = retailerId {
            if id == Self.OTHER { return "Other (paste a URL)" }
            return retailersObs?.value.first(where: { $0.id == id })?.name ?? "Choose a retailer"
        }
        return "Choose a retailer"
    }
}
