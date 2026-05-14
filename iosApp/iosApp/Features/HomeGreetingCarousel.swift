// HomeGreetingCarousel.swift
// Rotating welcome carousel on the Home tab — replaces the static
// "Hi {firstName} 👋" line. Sourced from CustomerDashboardViewModel's
// `headlinePrefix` + `greetings` StateFlows (built in the KMP shared
// module so iOS and Android render identical copy).
//
// Behaviour:
//   - Auto-advance every 5 seconds with a 0.35s ease-in-out fade-cross.
//   - Pauses while the user is long-pressing the card.
//   - Single greeting → renders static (no rotation).
//   - Tap → pushes the destination view onto the Home stack and calls
//     `markGreetingSeen(id:)` so the freshness rule dismisses the
//     greeting on next emission.

import SwiftUI
import ThapsusShared

struct HomeGreetingCarousel: View {
    let vm: CustomerDashboardViewModel?
    /// Called when the carousel taps a greeting whose destination is
    /// `HomeGreetingDestination.NpsSurvey`. The host view owns the sheet
    /// state and presents `NpsSurveyView` — surveys aren't a push.
    var onNpsTap: () -> Void = {}

    @State private var prefixObs: StateFlowObserver<String>? = nil
    @State private var greetingsObs: StateFlowObserver<[HomeGreeting]>? = nil

    @State private var index: Int = 0
    @State private var paused: Bool = false

    private let rotateInterval: Duration = .seconds(5)
    private let fade: Animation = .easeInOut(duration: 0.35)

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(prefixObs?.value ?? "hi.")
                .font(.body(15, weight: .semibold))
                .foregroundStyle(LG.fg3)
                .accessibilityAddTraits(.isHeader)

            content
        }
        .padding(.top, 8)
        .padding(.bottom, 4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .onAppear { bootstrap() }
        .task { await rotationLoop() }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        let list = currentGreetings
        if let current = list[safe: index] {
            interactiveGreeting(current)
                .simultaneousGesture(
                    LongPressGesture(minimumDuration: 0.15)
                        .onChanged { _ in paused = true }
                        .onEnded { _ in paused = false }
                )

            if list.count > 1 {
                HStack(spacing: 6) {
                    ForEach(0..<list.count, id: \.self) { i in
                        Capsule()
                            .fill(i == index ? LG.fg.opacity(0.85) : LG.fg.opacity(0.20))
                            .frame(width: i == index ? 18 : 6, height: 4)
                            .animation(fade, value: index)
                    }
                }
                .padding(.top, 2)
            }
        } else {
            // Pre-bootstrap fallback so the layout doesn't pop in.
            Text("ready when you are.")
                .font(.display(24, weight: .heavy))
                .foregroundStyle(LG.fg)
        }
    }

    @ViewBuilder
    private func interactiveGreeting(_ current: HomeGreeting) -> some View {
        // NPS surveys are a sheet, not a stack push. Branch here so the
        // host view can mount `NpsSurveyView` via its own state binding.
        if current.destination is HomeGreetingDestinationNpsSurvey {
            Button {
                vm?.markGreetingSeen(greetingId: current.id)
                onNpsTap()
            } label: {
                greetingText(current)
            }
            .buttonStyle(.plain)
        } else {
            NavigationLink {
                current.destination.makeDestinationView()
                    .onAppear { vm?.markGreetingSeen(greetingId: current.id) }
            } label: {
                greetingText(current)
            }
            .buttonStyle(.plain)
        }
    }

    private func greetingText(_ current: HomeGreeting) -> some View {
        Text(current.body)
            .font(.display(24, weight: .heavy))
            .foregroundStyle(LG.fg)
            .multilineTextAlignment(.leading)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .id(current.id)
            .transition(.opacity.combined(with: .move(edge: .bottom)))
    }

    // MARK: - State helpers

    private var currentGreetings: [HomeGreeting] {
        greetingsObs?.value ?? []
    }

    private func bootstrap() {
        guard let vm else { return }
        if prefixObs == nil {
            prefixObs = StateFlowObserver(initial: vm.headlinePrefix.value) {
                vm.headlinePrefix
            }
        }
        if greetingsObs == nil {
            greetingsObs = StateFlowObserver(initial: vm.greetings.value) {
                vm.greetings
            }
        }
    }

    private func rotationLoop() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: rotateInterval)
            if paused { continue }
            let count = currentGreetings.count
            guard count > 1 else { continue }
            withAnimation(fade) {
                index = (index + 1) % count
            }
        }
    }
}

// MARK: - Safe array subscript

private extension Array {
    subscript(safe i: Int) -> Element? {
        return (i >= 0 && i < count) ? self[i] : nil
    }
}
