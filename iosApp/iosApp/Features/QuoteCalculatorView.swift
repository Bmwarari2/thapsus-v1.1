// QuoteCalculatorView.swift
// Liquid-glass redesign of the customer-facing shipping calculator.
// Editable number fields (no +/- steppers) for weight + dimensions, glass
// surcharge toggles, hero quote card with monospaced total.

import SwiftUI
import ThapsusShared

private let phoneSurchargePence: Int64 = 7_500
private let laptopSurchargePence: Int64 = 6_500

struct QuoteCalculatorView: View {
    @State private var lengthCm: Double = 30
    @State private var widthCm: Double = 20
    @State private var heightCm: Double = 20
    @State private var actualKg: Double = 2.0
    // Customer-facing calculator has no declared-value UI — keep at 0.
    // Customs is excluded from the quote entirely via skipCustoms=true
    // on computeQuote(); a footer warns customers that VAT + Duty may
    // apply on KRA clearance. Matches the web calculator at thapsus.uk/pricing.
    @State private var declaredValuePence: Int64 = 0
    @State private var channel: PricingChannel = .ukAir

    // String shadows so the user can type freely (and clear the field
    // mid-edit) without the value snapping back to a formatted string.
    @State private var weightText: String = "2.00"
    @State private var lengthText: String = "30"
    @State private var widthText: String = "20"
    @State private var heightText: String = "20"

    @FocusState private var focusedField: NumberField?
    private enum NumberField: Hashable { case weight, length, width, height }

    @State private var includesPhone: Bool = false
    @State private var includesLaptop: Bool = false

    @State private var quoteObserver: StateFlowObserver<QuoteEngine.Quote?>?
    @State private var errorObserver: StateFlowObserver<String?>?
    @State private var gbpToKesObserver: StateFlowObserver<KotlinDouble?>?
    @State private var vm: QuoteViewModel?

    private var surchargePence: Int64 {
        (includesPhone ? phoneSurchargePence : 0) + (includesLaptop ? laptopSurchargePence : 0)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Same engine that prices your final shipment.")
                    .font(.body(14, weight: .medium))
                    .foregroundStyle(LG.fg3)
                    .padding(.top, 4)
                    .padding(.bottom, 4)

                dimensionsCard
                surchargesCard

                Button(action: compute) {
                    HStack(spacing: 6) {
                        Text("Calculate")
                        Image(systemName: "arrow.right")
                            .font(.footnote.weight(.bold))
                    }
                }
                .buttonStyle(LGPrimaryButtonStyle())

                if let q = quoteObserver?.value {
                    quoteCard(q)
                }
                if let error = errorObserver?.value ?? nil {
                    LGStatusBanner(tone: .err, title: "Couldn't price", message: error)
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 100)
        }
        .navigationTitle("Quote")
        .glassNavigationBar()
        .scrollContentBackground(.hidden)
        .background(LiquidGlassBackground())
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focusedField = nil }
                    .fontWeight(.semibold)
                    .foregroundStyle(LG.accent2)
            }
        }
        .task {
            guard vm == nil else { return }
            let v = ThapsusSdk.shared.quoteViewModel()
            self.vm = v
            v.loadPricing()
            self.quoteObserver = StateFlowObserver(initial: nil) { v.quote }
            self.errorObserver = StateFlowObserver(initial: nil) { v.error }
            self.gbpToKesObserver = StateFlowObserver(initial: nil) { v.gbpToKes }
        }
        .onDisappear {
            vm?.clear(); vm = nil
            quoteObserver = nil; errorObserver = nil; gbpToKesObserver = nil
        }
    }

    // MARK: - Dimensions

    @ViewBuilder
    private var dimensionsCard: some View {
        GlassPanel(corner: LG.Radius.xl, padding: 18) {
            VStack(spacing: 0) {
                numberRow(
                    label: "Weight", suffix: "kg",
                    text: $weightText, focus: .weight,
                    range: 0.1...1000, format: "%.2f",
                    commit: { actualKg = clamp(parse($0), 0.1, 1000, fallback: actualKg) }
                )
                divider
                numberRow(
                    label: "Length", suffix: "cm",
                    text: $lengthText, focus: .length,
                    range: 1...300, format: "%.0f",
                    commit: { lengthCm = clamp(parse($0), 1, 300, fallback: lengthCm) }
                )
                divider
                numberRow(
                    label: "Width", suffix: "cm",
                    text: $widthText, focus: .width,
                    range: 1...300, format: "%.0f",
                    commit: { widthCm = clamp(parse($0), 1, 300, fallback: widthCm) }
                )
                divider
                numberRow(
                    label: "Height", suffix: "cm",
                    text: $heightText, focus: .height,
                    range: 1...300, format: "%.0f",
                    commit: { heightCm = clamp(parse($0), 1, 300, fallback: heightCm) }
                )
            }
        }
    }

    private var divider: some View {
        Rectangle().fill(LG.line).frame(height: 1).padding(.vertical, 12)
    }

    @ViewBuilder
    private func numberRow(
        label: String,
        suffix: String,
        text: Binding<String>,
        focus: NumberField,
        range: ClosedRange<Double>,
        format: String,
        commit: @escaping (String) -> Void
    ) -> some View {
        HStack(spacing: 12) {
            Text(label)
                .font(.body(14, weight: .semibold))
                .foregroundStyle(LG.fg2)
            Spacer()
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                TextField("", text: text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.mono(22, weight: .bold))
                    .foregroundStyle(LG.fg)
                    .focused($focusedField, equals: focus)
                    .frame(minWidth: 80)
                    .onSubmit { commit(text.wrappedValue) }
                    .onChange(of: focusedField) { _, new in
                        if new != focus {
                            commit(text.wrappedValue)
                            text.wrappedValue = String(format: format, parse(text.wrappedValue))
                        }
                    }
                Text(suffix)
                    .font(.mono(12, weight: .medium))
                    .foregroundStyle(LG.fg3)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { focusedField = focus }
    }

    private func parse(_ text: String) -> Double {
        Double(text.replacingOccurrences(of: ",", with: ".")) ?? 0
    }

    private func clamp(_ value: Double, _ low: Double, _ high: Double, fallback: Double) -> Double {
        guard value > 0 else { return fallback }
        return min(max(value, low), high)
    }

    // MARK: - Surcharges

    @ViewBuilder
    private var surchargesCard: some View {
        GlassPanel(corner: LG.Radius.xl, padding: 16) {
            VStack(spacing: 0) {
                surchargeRow(
                    title: "Phone surcharge",
                    subtitle: "Lithium battery handling · £75",
                    isOn: $includesPhone
                )
                divider
                surchargeRow(
                    title: "Laptop surcharge",
                    subtitle: "Lithium battery handling · £65",
                    isOn: $includesLaptop
                )
            }
        }
    }

    @ViewBuilder
    private func surchargeRow(title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.body(14, weight: .semibold))
                    .foregroundStyle(LG.fg)
                Text(subtitle)
                    .font(.body(12.5, weight: .medium))
                    .foregroundStyle(LG.fg3)
            }
            Spacer()
            LGToggle(isOn: isOn)
        }
    }

    // MARK: - Quote hero

    /// GBP→KES rate from the shared module's QuoteViewModel. Nil until the
    /// first `/exchange/rates` fetch lands or when that fetch fails — in
    /// either case `formatMoney` falls back to displaying £ so the customer
    /// still sees a usable number.
    private var gbpToKes: Double? {
        guard let v = gbpToKesObserver?.value?.doubleValue, v > 0 else { return nil }
        return v
    }

    /// Format a GBP amount for customer display. KES when the live rate is
    /// available; £ fallback otherwise. KES is rounded to whole shillings —
    /// pricing receipts elsewhere do the same and fractional shillings would
    /// look like a bug.
    private func formatMoney(_ gbp: Double) -> String {
        if let rate = gbpToKes {
            let value = Int((gbp * rate).rounded())
            return "KES \(value.formatted(.number))"
        }
        return String(format: "£%.2f", gbp)
    }

    @ViewBuilder
    private func quoteCard(_ q: QuoteEngine.Quote) -> some View {
        let surchargeMajor = Double(surchargePence) / 100
        let totalWithSurchargeMajor = q.total.major + surchargeMajor
        GlassPanel(corner: LG.Radius.xl, padding: 20, tint: LG.accentSoft) {
            VStack(alignment: .leading, spacing: 14) {
                LGEyebrow(text: "Estimated total", tone: .accent)
                Text(formatMoney(totalWithSurchargeMajor))
                    .font(.mono(38, weight: .bold))
                    .foregroundStyle(LG.fg)
                    .contentTransition(.numericText())

                VStack(spacing: 6) {
                    line("Chargeable weight", String(format: "%.2f kg", q.volumetric.chargeableKg))
                    line("Base shipping", formatMoney(q.freight.major))
                    if q.handling.major > 0 {
                        line("UK handling", formatMoney(q.handling.major))
                    }
                    if q.perKgFee.major > 0 {
                        line("Trunking", formatMoney(q.perKgFee.major))
                    }
                    if includesPhone { line("Phone surcharge", formatMoney(75.0), emphasis: true) }
                    if includesLaptop { line("Laptop surcharge", formatMoney(65.0), emphasis: true) }
                    if q.processingFee.major > 0 {
                        line("Card processing", formatMoney(q.processingFee.major))
                    }
                }

                Text("Customs (VAT + Duty) may be charged separately by Kenya Revenue Authority on clearance and are not included in this estimate.")
                    .font(.body(11, weight: .regular))
                    .foregroundStyle(LG.fg3)
                    .padding(.top, 2)
            }
        }
    }

    private func line(_ k: String, _ v: String, emphasis: Bool = false) -> some View {
        HStack {
            Text(k)
                .font(.body(13, weight: .medium))
                .foregroundStyle(emphasis ? LG.accent2 : LG.fg3)
            Spacer()
            Text(v)
                .font(.mono(13, weight: .semibold))
                .foregroundStyle(emphasis ? LG.accent2 : LG.fg)
        }
    }

    private func compute() {
        // Make sure any in-flight edit is committed before pricing.
        actualKg = clamp(parse(weightText), 0.1, 1000, fallback: actualKg)
        lengthCm = clamp(parse(lengthText), 1, 300, fallback: lengthCm)
        widthCm  = clamp(parse(widthText),  1, 300, fallback: widthCm)
        heightCm = clamp(parse(heightText), 1, 300, fallback: heightCm)
        focusedField = nil

        let dims = ParcelDimensions(
            lengthCm: lengthCm, widthCm: widthCm, heightCm: heightCm, actualKg: actualKg
        )
        // SKIE bridges Kotlin function-level defaults to required Swift args,
        // so even though items/electronicsItemKey/hsTier/skipCustoms all have
        // default values on the Kotlin side, the Swift call site has to supply
        // them. skipCustoms=true keeps the public calculator's quote free of
        // customs; the footer warns customers that VAT + Duty may apply on
        // clearance. Per-item HS-code customs is the order-creation flow.
        vm?.computeQuote(
            dims: dims,
            channel: channel,
            insurance: InsuranceTier.standard,
            declaredValuePence: declaredValuePence,
            items: [],
            electronicsItemKey: nil,
            hsTier: nil,
            skipCustoms: true
        )
    }
}
