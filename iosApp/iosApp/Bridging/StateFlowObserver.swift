// StateFlowObserver.swift
// Bridges Kotlin StateFlow → SwiftUI @Observable via SKIE.
//
// SKIE 0.10 wraps `StateFlow<T>` as `SkieSwiftStateFlow<T>` which itself
// conforms to `AsyncSequence`. We just `for await` over it directly — no
// `.toAsyncSequence()` shim, no AsyncStream conversion.

import Foundation
import Observation

@Observable
final class StateFlowObserver<T>: @unchecked Sendable {
    var value: T

    private var task: Task<Void, Never>?

    /// Initialise with the current value of the flow plus a builder that
    /// returns the SKIE-wrapped flow itself. The flow is iterated as an
    /// AsyncSequence and each emission updates `value` on the main actor.
    init<S: AsyncSequence>(initial: T, _ stream: @escaping () -> S) where S.Element == T {
        self.value = initial
        let seq = stream()
        self.task = Task { @MainActor [weak self] in
            do {
                for try await element in seq {
                    self?.value = element
                }
            } catch {
                // Flow termination — typically a coroutine cancellation. Ignore.
            }
        }
    }

    deinit { task?.cancel() }
}
