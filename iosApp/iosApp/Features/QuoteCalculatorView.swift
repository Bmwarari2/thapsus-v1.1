// QuoteCalculatorView.swift
// Customer-facing shipping calculator. Calls the same QuoteEngine the server
// uses (via QuoteViewModel) so the number a customer sees here matches what
// they'll be charged at intake.
//
// Adds a dedicated "Electronics & Special Handling" card with toggles for the
// two surcharges customers most often forget about (phone £75, laptop £65).
// These are shown in the breakdown and added to the displayed total — the same
// surcharge is enforced server-side at intake.

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
            VStack(alignment: .leading, spacing: 16) {
                EditorialHeader(
                    eyebrow: "What will it cost?",
                    title: "Shipping\nCalculator",
                    subtitle: "Estimate before you buy. Same engine as our intake desk."
                )

                CalloutBanner(
                    icon: "bolt.heart.fill",
                    title: "Electronics & Special Handling",
                    message: "Phones, laptops, and lithium-cell devices need extra screening — toggle below so your quote reflects the real handling fee."
                )

                dimensionsCard
                electronicsCard

                Button(action: compute) {
                    Text("Calculate")
                }
                .buttonStyle(InkButtonStyle())

                if let q = quoteObserver?.value {
                    quoteCard(q)
                }
                if let error = errorObserver?.value ?? nil {
                    ErrorBanner(title: "Couldn't price", message: error)
                }

                Color.clear.frame(height: 24)
            }
            .padding(20)
        }
        .navigationTitle("Calculator")
        .glassNavigationBar()
        .scrollContentBackground(.hidden)
        .appBackground()
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

    @ViewBuilder
    private var dimensionsCard: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("Parcel dimensions").font(.headline).foregroundStyle(Brand.ink)
                stepper("Length", suffix: "cm", value: $lengthCm, range: 1...300)
                stepper("Width", suffix: "cm", value: $widthCm, range: 1...300)
                stepper("Height", suffix: "cm", value: $heightCm, range: 1...300)
                stepper("Mass", suffix: "kg", value: $actualKg, range: 0.1...80, step: 0.1, format: "%.1f")
            }
        }
    }

    @ViewBuilder
    private var electronicsCard: some View {
        SoftCard(tint: Brand.orange.opacity(0.06)) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 8) {
                    Image(systemName: "cpu.fill").foregroundStyle(Brand.orange)
                    Text("Special handling").font(.headline).foregroundStyle(Brand.ink)
                }

                handlingToggle(
                    title: "Phone",
                    subtitle: "Lithium-cell screening + foam pack",
                    price: "+£75",
                    icon: "iphone",
                    isOn: $includesPhone
                )
                handlingToggle(
                    title: "Laptop",
                    subtitle: "Cushioned crate + reinforced corners",
                    price: "+£65",
                    icon: "laptopcomputer",
                    isOn: $includesLaptop
                )
            }
        }
    }

    @ViewBuilder
    private func handlingToggle(
        title: String,
        subtitle: String,
        price: String,
        icon: String,
        isOn: Binding<Bool>
    ) -> some View {
        Toggle(isOn: isOn) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(Brand.cream)
                    .frame(width: 38, height: 38)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(Brand.ink)
                    )
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Brand.ink)
                        Text(price)
                            .font(.caption.weight(.bold))
                            .foregroundStyle(Brand.orange)
                    }
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .toggleStyle(SwitchToggleStyle(tint: Brand.orange))
    }

    private func compute() {
        let dims = ParcelDimensions(
            lengthCm: lengthCm, widthCm: widthCm, heightCm: heightCm, actualKg: actualKg
        )
        // Insurance offering removed 2026-04-30 — pass `.standard` (no premium)
        // so the QuoteEngine signature stays satisfied without surfacing tiers.
        vm?.computeQuote(
            dims: dims,
            channel: channel,
            insurance: InsuranceTier.standard,
            declaredValuePence: declaredValuePence
        )
    }

    @ViewBuilder
    private func stepper(
        _ label: String,
        suffix: String,
        value: Binding<Double>,
        range: ClosedRange<Double>,
        step: Double = 1,
        format: String = "%.0f"
    ) -> some View {
        HStack {
            Text(label).foregroundStyle(Brand.ink)
            Spacer()
            HStack(spacing: 6) {
                Text(String(format: format, value.wrappedValue))
                    .font(.subheadline.monospacedDigit().weight(.semibold))
                    .foregroundStyle(Brand.ink)
                    .frame(minWidth: 48, alignment: .trailing)
                Text(suffix).font(.caption).foregroundStyle(.secondary)
            }
            Stepper("", value: value, in: range, step: step).labelsHidden()
        }
    }

    @ViewBuilder
    private func quoteCard(_ q: QuoteEngine.Quote) -> some View {
        let surchargeMajor = Double(surchargePence) / 100
        let totalWithSurchargeMajor = q.total.major + surchargeMajor

        InkCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Total").font(.eyebrow).foregroundStyle(Brand.cream.opacity(0.7))
                    Spacer()
                    Text("Inc. VAT").font(.caption).foregroundStyle(Brand.cream.opacity(0.5))
                }
                Text(String(format: "£%.2f", totalWithSurchargeMajor))
                    .font(.system(size: 48, weight: .bold, design: .rounded))
                    .foregroundStyle(Brand.cream)
                    .contentTransition(.numericText())

                Divider().background(Brand.cream.opacity(0.18))

                line("Chargeable", String(format: "%.2f kg", q.volumetric.chargeableKg))
                line("Freight", String(format: "£%.2f", q.freight.major))
                line("UK handling", String(format: "£%.2f", q.handling.major))

                // Trunking (per-kg fee). Hidden when zero so the breakdown
                // stays tidy for the common case, but rendered when set so
                // the line items reconcile against the headline total — the
                // 2026-04-30 customer-facing bug was a £75 trunking fee
                // hiding inside `q.perKgFee` without ever rendering, leaving
                // the customer staring at a £104 total backed by a £29
                // visible breakdown.
                if q.perKgFee.major > 0 {
                    line("Trunking", String(format: "£%.2f", q.perKgFee.major))
                }

                // Insurance offering removed 2026-04-30 (S1-0); we still
                // surface a non-zero premium if the engine somehow returns
                // one so the totals always reconcile.
                if q.insurancePremium.major > 0 {
                    line("Insurance", String(format: "£%.2f", q.insurancePremium.major))
                }

                if includesPhone {
                    line("Phone surcharge", "+£75.00", emphasis: true)
                }
                if includesLaptop {
                    line("Laptop surcharge", "+£65.00", emphasis: true)
                }

                line("Card processing", String(format: "£%.2f", q.processingFee.major))
            }
        }
    }

    @ViewBuilder
    private func line(_ k: String, _ v: String, emphasis: Bool = false) -> some View {
        HStack {
            Text(k)
                .font(.subheadline)
                .foregroundStyle(emphasis ? Brand.orange : Brand.cream.opacity(0.7))
            Spacer()
            Text(v)
                .font(.subheadline.monospacedDigit().weight(.semibold))
                .foregroundStyle(emphasis ? Brand.orange : Brand.cream)
        }
    }
}
