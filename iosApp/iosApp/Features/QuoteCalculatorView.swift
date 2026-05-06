// QuoteCalculatorView.swift
// Liquid-glass redesign of the customer-facing shipping calculator.
// Dimensions card (steppers via mono number + stepper) · surcharges card
// with glass toggles · hero quote card with monospaced total.

import SwiftUI
import ThapsusShared

private let phoneSurchargePence: Int64 = 7_500
private let laptopSurchargePence: Int64 = 6_500

struct QuoteCalculatorView: View {
    @State private var lengthCm: Double = 30
    @State private var widthCm: Double = 20
    @State private var heightCm: Double = 20
    @State private var actualKg: Double = 2.0
    @State private var declaredValuePence: Int64 = 5_000
    @State private var channel: PricingChannel = .ukAir

    @State private var includesPhone: Bool = false
    @State private var includesLaptop: Bool = false

    @State private var quoteObserver: StateFlowObserver<QuoteEngine.Quote?>?
    @State private var errorObserver: StateFlowObserver<String?>?
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
                            .font(.system(size: 13, weight: .bold))
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
        .task {
            guard vm == nil else { return }
            let v = ThapsusSdk.shared.quoteViewModel()
            self.vm = v
            v.loadPricing()
            self.quoteObserver = StateFlowObserver(initial: nil) { v.quote }
            self.errorObserver = StateFlowObserver(initial: nil) { v.error }
        }
        .onDisappear { vm?.clear(); vm = nil; quoteObserver = nil; errorObserver = nil }
    }

    // MARK: - Dimensions

    @ViewBuilder
    private var dimensionsCard: some View {
        GlassPanel(corner: LG.Radius.xl, padding: 18) {
            VStack(spacing: 0) {
                dimRow(label: "Weight", suffix: "kg", value: $actualKg, range: 0.1...80, step: 0.1, format: "%.2f")
                divider
                dimRow(label: "Length", suffix: "cm", value: $lengthCm, range: 1...300, step: 1, format: "%.0f")
                divider
                HStack(spacing: 12) {
                    dimRow(label: "Width", suffix: "cm", value: $widthCm, range: 1...300, step: 1, format: "%.0f", inline: true)
                    dimRow(label: "Height", suffix: "cm", value: $heightCm, range: 1...300, step: 1, format: "%.0f", inline: true)
                }
                .padding(.vertical, 6)
            }
        }
    }

    private var divider: some View {
        Rectangle().fill(LG.line).frame(height: 1).padding(.vertical, 12)
    }

    @ViewBuilder
    private func dimRow(
        label: String,
        suffix: String,
        value: Binding<Double>,
        range: ClosedRange<Double>,
        step: Double,
        format: String,
        inline: Bool = false
    ) -> some View {
        if inline {
            VStack(alignment: .leading, spacing: 6) {
                Text(label.uppercased())
                    .font(.body(11, weight: .bold))
                    .tracking(0.6)
                    .foregroundStyle(LG.fg3)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(String(format: format, value.wrappedValue))
                        .font(.mono(22, weight: .bold))
                        .foregroundStyle(LG.fg)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(suffix)
                        .font(.mono(12, weight: .medium))
                        .foregroundStyle(LG.fg3)
                    Stepper("", value: value, in: range, step: step).labelsHidden()
                }
            }
        } else {
            HStack {
                Text(label)
                    .font(.body(13.5, weight: .semibold))
                    .foregroundStyle(LG.fg2)
                Spacer()
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(String(format: format, value.wrappedValue))
                        .font(.mono(22, weight: .bold))
                        .foregroundStyle(LG.fg)
                    Text(suffix)
                        .font(.mono(12, weight: .medium))
                        .foregroundStyle(LG.fg3)
                }
                Stepper("", value: value, in: range, step: step).labelsHidden()
            }
        }
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

    @ViewBuilder
    private func quoteCard(_ q: QuoteEngine.Quote) -> some View {
        let surchargeMajor = Double(surchargePence) / 100
        let totalWithSurchargeMajor = q.total.major + surchargeMajor
        GlassPanel(corner: LG.Radius.xl, padding: 20, tint: LG.accentSoft) {
            VStack(alignment: .leading, spacing: 14) {
                LGEyebrow(text: "Estimated total", tone: .accent)
                Text(String(format: "£%.2f", totalWithSurchargeMajor))
                    .font(.mono(38, weight: .bold))
                    .foregroundStyle(LG.fg)
                    .contentTransition(.numericText())

                VStack(spacing: 6) {
                    line("Chargeable weight", String(format: "%.2f kg", q.volumetric.chargeableKg))
                    line("Base shipping", String(format: "£%.2f", q.freight.major))
                    if q.handling.major > 0 {
                        line("UK handling", String(format: "£%.2f", q.handling.major))
                    }
                    if q.perKgFee.major > 0 {
                        line("Trunking", String(format: "£%.2f", q.perKgFee.major))
                    }
                    if includesPhone { line("Phone surcharge", "£75.00", emphasis: true) }
                    if includesLaptop { line("Laptop surcharge", "£65.00", emphasis: true) }
                    if q.processingFee.major > 0 {
                        line("Card processing", String(format: "£%.2f", q.processingFee.major))
                    }
                }
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
        let dims = ParcelDimensions(
            lengthCm: lengthCm, widthCm: widthCm, heightCm: heightCm, actualKg: actualKg
        )
        vm?.computeQuote(
            dims: dims,
            channel: channel,
            insurance: InsuranceTier.standard,
            declaredValuePence: declaredValuePence
        )
    }
}
