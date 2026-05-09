package com.thapsus.cargo.data.remote

/**
 * Lightweight one-shot signals shared between the API client (where 401s
 * surface) and whatever UI lands the user on the sign-in screen.
 *
 * Why a plain object and not a SharedFlow / DI dependency:
 *
 *   - The signal is a single boolean that lives across at most one
 *     navigation transition. SharedFlow would be overkill.
 *   - The reader (Swift `SignInView.onAppear`) is on the iOS side and
 *     has no easy way to register a Kotlin observer without a DI hop;
 *     a static read of `AuthEventFlags.sessionExpired` is one line.
 *   - The writer (`ThapsusApiClient.onUnauthorized`) has no
 *     dependencies on the UI layer and shouldn't grow any.
 *
 * Lifetime: the signal is set when a request returns 401 and is
 * consumed (read + cleared) by the sign-in screen. If the user never
 * lands on sign-in (rare — they'd have to background the app and
 * never return), the flag persists harmlessly until process restart.
 */
object AuthEventFlags {
    /**
     * True when the most recent transition out of `Authenticated` was
     * caused by a server 401 (token expired / revoked / user disabled),
     * not by the user tapping Sign Out. The sign-in screen reads this
     * on appear, shows a banner, and clears it.
     */
    var sessionExpired: Boolean = false

    /**
     * Callback invoked when the API client detects a 401. Wired by
     * AuthRepository.init so the state flow flips from Authenticated
     * to SignedOut without waiting for the user to navigate.
     *
     * Without this, settings.clear() in the onUnauthorized hook leaves
     * AuthRepository._state stuck at Authenticated until the next
     * rehydrate / app launch — meaning the user could sit on a stale
     * authenticated screen retrying broken requests indefinitely.
     */
    internal var onServerSignOut: (() -> Unit)? = null

    /** Called by ThapsusApiClient.onUnauthorized after settings.clear(). */
    fun markServerSignOut() {
        sessionExpired = true
        onServerSignOut?.invoke()
    }
}
