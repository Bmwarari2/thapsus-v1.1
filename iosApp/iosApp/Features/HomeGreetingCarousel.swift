// HomeGreetingCarousel.swift
// Rotating welcome carousel on the Home tab — replaces the static
// "Hi {firstName} 👋" line. Sourced from
// `CustomerDashboardViewModel.greetings` (KMP shared module), with the
// time-of-day prefix composed locally from the `firstName` the host
// view passes in. Composing locally side-steps a race where the VM's
// `auth.state` snapshot doesn't always carry `profile` at first
// emission on iOS — the host already has reliable access via
// `env.session.profile.fullName`.
//
// Behaviour:
//   - One continuous line ("Good morning, Brian. Your shipment is on
//     its way to Kenya.") at the display font, not two lines at
//     different sizes.
//   - Auto-advance every 8 seconds with a 0.35s ease-in-out fade-cross.
//   - Pauses while the user is long-pressing the card.
//   - Single greeting → renders static (no rotation).
//   - Tap → pushes the destination view onto the Home stack and calls
//     `markGreetingSeen(id:)` so the freshness rule dismisses the
//     greeting on next emission.
//   - NPS destination is intercepted: instead of pushing, the host
//     presents the survey via the `onNpsTap` callback + its own sheet.

import SwiftUI
import ThapsusShared

struct HomeGreetingCarousel: View {
    let vm: CustomerDashboardViewModel?
    /// First name to weave into the time-of-day prefix (e.g. "Brian").
    /// Empty string drops the comma + name and renders just
    /// "Good morning. Your shipment …".
    var firstName: String = ""
    /// Called when the carousel taps a greeting whose destination is
    /// `HomeGreetingDestination.NpsSurvey`. The host view owns the sheet
    /// state and presents `NpsSurveyView` — surveys aren't a push.
    var onNpsTap: () -> Void = {}

    @State private var greetingsObs: StateFlowObserver<[HomeGreeting]>? = nil
    @State private var index: Int = 0
    @State private var paused: Bool = false

    private let rotateInterval: Duration = .seconds(8)
    private let fade: Animation = .easeInOut(duration: 0.35)

    /// Default timezone is the device's — the customer is on the move
    /// (UK shopping, KE delivery) so device-local matches their context.
    private var timeZone: TimeZone { TimeZone.current }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
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
                .padding(.top, 10)
            }
        } else {
            // Pre-bootstrap fallback so the layout doesn't pop in.
            Text(fullSentenceFor(body: "Ready when you are."))
                .font(.display(24, weight: .heavy))
                .foregroundStyle(LG.fg)
        }
    }

    @ViewBuilder
    private func interactiveGreeting(_ current: HomeGreeting) -> some View {
        // NPS surveys are a sheet, not a stack push. Branch here so the
        // host view can mount `NpsSurveyView` via its own state binding.
        // SKIE bridges sealed-class variants as nested types (dot syntax) —
        // see HomeGreetingNavigation.swift for the load-bearing comment.
        if current.destination is HomeGreetingDestination.NpsSurvey {
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
        Text(fullSentenceFor(body: current.body))
            .font(.display(24, weight: .heavy))
            .foregroundStyle(LG.fg)
            .multilineTextAlignment(.leading)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .id(current.id)
            .transition(.opacity.combined(with: .move(edge: .bottom)))
    }

    /// Composes the full headline — time-of-day prefix + greeting body — as
    /// a single sentence. `TimeOfDayGreeter` lives in the KMP shared module
    /// so iOS and Android render identical copy. We use the
    /// primitive-friendly `greetingLineFor(epochMs:tzId:firstName:)`
    /// overload to avoid the kotlinx-datetime <-> Foundation bridge.
    private func fullSentenceFor(body: String) -> String {
        let epochMs = Int64(Date().timeIntervalSince1970 * 1_000)
        let prefix = TimeOfDayGreeter.shared.greetingLineFor(
            epochMs: epochMs,
            tzId: timeZone.identifier,
            firstName: firstName
        )
        return "\(prefix) \(body)"
    }

    // MARK: - State helpers

    private var currentGreetings: [HomeGreeting] {
        greetingsObs?.value ?? []
    }

    private func bootstrap() {
        guard let vm else { return }
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
