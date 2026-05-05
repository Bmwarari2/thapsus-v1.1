// CutoffBannerView.swift
// Renders the upcoming flight cut-off as a glass banner. Mounted on
// CustomerHomeView and CustomerDashboardView. Hides itself when no
// consolidation is open.

import SwiftUI
import ThapsusShared

struct CutoffBannerView: View {
    @State private var vm: CutoffBannerViewModel?
    @State private var observer: StateFlowObserver<CutoffBannerViewModelState>?
    @State private var now: Date = Date()

    private let ticker = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    var body: some View {
        Group {
            if let open = observer?.value as? CutoffBannerViewModelStateOpen {
                banner(for: open.consolidation)
            } else {
                EmptyView()
            }
        }
        .task {
            guard vm == nil else { return }
            let model = ThapsusSdk.shared.cutoffBannerViewModel()
            vm = model
            model.load()
            observer = StateFlowObserver(initial: model.state.value) { model.state }
        }
        .onReceive(ticker) { now = $0 }
        .onDisappear { vm?.clear(); vm = nil; observer = nil }
    }

    @ViewBuilder
    private func banner(for c: ConsolidationDto) -> some View {
        InkCard {
            HStack(alignment: .center, spacing: 14) {
                Image(systemName: "airplane.departure")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Brand.cream)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Next flight cut-off")
                        .font(.eyebrow)
                        .foregroundStyle(Brand.cream.opacity(0.7))
                    Text(countdown(to: c.cutoffAt))
                        .font(.system(.title2, design: .rounded).weight(.bold))
                        .foregroundStyle(Brand.cream)
                        .contentTransition(.numericText())
                    Text("Get parcels to our UK warehouse before the cut-off to make this flight.")
                        .font(.caption)
                        .foregroundStyle(Brand.cream.opacity(0.65))
                        .lineLimit(2)
                }
                Spacer()
            }
        }
    }

    private func countdown(to iso: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let target = formatter.date(from: iso) ??
                ISO8601DateFormatter().date(from: iso)
        else { return iso }
        let interval = max(0, target.timeIntervalSince(now))
        let days = Int(interval) / 86_400
        let hours = (Int(interval) % 86_400) / 3_600
        let minutes = (Int(interval) % 3_600) / 60
        if days >= 1 {
            return "\(days)d \(hours)h \(minutes)m left"
        } else if hours >= 1 {
            return "\(hours)h \(minutes)m left"
        } else if interval > 0 {
            return "\(minutes)m left"
        } else {
            return "Cut-off passed"
        }
    }
}
