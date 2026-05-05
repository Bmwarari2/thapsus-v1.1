package com.thapsus.cargo.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Lightweight base for Kotlin "view models" that Swift will own.
 * Swift code is responsible for calling [clear] on `deinit`.
 *
 * (We intentionally do not depend on AndroidX ViewModel — KMP iOS-only target
 * has no need for the lifecycle lib, and Swift drives the lifetime.)
 */
abstract class SharedViewModel {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcherForUi())

    open fun clear() {
        scope.cancel()
    }
}

/** Resolved by SKIE/Kotlin-Native to MainQueue dispatcher on iOS. */
internal expect fun dispatcherForUi(): kotlinx.coroutines.CoroutineDispatcher
