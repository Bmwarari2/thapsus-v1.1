package com.thapsus.cargo.util

actual fun installUnhandledExceptionHook() {
    // No-op on Android — the JVM's default uncaught-exception handler
    // already prints the throwable to logcat with stack trace, which is
    // enough for triage. Swap in a Crashlytics integration here when we
    // wire one up without having to touch call sites.
}
