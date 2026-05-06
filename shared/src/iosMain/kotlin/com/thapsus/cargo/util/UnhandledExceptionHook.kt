package com.thapsus.cargo.util

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook

private var installed: Boolean = false

@OptIn(ExperimentalNativeApi::class)
actual fun installUnhandledExceptionHook() {
    if (installed) return
    installed = true
    setUnhandledExceptionHook { throwable ->
        val name = throwable::class.simpleName ?: "Throwable"
        val message = throwable.message ?: "(no message)"
        // Single-line summary first so the Xcode console search ('!!!')
        // jumps straight to the class + message — the stack trace below
        // can be many lines.
        println("[Thapsus] !!! UNHANDLED KOTLIN EXCEPTION: $name: $message")
        println(throwable.stackTraceToString())
    }
}
