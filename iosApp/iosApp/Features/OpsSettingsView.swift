// OpsSettingsView.swift
// Admin settings: exchange rates, fees, promotions, prohibited dictionary.

import SwiftUI
import ThapsusShared

struct OpsSettingsView: View {
    @State private var vm: OpsSettingsViewModel? = nil
    @State private var observer: StateFlowObserver<OpsSettingsViewModelUiState>? = nil
    @State private var pairDrafts: [String: String] = [:]
    @State private var feeDrafts: [String: String] = [:]
    @State private var showAddProhibited: Bool = false
    @State private var showAddTier: Bool = false

    private static let supportedPairs = ["USD_KES", "GBP_KES", "EUR_KES", "CNY_KES"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Operations", systemImage: "gearshape.fill")
                EditorialHeader(title: "Ops settings",
                                subtitle: "Edit pricing, fees, exchange rates and the prohibited dictionary.")
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Ops settings")
        .glassNavigationBar()
        .sheet(isPresented: $showAddProhibited) {
            AddProhibitedSheet { term, severity, reason in
                vm?.addProhibited(term: term, severity: severity, reason: reason)
                showAddProhibited = false
            }
        }
        .sheet(isPresented: $showAddTier) {
            AddPricingTierSheet { channel, minKg, maxKg, gbpPerKg, notes in
                vm?.createTier(
                    channel: channel,
                    minKg: minKg,
                    maxKg: maxKg,
                    gbpPerKg: gbpPerKg,
                    notes: notes
                )
                showAddTier = false
            }
        }
        .task { bootstrap() }
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as OpsSettingsViewModelUiStateLoaded:
            ratesSection(loaded.rates)
            feesSection(loaded.fees)
            tiersSection(loaded.tiers)
            promosSection(loaded.promotions)
            prohibitedSection(loaded.prohibited)
        case is OpsSettingsViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
        case let err as OpsSettingsViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    @ViewBuilder
    private func ratesSection(_ rates: [ExchangeRateDto]) -> some View {
        SectionHeader(title: "Exchange rates")
        let byPair = Dictionary(uniqueKeysWithValues: rates.map { ($0.currencyPair, $0) })
        ForEach(Self.supportedPairs, id: \.self) { pair in
            rateCard(pair: pair, dto: byPair[pair])
        }
        CrystalCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Bulk update").font(.headline).foregroundStyle(Brand.ink)
                Text("Edits the four pairs together in one round-trip. Empty fields are skipped.")
                    .font(.caption2).foregroundStyle(.secondary)
                Button {
                    let parsed: [String: Double] = pairDrafts
                        .compactMapValues { Double($0) }
                        .filter { $1 > 0 }
                    if !parsed.isEmpty {
                        vm?.setRates(rates: parsed.mapValues { KotlinDouble(value: $0) })
                        pairDrafts.removeAll()
                    }
                } label: {
                    Label("Save all changes", systemImage: "arrow.up.circle.fill")
                }
                .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))
                .disabled(pairDrafts.values.compactMap { Double($0) }.allSatisfy { $0 <= 0 })
            }
        }
    }

    @ViewBuilder
    private func rateCard(pair: String, dto: ExchangeRateDto?) -> some View {
        let draft = pairDrafts[pair] ?? ""
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(humanPair(pair)).font(.headline).foregroundStyle(Brand.ink)
                        if let updated = dto?.updatedAt {
                            Text("Last updated \(updated)").font(.caption2).foregroundStyle(.secondary)
                        } else {
                            Text("Never set — defaults shown").font(.caption2).foregroundStyle(.orange)
                        }
                    }
                    Spacer()
                    Text(dto.map { String(format: "%.4f", $0.rate) } ?? "—")
                        .font(.system(.title3, design: .monospaced).weight(.heavy))
                        .foregroundStyle(Brand.orange)
                }
                HStack(spacing: 8) {
                    TextField("New rate", text: Binding(
                        get: { draft },
                        set: { pairDrafts[pair] = $0 }
                    ))
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.plain)
                    .padding(10)
                    .background(
                        RoundedRectangle(cornerRadius: 10).fill(Brand.cream.opacity(0.6))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10).stroke(Brand.ink.opacity(0.06), lineWidth: 1)
                    )
                    Button("Save") {
                        if let r = Double(draft), r > 0 {
                            vm?.setRate(currencyPair: pair, rate: r)
                            pairDrafts[pair] = nil
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Brand.orange)
                    .disabled((Double(draft) ?? 0) <= 0)
                }
            }
        }
    }

    private func humanPair(_ pair: String) -> String {
        switch pair {
        case "USD_KES": return "USD → KES"
        case "GBP_KES": return "GBP → KES"
        case "EUR_KES": return "EUR → KES"
        case "CNY_KES": return "CNY → KES"
        default: return pair
        }
    }

    @ViewBuilder
    private func feesSection(_ fees: [AdminFeeDto]) -> some View {
        SectionHeader(title: "Fees")
        if fees.isEmpty {
            CrystalCard {
                Text("No fees configured. Run migration 001a if pricing seeds didn't fire.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        }
        ForEach(fees, id: \.id) { fee in
            feeCard(fee)
        }
    }

    @ViewBuilder
    private func feeCard(_ fee: AdminFeeDto) -> some View {
        let draft = feeDrafts[fee.id] ?? format(fee.amount)
        CrystalCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(fee.label).font(.headline).foregroundStyle(Brand.ink)
                        Text(fee.code).font(.caption.monospaced()).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text(fee.isActive ? "ACTIVE" : "OFF")
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(fee.isActive ? .green : .secondary)
                        .padding(.horizontal, 6).padding(.vertical, 3)
                        .background(Capsule().fill((fee.isActive ? Color.green : Color.gray).opacity(0.16)))
                }
                HStack(spacing: 8) {
                    Text(fee.isPercentage ? "%" : fee.currency)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(minWidth: 36, alignment: .leading)
                    TextField(format(fee.amount), text: Binding(
                        get: { draft },
                        set: { feeDrafts[fee.id] = $0 }
                    ))
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.plain)
                    .padding(8)
                    .background(
                        RoundedRectangle(cornerRadius: 10).fill(Brand.cream.opacity(0.6))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10).stroke(Brand.ink.opacity(0.06), lineWidth: 1)
                    )
                    Button("Save") {
                        if let v = Double(draft), v >= 0 {
                            vm?.setFeeAmount(id: fee.id, amount: v)
                            feeDrafts[fee.id] = nil
                        }
                    }
                    .buttonStyle(.bordered)
                    .tint(Brand.orange)
                    .disabled((Double(draft) ?? -1) < 0 || draft == format(fee.amount))
                }
                Toggle("Enabled", isOn: Binding(
                    get: { fee.isActive },
                    set: { vm?.toggleFee(id: fee.id, isActive: $0) }
                ))
            }
        }
    }

    @ViewBuilder
    private func tiersSection(_ tiers: [PricingTierDto]) -> some View {
        SectionHeader(title: "Pricing tiers")
        Button(action: { showAddTier = true }) {
            HStack { Image(systemName: "plus.circle.fill"); Text("Add tier") }
        }
        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

        if tiers.isEmpty {
            CrystalCard {
                Text("No tiers yet — add one to drive the quote calculator.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        } else {
            ForEach(tiers, id: \.id) { tier in
                CrystalCard {
                    HStack(alignment: .firstTextBaseline) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(channelLabel(tier.channel))
                                .font(.headline).foregroundStyle(Brand.ink)
                            Text(String(format: "%@–%@ kg",
                                        format(tier.minKg), format(tier.maxKg)))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(String(format: "£%@/kg", format(tier.gbpPerKg)))
                            .font(.system(.title3, design: .monospaced).weight(.heavy))
                            .foregroundStyle(Brand.orange)
                        Toggle("", isOn: Binding(
                            get: { tier.isActive },
                            set: { newValue in
                                vm?.updateTier(
                                    id: tier.id,
                                    gbpPerKg: nil,
                                    minKg: nil,
                                    maxKg: nil,
                                    isActive: KotlinBoolean(bool: newValue),
                                    notes: nil,
                                    effectiveTo: nil
                                )
                            }
                        ))
                        .labelsHidden()
                    }
                }
            }
        }
    }

    private func channelLabel(_ c: PricingChannel) -> String {
        switch c {
        case .ukAir: return "UK · Air"
        case .ukSea: return "UK · Sea"
        case .chinaAir: return "China · Air"
        @unknown default: return String(describing: c)
        }
    }

    @ViewBuilder
    private func promosSection(_ promos: [AdminPromotionDto]) -> some View {
        if !promos.isEmpty {
            SectionHeader(title: "Promotions")
            ForEach(promos, id: \.id) { p in
                CrystalCard {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(p.code).font(.headline.monospaced()).foregroundStyle(Brand.orange)
                            Spacer()
                            Text("\(p.uses)/\(p.maxUses ?? 0) used")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Text("\(p.type): \(format(p.value))").font(.subheadline)
                        if let desc = p.description_, !desc.isEmpty {
                            Text(desc).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func prohibitedSection(_ items: [ProhibitedItemDto]) -> some View {
        SectionHeader(title: "Prohibited dictionary")
        Button(action: { showAddProhibited = true }) {
            HStack { Image(systemName: "plus.circle.fill"); Text("Add term") }
        }
        .buttonStyle(GlassSheenButtonStyle(fill: Brand.orange, foreground: .white))

        if items.isEmpty {
            CrystalCard { Text("No entries currently visible.").font(.subheadline).foregroundStyle(.secondary) }
        } else {
            ForEach(items, id: \.id) { item in
                CrystalCard {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.term.capitalized).font(.headline).foregroundStyle(Brand.ink)
                            Text(String(describing: item.severity).uppercased())
                                .font(.caption2.weight(.heavy)).tracking(2)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button(role: .destructive, action: { vm?.removeProhibited(id: item.id) }) {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.bordered)
                        .tint(.red)
                        .accessibilityLabel("Remove \(item.term)")
                    }
                }
            }
        }
    }

    private func format(_ amount: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.maximumFractionDigits = 2
        return f.string(from: NSNumber(value: amount)) ?? "\(amount)"
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.opsSettingsViewModel()
        vm = model
        model.load()
        observer = StateFlowObserver(initial: model.state.value) { model.state }
    }
}

private struct AddProhibitedSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var term: String = ""
    @State private var severity: String = "prohibited"
    @State private var reason: String = ""
    let onSubmit: (String, String, String?) -> Void

    var body: some View {
        NavigationStack {
            Form {
                TextField("Term (e.g. lithium battery)", text: $term)
                Picker("Severity", selection: $severity) {
                    Text("Prohibited").tag("prohibited")
                    Text("Restricted").tag("restricted")
                    Text("Dangerous goods").tag("dangerous_goods")
                }
                Section("Reason") { TextEditor(text: $reason).frame(minHeight: 100) }
            }
            .navigationTitle("Add term")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") { onSubmit(term, severity, reason.isEmpty ? nil : reason) }
                        .disabled(term.isEmpty)
                }
            }
        }
    }
}

private struct AddPricingTierSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var channel: PricingChannel = .ukAir
    @State private var minKg: String = "0"
    @State private var maxKg: String = "5"
    @State private var gbpPerKg: String = "10"
    @State private var notes: String = ""
    let onSubmit: (PricingChannel, Double, Double, Double, String?) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Picker("Channel", selection: $channel) {
                    Text("UK · Air").tag(PricingChannel.ukAir)
                    Text("UK · Sea").tag(PricingChannel.ukSea)
                    Text("China · Air").tag(PricingChannel.chinaAir)
                }
                Section("Weight band (kg)") {
                    TextField("Min kg", text: $minKg).keyboardType(.decimalPad)
                    TextField("Max kg", text: $maxKg).keyboardType(.decimalPad)
                }
                Section("Rate") {
                    TextField("GBP per kg", text: $gbpPerKg).keyboardType(.decimalPad)
                }
                Section("Notes") { TextEditor(text: $notes).frame(minHeight: 80) }
            }
            .navigationTitle("Add pricing tier")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        guard
                            let minV = Double(minKg),
                            let maxV = Double(maxKg),
                            let rate = Double(gbpPerKg),
                            maxV > minV, rate > 0
                        else { return }
                        onSubmit(channel, minV, maxV, rate, notes.isEmpty ? nil : notes)
                    }
                    .disabled(Double(minKg) == nil || Double(maxKg) == nil || Double(gbpPerKg) == nil)
                }
            }
        }
    }
}
