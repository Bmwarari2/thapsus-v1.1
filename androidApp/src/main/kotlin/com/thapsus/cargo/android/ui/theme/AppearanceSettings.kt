package com.thapsus.cargo.android.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

// User-facing theme override — mirrors the iOS ThapsusTheme enum so
// "appearance" reads identically on both platforms. `System` defers to
// the device setting; `Light` / `Dark` force the choice.
enum class ThapsusThemePreference(
    val storageKey: String,
    val label: String,
    val detail: String
) {
    System("system", "System", "Follow device setting"),
    Light("light", "Light", "Cream background, ink text"),
    Dark("dark", "Warm charcoal background, cream text");

    companion object {
        fun fromKey(key: String?): ThapsusThemePreference =
            entries.firstOrNull { it.storageKey == key } ?: System
    }
}

private const val PREFS_NAME = "thapsus.appearance"
private const val PREF_KEY = "theme"

// Backed by SharedPreferences so the choice survives process restarts.
// Exposed via [LocalAppearanceStore] so any composable (e.g. the settings
// screen) can mutate the value and have ThapsusTheme recompose with the
// new color scheme.
@Stable
class AppearanceStore internal constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = mutableStateOf(
        ThapsusThemePreference.fromKey(prefs.getString(PREF_KEY, null))
    )

    val theme: ThapsusThemePreference get() = state.value

    fun set(value: ThapsusThemePreference) {
        state.value = value
        prefs.edit { putString(PREF_KEY, value.storageKey) }
    }
}

@Composable
fun rememberAppearanceStore(): AppearanceStore {
    val context = LocalContext.current
    return remember(context.applicationContext) { AppearanceStore(context) }
}

// The user's appearance store. Provided by ThapsusTheme so settings UI
// can read + mutate the persisted choice.
val LocalAppearanceStore: ProvidableCompositionLocal<AppearanceStore> =
    staticCompositionLocalOf {
        error("AppearanceStore not provided. Wrap content in ThapsusTheme().")
    }

// Resolved dark-mode flag for the current composition. Brand tokens read
// this instead of `isSystemInDarkTheme()` so a Light/Dark override
// (driven by AppearanceStore) flips the entire palette, not just the
// MaterialTheme color scheme.
//
// Default `false` keeps Brand tokens valid if read outside ThapsusTheme
// (e.g. unit tests or @Preview without the theme wrapper) — the production
// app is always wrapped in ThapsusTheme so this default never fires.
val LocalIsDarkTheme: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { false }
