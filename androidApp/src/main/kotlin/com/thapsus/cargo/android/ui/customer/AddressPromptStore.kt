package com.thapsus.cargo.android.ui.customer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

// SharedPreferences-backed flag tracking whether the post-auth
// address-capture prompt has been resolved (saved OR explicitly
// skipped). Mirrors the iOS UserDefaults key
// `thapsus.address.prompt.dismissed_v1` so the two platforms share the
// same semantic. Reinstalling resets and the prompt re-appears.
//
// Edit access to the delivery address from the Account tab remains
// canonical; this flag only governs the proactive sheet.

private const val PREFS_NAME = "thapsus.address"
private const val PREF_KEY_DISMISSED = "prompt.dismissed_v1"

@Stable
class AddressPromptStore internal constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = mutableStateOf(prefs.getBoolean(PREF_KEY_DISMISSED, false))

    val isDismissed: Boolean get() = state.value

    fun markDismissed() {
        state.value = true
        prefs.edit { putBoolean(PREF_KEY_DISMISSED, true) }
    }
}

@Composable
fun rememberAddressPromptStore(): AddressPromptStore {
    val context = LocalContext.current
    return remember(context.applicationContext) { AddressPromptStore(context) }
}
