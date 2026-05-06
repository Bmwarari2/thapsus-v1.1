package com.thapsus.cargo.presentation

import com.thapsus.cargo.util.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Lightweight base for Kotlin "view models" that Swift will own.
 * Swift code is responsible for calling [clear] on `deinit`.
 *
 * (We intentionally do not depend on AndroidX ViewModel — KMP iOS-only target
 * has no need for the lifecycle lib, and Swift drives the lifetime.)
 *
 * The shared `loggingExceptionHandler` is installed on `scope` so a
 * stray `scope.launch { … }` whose body throws lands as a console log
 * line instead of propagating to the K/N abort handler — the latter
 * surfaces to the user as an app freeze (see CoroutineErrorHandler.kt).
 */
abstract class SharedViewModel {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcherForUi() + loggingExceptionHandler)

    open fun clear() {
        scope.cancel()
    }
}

/** Resolved by SKIE/Kotlin-Native to MainQueue dispatcher on iOS. */
internal expect fun dispatcherForUi(): kotlinx.coroutines.CoroutineDispatcher
