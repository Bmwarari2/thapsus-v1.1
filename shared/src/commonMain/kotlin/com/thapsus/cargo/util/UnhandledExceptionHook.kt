package com.thapsus.cargo.util

/**
 * Install a platform-specific unhandled-exception hook.
 *
 * On iOS this routes Kotlin/Native's `setUnhandledExceptionHook` to a
 * `println` (→ NSLog) line that always prints the throwable's class,
 * message, and full Kotlin stack trace BEFORE the runtime aborts the
 * process. Without this, K/N's default behaviour swallows the message
 * and Xcode just shows raw assembly at `Kotlin_processUnhandledException`,
 * making POD-style "app hangs/crashes on view X" reports almost
 * impossible to triage without a paired LLDB session.
 *
 * On Android this is currently a no-op — the JVM's default
 * `Thread.UncaughtExceptionHandler` already prints the throwable to
 * logcat with stack trace, which is enough for triage. We can swap in
 * a Crashlytics integration here later without changing call sites.
 *
 * Idempotent: safe to call from `ThapsusSdk.start()` even though that
 * function is itself idempotent.
 */
expect fun installUnhandledExceptionHook()
