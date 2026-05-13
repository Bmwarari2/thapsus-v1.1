// BuyForMeView.swift
// Concierge-purchase flow: paste a retailer link, our team quotes, you pay.

import SwiftUI
import SafariServices
import ThapsusShared

struct BuyForMeView: View {
    @State private var vm: BuyForMeViewModel? = nil
    @State private var stateObserver: StateFlowObserver<BuyForMeViewModelUiState>? = nil
    @State private var actionObserver: StateFlowObserver<BuyForMeViewModelActionState>? = nil
    @State private var retailersObs: StateFlowObserver<[RetailerDto]>? = nil
    @State private var showCreate: Bool = false
    /// `nil` = sheet closed, otherwise the order whose quote we're rejecting.
    @State private var rejectingFor: BuyForMeOrderDto? = nil
    /// In-app Safari browser target. Tapping a retailer in the rotating
    /// strip sets this to that retailer's `baseUrl`; the sheet hosts
    /// SFSafariViewController so the customer stays inside the app and
    /// can come back to the Shop tab with their idea / link ready.
    /// Wrapped in IdentifiableURL so `.sheet(item:)` can identify it
    /// without extending URL itself (avoids retroactive conformance).
    @State private var inAppUrl: IdentifiableURL? = nil
    /// Pay-flow target. Set when the customer taps "Accept & buy" on a
    /// quoted BFM order — opens PayInvoiceView with target_kind=
    /// buy_for_me. The legacy `vm.accept` route hit the removed
    /// /buy-for-me/{id}/accept wallet endpoint and bubbled a 410 with a
    /// developer hint into the customer-visible error banner.
    @State private var payTarget: PayTarget? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Concierge", systemImage: "wand.and.stars")
                EditorialHeader(title: "Buy for me",
                                subtitle: "Paste a UK retailer link, we buy and ship.")

                retailerMarquee

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
        // In-app browser for retailer-strip taps. SFSafariViewController
        // handles its own dismiss affordance, so a simple .sheet(item:)
        // binding is enough — no wrapper UINavigationController needed.
        .sheet(item: $inAppUrl) { wrapped in
            BFMSafariView(url: wrapped.url).ignoresSafeArea()
        }
        .sheet(item: $payTarget) { target in
            PayInvoiceView(
                targetKind: target.kind,
                targetId: target.id,
                targetTitle: target.title,
                amountKesGross: target.amountKes
            )
        }
        .task { bootstrap() }
    }

    /// Rotating retailer strip pinned to the top of the Shop page.
    /// Mirrors the web Home.jsx MarqueeRetailers component but adapted
    /// for touch: a horizontally-scrollable list of curated retailers
    /// (sorted by sortOrder, capped at 12 so the strip stays scannable).
    /// Tap a card → in-app Safari for that retailer's homepage. Customer
    /// browses, finds a product, then comes back and pastes the link via
    /// the "New request" button below.
    @ViewBuilder
    private var retailerMarquee: some View {
        if let all = retailersObs?.value, !all.isEmpty {
            let curated = all
                .sorted { $0.sortOrder < $1.sortOrder }
                .prefix(12)
            VStack(alignment: .leading, spacing: 8) {
                LGEyebrow(text: "Popular UK retailers")
                    .padding(.horizontal, 4)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(Array(curated), id: \.id) { retailer in
                            retailerChip(retailer)
                        }
                    }
                    .padding(.horizontal, 4)
                }
                .scrollClipDisabled()
            }
        }
    }

    @ViewBuilder
    private func retailerChip(_ retailer: RetailerDto) -> some View {
        Button {
            if let url = URL(string: retailer.baseUrl) {
                inAppUrl = IdentifiableURL(url: url)
            }
        } label: {
            HStack(spacing: 8) {
                ZStack {
                    Circle().fill(LG.glassBgStrong)
                    Image(systemName: retailerIcon(for: retailer.name))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(LG.accent2)
                }
                .frame(width: 30, height: 30)

                Text(retailer.name.uppercased())
                    .font(.body(12, weight: .heavy))
                    .tracking(1)
                    .foregroundStyle(LG.fg)
                    .lineLimit(1)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                Capsule().fill(.ultraThinMaterial)
            )
            .overlay(
                Capsule().strokeBorder(LG.glassBorder, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    /// SF Symbol pick for a retailer chip. Keeps the design system
    /// monochrome — coloured retailer logos are out of scope for v1.1
    /// since logo URLs aren't bundled with the asset catalogue.
    private func retailerIcon(for name: String) -> String {
        let lower = name.lowercased()
        if lower.contains("amazon") || lower.contains("ebay") || lower.contains("argos") { return "shippingbox.fill" }
        if lower.contains("zara") || lower.contains("asos") || lower.contains("shein") || lower.contains("next") || lower.contains("marks") { return "bag.fill" }
        if lower.contains("temu") || lower.contains("aliexpress") { return "bolt.fill" }
        if lower.contains("currys") || lower.contains("apple") || lower.contains("john lewis") { return "laptopcomputer" }
        return "storefront"
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
                            payTarget = PayTarget.fromBfm(order)
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

    @ScaledMetric(relativeTo: .caption2) private var statusBadgeSize: CGFloat = 9

    private func statusBadge(_ status: String) -> some View {
        let map: [String: Color] = [
            "pending_quote": .orange, "quoted": .blue, "paid": .green,
            "purchased": .purple, "received": .teal, "shipped": .green,
            "cancelled": .red
        ]
        let color = map[status] ?? .secondary
        return Text(status.replacingOccurrences(of: "_", with: " ").uppercased())
            .font(.system(size: statusBadgeSize, weight: .heavy)).tracking(2)
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
        // UK-only system; any non-UK rows (legacy data) are filtered out
        // so the customer never sees them.
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

// MARK: - In-app Safari wrapper for retailer-strip taps
//
// Inlined here rather than a new Hardware/SafariView.swift file so the
// PR doesn't need a pbxproj edit (the project uses explicit file
// references, not synchronised folders). If a second consumer ever
// needs SFSafariViewController, lift this into Hardware/ at that point.

private struct BFMSafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let config = SFSafariViewController.Configuration()
        config.entersReaderIfAvailable = false
        return SFSafariViewController(url: url, configuration: config)
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

// Small wrapper so `.sheet(item:)` can identify the URL without us
// extending Foundation.URL with a retroactive Identifiable conformance.
private struct IdentifiableURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}
