package com.thapsus.cargo.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Shared CoroutineExceptionHandler for every CoroutineScope created by
 * Thapsus repos / view-models.
 *
 * Without this, a `scope.launch { … }` whose body throws and isn't
 * caught propagates to `kotlinx.coroutines.internal.propagateExceptionFinalResort`
 * which on Kotlin/Native (iOS) calls the K/N abort handler — surfacing
 * to the user as an app freeze rather than a recoverable error.
 *
 * The QuoteViewModel comment (added 2026-04-30) traced one such freeze
 * on the customer Calculator tab; another arrived 2026-05-06 with the
 * disassembly pointing at the same handler. Rather than chasing each
 * site individually, every scope now installs this handler so a stray
 * throw surfaces as a log line and the app keeps running.
 *
 * Cancellation is normal flow control (e.g. SwiftUI tab switch tearing
 * down `Task { … }` observers), so we deliberately swallow it without
 * logging — otherwise the console fills up with noise on every screen
 * change.
 *
 * `println` is used deliberately: on Kotlin/Native it routes to NSLog,
 * which shows up in Xcode's debug console and Console.app. We don't
 * pull in a full logging dependency just for this.
 */
val loggingExceptionHandler: CoroutineExceptionHandler =
    CoroutineExceptionHandler { ctx, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        val name = ctx[kotlinx.coroutines.CoroutineName.Key]?.name ?: "anonymous"
        println(
            "[Thapsus] Unhandled coroutine exception in '$name': " +
                "${throwable::class.simpleName ?: "Throwable"}: ${throwable.message ?: "(no message)"}"
        )
    }
