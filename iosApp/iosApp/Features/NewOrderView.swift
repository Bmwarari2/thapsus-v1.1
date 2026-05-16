// NewOrderView.swift
// Multi-step parcel creation. Mirrors the webapp NewOrder.jsx flow but
// adapted to a vertical iOS form. Re-uses the existing ParcelPreReg model
// for the actual submit so we don't fork the data layer.

import SwiftUI
import ThapsusShared

struct NewOrderView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.dismiss) private var dismiss

    @State private var vm: ParcelPreRegViewModel? = nil
    @State private var observer: StateFlowObserver<ParcelPreRegViewModelState>? = nil

    // Warehouse address VM is observed here too so the customer can see
    // (and copy) the shipping address before picking a retailer on
    // step 0 — the warehouse-address standalone screen was rolled into
    // this flow so customers see the address right when they need it.
    @State private var warehouseVm: WarehouseViewModel? = nil
    @State private var warehouseObserver: StateFlowObserver<WarehouseViewModelUiState>? = nil
    @State private var addressCopied: Bool = false

    @State private var step: Int = 0
    @State private var market: String = "UK"
    @State private var retailer: String = ""
    @State private var customRetailer: String = ""
    @State private var description: String = ""
    @State private var declaredValueGbp: String = ""
    @State private var hsTier: String = "general"
    @State private var weightKg: String = ""
    @State private var lengthCm: String = ""
    @State private var widthCm: String = ""
    @State private var heightCm: String = ""

    private let retailers = ["Amazon", "Shein", "Next", "Asos", "Superdrug", "eBay", "ZARA", "H&M", "Other"]

    private static let defaultWarehouseLines: [String] = [
        "31 Collingwood Close",
        "Hazel Grove, Stockport",
        "SK7 4LB",
        "United Kingdom"
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Send a parcel", systemImage: "shippingbox.fill")
                EditorialHeader(title: "New order",
                                subtitle: "We'll generate a label and book a slot on the next weekly flight.")

                // Warehouse address + process explainer. Only shown on
                // step 0 so the form gets full focus from step 1
                // onwards. The address card was previously its own
                // standalone screen — folded in here because the
                // customer needs the address at exactly this moment
                // (right before they check out at a UK retailer).
                if step == 0 {
                    warehouseAddressCard

                    ProcessStepsCard(
                        title: "How New order works",
                        steps: [
                            ("1", "Tell us about it", "Pick the retailer and what you're sending."),
                            ("2", "Ship to the address above", "Use your warehouse address at checkout — we'll receive and weigh the parcel."),
                            ("3", "We quote on receipt", "You'll get an emailed shipping invoice once it's measured."),
                            ("4", "Pay and we fly", "Pay from wallet or card; your parcel rides the next weekly flight."),
                        ]
                    )
                }

                progressBar

                stepContent

                statusBanner

                navButtons
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("New order")
        .glassNavigationBar()
        .task { bootstrap() }
        .onDisappear { vm?.reset() }
    }

    @ViewBuilder
    private var statusBanner: some View {
        switch observer?.value {
        case is ParcelPreRegViewModelStateSubmitting:
            CalloutBanner(
                tint: Brand.orange.opacity(0.16),
                icon: "paperplane.fill",
                title: "Submitting…",
                message: "Sending your order to Thapsus Cargo."
            )
        case let saved as ParcelPreRegViewModelStateSaved:
            CalloutBanner(
                tint: Color.green.opacity(0.14),
                icon: "checkmark.circle.fill",
                title: "Order created",
                message: "Tracking number \(saved.order.trackingNumber ?? String(saved.order.id.prefix(8))). A confirmation email is on its way."
            )
        case let failed as ParcelPreRegViewModelStateFailed:
            ErrorBanner(title: "Couldn't create the order", message: failed.message)
        default: EmptyView()
        }
    }

    private var isSubmitting: Bool {
        observer?.value is ParcelPreRegViewModelStateSubmitting
    }

    private var hasSaved: Bool {
        observer?.value is ParcelPreRegViewModelStateSaved
    }

    private var progressBar: some View {
        HStack(spacing: 6) {
            ForEach(0..<3, id: \.self) { i in
                RoundedRectangle(cornerRadius: 4)
                    .fill(i <= step ? Brand.orange : Brand.ink.opacity(0.12))
                    .frame(height: 4)
            }
        }
    }

    @ViewBuilder
    private var stepContent: some View {
        switch step {
        case 0: retailerStep
        case 1: detailsStep
        case 2: reviewStep
        default: EmptyView()
        }
    }

    private var retailerStep: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Pick a retailer").font(.headline).foregroundStyle(Brand.ink)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: 8)], spacing: 8) {
                    ForEach(retailers, id: \.self) { name in
                        Button(action: { retailer = name }) {
                            Text(name)
                                .font(.subheadline.weight(.semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(retailer == name ? Brand.orange.opacity(0.18) : Color.white.opacity(0.4))
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(retailer == name ? Brand.orange : Brand.ink.opacity(0.1), lineWidth: 1)
                                )
                                .foregroundStyle(Brand.ink)
                        }
                    }
                }
                if retailer == "Other" {
                    TextField("Retailer name", text: $customRetailer)
                        .textFieldStyle(.plain)
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(Brand.cream.opacity(0.6))
                        )
                }
            }
        }
    }

    private var detailsStep: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("Tell us about it").font(.headline).foregroundStyle(Brand.ink)
                TextField("e.g. Blue hoodie size M", text: $description, axis: .vertical)
                    .textFieldStyle(.plain)
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(Brand.cream.opacity(0.6))
                    )

                VStack(alignment: .leading, spacing: 6) {
                    Text("Declared value (£)".uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(Brand.ink.opacity(0.5))
                    TextField("250", text: $declaredValueGbp)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(.plain)
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(Brand.cream.opacity(0.6))
                        )
                }

                // Weight + dimensions are operator-stamped at warehouse
                // intake — asking the customer for a guess at order time
                // produced unreliable data and confused the cost-when-
                // ready expectation.  We surface a single info line in
                // their place so the customer knows the next step.
                CalloutBanner(
                    tint: Brand.cream.opacity(0.45),
                    icon: "info.circle.fill",
                    title: "Cost calculated after we receive",
                    message: "Our warehouse team will weigh and measure your parcel on arrival. You'll get a price email once it's been consolidated for shipment."
                )

                Text("Customs category")
                    .font(.caption.weight(.heavy)).tracking(1.4)
                    .foregroundStyle(Brand.cream.opacity(0.7))
                    .padding(.top, 8)
                Picker("Category", selection: $hsTier) {
                    ForEach(hsCategories) { cat in
                        Text(cat.label).tag(cat.key)
                    }
                }
                .pickerStyle(.menu)
                if let active = hsCategories.first(where: { $0.key == hsTier }) {
                    Text(active.note)
                        .font(.caption2)
                        .foregroundStyle(Brand.cream.opacity(0.55))
                }
            }
        }
    }

    private var reviewStep: some View {
        InkFeatureCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("Review").font(.headline)
                reviewLine("Retailer", retailer == "Other" ? customRetailer : retailer)
                reviewLine("Description", description)
                reviewLine("Declared value", declaredValueGbp.isEmpty ? "—" : "£ \(declaredValueGbp)")
                reviewLine("Weight & dimensions", "Captured at warehouse")
                reviewLine("Customs category", hsCategories.first(where: { $0.key == hsTier })?.label ?? hsTier)
            }
        }
    }

    @ViewBuilder
    private func dimensionField(_ placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .keyboardType(.decimalPad)
            .multilineTextAlignment(.center)
            .textFieldStyle(.plain)
            .padding(12)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Brand.cream.opacity(0.6))
            )
    }

    private func reviewLine(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label.uppercased())
                .font(.caption2.weight(.heavy)).tracking(2)
                .foregroundStyle(Brand.cream.opacity(0.6))
            Spacer()
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(Brand.cream)
        }
    }

    @ViewBuilder
    private var navButtons: some View {
        if hasSaved {
            Button("Done") { dismiss() }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.ink, foreground: .white))
        } else {
            HStack(spacing: 10) {
                if step > 0 && !isSubmitting {
                    Button("Back") { step -= 1 }
                        .buttonStyle(.bordered).tint(.secondary)
                }
                Button(step == 2 ? (isSubmitting ? "Submitting…" : "Create order") : "Next") {
                    if step == 2 { submit() } else { step += 1 }
                }
                .buttonStyle(GlassSheenButtonStyle())
                .disabled(!stepValid || isSubmitting)
            }
        }
    }

    private var stepValid: Bool {
        switch step {
        case 0: return retailer == "Other" ? !customRetailer.isEmpty : !retailer.isEmpty
        case 1: return !description.isEmpty
        case 2: return Double(declaredValueGbp) != nil
        default: return true
        }
    }

    private func submit() {
        guard let vm else { return }
        let resolvedRetailer = retailer == "Other" ? customRetailer : retailer
        let valuePence = Int64((Double(declaredValueGbp) ?? 0) * 100)
        // Insurance offering removed 2026-04-30 — the shared PreRegInput still
        // requires an InsuranceTier so we always pass the no-cost `standard`.
        // The shared/ DTOs and QuoteEngine still understand the field; webapp
        // and Android will catch up to the removal in their next pass (see
        // memory: insurance_removed.md).
        vm.submit(input: ParcelPreRegViewModel.PreRegInput(
            retailer: resolvedRetailer,
            description: description,
            declaredValueGbpPence: valuePence,
            insuranceTier: InsuranceTier.standard,
            market: market,
            shippingSpeed: "economy",
            hsTier: hsTier,
            // Weight + dimensions are stamped by the operator on receive
            // — never collected from the customer here. The shared DTO
            // still accepts them so older clients on the same channel
            // keep building, but we always send nil from this screen.
            weightKg: nil,
            lengthCm: nil,
            widthCm: nil,
            heightCm: nil
        ))
    }

    private func bootstrap() {
        guard vm == nil, let userId = env.currentUserID else { return }
        let model = ThapsusSdk.shared.parcelPreRegViewModel(userId: userId)
        vm = model
        observer = StateFlowObserver(initial: model.state.value) {
            model.state
        }
        if warehouseVm == nil {
            let wh = ThapsusSdk.shared.warehouseViewModel()
            warehouseVm = wh
            wh.load()
            warehouseObserver = StateFlowObserver(initial: wh.state.value) {
                wh.state
            }
        }
    }

    // MARK: - Warehouse address card

    @ViewBuilder
    private var warehouseAddressCard: some View {
        let auth = env.session as? AuthSessionAuthenticated
        let displayName = auth?.profile?.fullName?.trimmingCharacters(in: .whitespacesAndNewlines)
        let warehouseCode = auth?.profile?.warehouseId?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nilIfEmpty ?? "TC-XXXX"
        let lines: [String] = {
            if let loaded = warehouseObserver?.value as? WarehouseViewModelUiStateLoaded,
               let uk = loaded.addresses["UK"], !uk.lines.isEmpty {
                return uk.lines
            }
            return Self.defaultWarehouseLines
        }()

        InkFeatureCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 8) {
                    Image(systemName: "mappin.and.ellipse").foregroundStyle(Brand.orange)
                    Text("Ship your parcel here")
                        .font(.headline)
                        .foregroundStyle(Brand.cream)
                }
                Text("Use this address when you check out at the UK retailer.")
                    .font(.footnote)
                    .foregroundStyle(Brand.cream.opacity(0.7))

                VStack(alignment: .leading, spacing: 6) {
                    if let name = displayName, !name.isEmpty {
                        Text(name)
                            .font(.system(.title3, design: .monospaced).weight(.heavy))
                            .foregroundStyle(Brand.orange)
                    }
                    Text(warehouseCode)
                        .font(.system(.title3, design: .monospaced).weight(.heavy))
                        .foregroundStyle(Brand.orange)
                    ForEach(lines, id: \.self) { line in
                        Text(line)
                            .font(.system(.callout, design: .monospaced))
                            .foregroundStyle(Brand.cream.opacity(0.85))
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(.white.opacity(0.06))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(.white.opacity(0.12), lineWidth: 1)
                )

                Button(action: { copyAddress(lines: lines, code: warehouseCode, name: displayName) }) {
                    HStack(spacing: 8) {
                        Image(systemName: addressCopied ? "checkmark.circle.fill" : "doc.on.doc")
                        Text(addressCopied ? "Copied!" : "Copy address")
                    }
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
            }
        }
    }

    private func copyAddress(lines: [String], code: String, name: String?) {
        let header = [name?.nilIfEmpty, code].compactMap { $0 }
        UIPasteboard.general.string = (header + lines).joined(separator: "\n")
        addressCopied = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { addressCopied = false }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
