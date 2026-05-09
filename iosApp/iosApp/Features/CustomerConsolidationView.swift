// CustomerConsolidationView.swift
// Read-only weekly-flight summary for parcels that have been assigned to a
// consolidation. Reachable from ParcelDetailView when consolidation_id is set.

import SwiftUI
import ThapsusShared

struct CustomerConsolidationView: View {
    let consolidationId: String

    @State private var vm: CustomerConsolidationViewModel? = nil
    @State private var observer: StateFlowObserver<CustomerConsolidationViewModelUiState>? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                EyebrowPill(label: "Weekly flight", systemImage: "airplane")
                EditorialHeader(title: "Your consolidation",
                                subtitle: "The weekly UK→Nairobi flight your parcel is on.")
                content
            }
            .padding(20)
        }
        .scrollContentBackground(.hidden)
        .liquidBackdrop()
        .navigationTitle("Consolidation")
        .glassNavigationBar()
        .task { bootstrap() }
    }

    @ViewBuilder
    private var content: some View {
        switch observer?.value {
        case let loaded as CustomerConsolidationViewModelUiStateLoaded:
            consolidationCard(loaded.consolidation)
        case is CustomerConsolidationViewModelUiStateLoading:
            ProgressView().frame(maxWidth: .infinity)
        case let err as CustomerConsolidationViewModelUiStateError:
            ErrorBanner(title: "Couldn't load", message: err.message)
        default: EmptyView()
        }
    }

    private func consolidationCard(_ c: ConsolidationDto) -> some View {
        InkFeatureCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Status").font(.caption.weight(.semibold)).foregroundStyle(Brand.cream.opacity(0.7))
                    Spacer()
                    Text(String(describing: c.status).uppercased())
                        .font(.caption2.weight(.heavy)).tracking(2)
                        .foregroundStyle(Brand.orange)
                }
                Divider().background(.white.opacity(0.15))
                detailLine("Week start", c.weekStart)
                detailLine("Cut-off", c.cutoffAt)
                detailLine("Departure", c.departureAt ?? "—")
                Divider().background(.white.opacity(0.15))
                HStack {
                    statBlock("Total parcels", "\(c.totalParcels)")
                    Spacer()
                    statBlock("Total kg", String(format: "%.1f", Double(truncating: c.totalKg as NSNumber)))
                }
            }
        }
    }

    private func detailLine(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.caption).foregroundStyle(Brand.cream.opacity(0.7))
            Spacer()
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(Brand.cream)
        }
    }

    private func statBlock(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased()).font(.caption2.weight(.heavy)).tracking(2)
                .foregroundStyle(Brand.cream.opacity(0.6))
            Text(value).font(.title3.weight(.heavy)).foregroundStyle(Brand.orange)
        }
    }

    private func bootstrap() {
        guard vm == nil else { return }
        let model = ThapsusSdk.shared.customerConsolidationViewModel(consolidationId: consolidationId)
        vm = model
        observer = StateFlowObserver(initial: model.state.value) {
            model.state
        }
    }
}
