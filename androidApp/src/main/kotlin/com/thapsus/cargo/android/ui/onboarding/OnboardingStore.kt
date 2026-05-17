package com.thapsus.cargo.android.ui.onboarding

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

// SharedPreferences-backed completion flag for the first-launch
// walkthrough. Mirrors the iOS UserDefaults key thapsus.onboarding.completed_v1
// so the two platforms stay aligned. Reinstalling clears storage and
// re-shows onboarding on both sides.
//
// Pattern matches `AppearanceStore` — Composable state wrapped around
// the SharedPreferences read, so toggling `complete()` recomposes the
// gate in `RootScreen` immediately.

private const val PREFS_NAME = "thapsus.onboarding"
private const val PREF_KEY_COMPLETED = "completed_v1"

@Stable
class OnboardingStore internal constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = mutableStateOf(prefs.getBoolean(PREF_KEY_COMPLETED, false))

    val isCompleted: Boolean get() = state.value

    fun complete() {
        state.value = true
        prefs.edit { putBoolean(PREF_KEY_COMPLETED, true) }
    }
}

@Composable
fun rememberOnboardingStore(): OnboardingStore {
    val context = LocalContext.current
    return remember(context.applicationContext) { OnboardingStore(context) }
}
