// AppHaptics.swift
// Single source of truth for haptic feedback across the iOS app.
//
// We expose semantic cases — `.tap`, `.action`, `.success`, `.warning`,
// `.error` — so the call site reads at intent level and the choice of
// underlying UIKit feedback generator stays here.
//
// Two flavours:
//   • `.sensoryFeedback(.appHaptic(.success), trigger: state)` — SwiftUI
//     modifier. Use this whenever feedback should fire as a side effect of
//     a state change. iOS 17+ honours `Reduce Haptics` accessibility setting
//     automatically.
//   • `AppHaptics.fire(.tap)` — imperative call, for tap handlers and
//     non-SwiftUI contexts (e.g. UIKit-bridged sheets). Falls back to the
//     classic UIKit generators.
//
// Wiring guidance (audit P1 N3):
//   • `.tap`     — tab switch, chip selection, list-row tap that doesn't navigate
//   • `.action`  — primary CTA tap that leads to navigation / mutation
//   • `.success` — payment confirmed, BFM quote accepted, POD captured
//   • `.warning` — validation fail, rejection toast
//   • `.error`   — network failure, payment decline
//
// Do NOT wire on every realtime push (would buzz incessantly), text-field
// focus changes (annoying), or NavigationLink taps (system already haptics).

import SwiftUI
import UIKit

enum AppHaptic {
    case tap
    case action
    case success
    case warning
    case error
}

extension SensoryFeedback {
    /// Bridge an `AppHaptic` case to the underlying SwiftUI feedback style.
    /// Use as `.sensoryFeedback(.appHaptic(.success), trigger: someValue)`.
    static func appHaptic(_ kind: AppHaptic) -> SensoryFeedback {
        switch kind {
        case .tap:     return .selection
        case .action:  return .impact(weight: .light)
        case .success: return .success
        case .warning: return .warning
        case .error:   return .error
        }
    }
}

enum AppHaptics {
    /// Imperative fire — for tap handlers, completion callbacks, anywhere a
    /// SwiftUI modifier is awkward. Generators are created on each call so we
    /// don't have to manage prepared-state lifetimes; the latency is fine for
    /// discrete confirmation events.
    @MainActor
    static func fire(_ kind: AppHaptic) {
        switch kind {
        case .tap:
            UISelectionFeedbackGenerator().selectionChanged()
        case .action:
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case .success:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        case .warning:
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
        case .error:
            UINotificationFeedbackGenerator().notificationOccurred(.error)
        }
    }
}
