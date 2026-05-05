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

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Send a parcel", systemImage: "shippingbox.fill")
                EditorialHeader(title: "New order",
                                subtitle: "We'll generate a label and book a slot on the next weekly flight.")

                // Compact process explainer — only shown before the
                // customer starts filling the form. Once they're past
                // step 0 we hide it so the progress bar + step content
                // get full focus and the screen doesn't grow stale
                // copy on every step.
                if step == 0 {
                    ProcessStepsCard(
                        title: "How New order works",
                        steps: [
                            ("1", "Tell us about it", "Pick the market, retailer and what you're sending."),
                            ("2", "Ship to our UK warehouse", "Use your warehouse address — we'll receive and weigh it."),
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
            ForEach(0..<4, id: \.self) { i in
                RoundedRectangle(cornerRadius: 4)
                    .fill(i <= step ? Brand.orange : Brand.ink.opacity(0.12))
                    .frame(height: 4)
            }
        }
    }

    @ViewBuilder
    private var stepContent: some View {
        switch step {
        case 0: marketStep
        case 1: retailerStep
        case 2: detailsStep
        case 3: reviewStep
        default: EmptyView()
        }
    }

    private var marketStep: some View {
        CrystalCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Where's the parcel coming from?")
                    .font(.headline).foregroundStyle(Brand.ink)
                Picker("Market", selection: $market) {
                    Text("United Kingdom").tag("UK")
                    Text("China").tag("China")
                }.pickerStyle(.segmented)
            }
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
                reviewLine("Market", market == "UK" ? "United Kingdom" : "China")
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
                Button(step == 3 ? (isSubmitting ? "Submitting…" : "Create order") : "Next") {
                    if step == 3 { submit() } else { step += 1 }
                }
                .buttonStyle(GlassSheenButtonStyle())
                .disabled(!stepValid || isSubmitting)
            }
        }
    }

    private var stepValid: Bool {
        switch step {
        case 0: return !market.isEmpty
        case 1: return retailer == "Other" ? !customRetailer.isEmpty : !retailer.isEmpty
        case 2: return !description.isEmpty
        case 3: return Double(declaredValueGbp) != nil
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
    }
}
