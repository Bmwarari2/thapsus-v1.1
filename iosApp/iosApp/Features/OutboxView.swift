// OutboxView.swift
// Rider's "outbox" tab. Surfaces the count of queued mutations and a manual
// flush. Critical when a rider parks and gets coverage back after dead zones.

import SwiftUI
import ThapsusShared

struct OutboxView: View {
    @State private var vm: OutboxViewModel?
    @State private var pending: StateFlowObserver<KotlinLong>?
    @State private var busy: StateFlowObserver<KotlinBoolean>?
    @State private var lastFlushed: StateFlowObserver<KotlinInt?>?
    @State private var lastError: StateFlowObserver<String?>?

    @ScaledMetric(relativeTo: .largeTitle) private var outboxPendingCountSize: CGFloat = 56

    var body: some View {
        ScrollView {
            GlassEffectContainer(spacing: 16) {
                VStack(spacing: 16) {
                    SectionHeader(
                        title: "Outbox",
                        subtitle: "Queued mutations waiting on connectivity"
                    )

                    GlassCard(tint: Brand.gold.opacity(0.18)) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Pending").font(.caption).foregroundStyle(.secondary)
                            Text("\(pending?.value.intValue ?? 0)")
                                .font(.system(size: outboxPendingCountSize, weight: .bold, design: .rounded))
                                .contentTransition(.numericText())
                            // Always show the result of the last manual flush,
                            // including "0 sent" — riders need feedback that the
                            // tap registered, even when nothing flushed.
                            if let n = lastFlushed?.value?.intValue {
                                Text(n == 0
                                     ? "Last flush sent 0 — see error below."
                                     : "Last flush sent \(n)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }

                    // Surface the most recent retry error so the rider knows
                    // WHY the queue isn't draining. Previously the count
                    // just sat there with no diagnosis.
                    if let err = lastError?.value, !err.isEmpty {
                        ErrorBanner(title: "Last sync error", message: err)
                    }

                    Button(action: flush) {
                        if busy?.value.boolValue == true {
                            ProgressView().tint(Brand.gold)
                        } else {
                            Text("Flush now").frame(maxWidth: .infinity).padding(.vertical, 4)
                        }
                    }
                    .buttonStyle(.glassProminent)
                    .tint(Brand.gold)
                    .disabled(busy?.value.boolValue == true)

                    GlassCard {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("How this works").font(.headline)
                            Text("Capturing a POD on poor signal saves the event locally. The app retries with exponential back-off whenever connectivity is available, or whenever you tap flush.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(20)
            }
        }
        .navigationTitle("Outbox")
        .glassNavigationBar()
        .task {
            guard vm == nil else { return }
            let v = ThapsusSdk.shared.outboxViewModel()
            self.vm = v
            self.pending = StateFlowObserver(initial: KotlinLong(value: 0)) {
                v.pending
            }
            self.busy = StateFlowObserver(initial: KotlinBoolean(bool: false)) {
                v.busy
            }
            self.lastFlushed = StateFlowObserver(initial: nil) {
                v.lastFlushed
            }
            self.lastError = StateFlowObserver(initial: nil) {
                v.lastError
            }
        }
        .onDisappear {
            vm?.clear(); vm = nil
            pending = nil; busy = nil; lastFlushed = nil; lastError = nil
        }
    }

    private func flush() {
        vm?.flushNow()
    }
}
