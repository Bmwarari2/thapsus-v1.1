package com.thapsus.cargo.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun ThapsusTheme(content: @Composable () -> Unit) {
    val store = rememberAppearanceStore()
    val systemDark = isSystemInDarkTheme()
    val resolvedDark = when (store.theme) {
        ThapsusThemePreference.System -> systemDark
        ThapsusThemePreference.Light -> false
        ThapsusThemePreference.Dark -> true
    }
    val scheme = if (resolvedDark) {
        darkColorScheme(
            primary = Brand.Orange,
            onPrimary = Color.White,
            secondary = Brand.Gold,
            background = Color(0xFF100C0C),
            surface = Color(0xFF100C0C),
            onBackground = Color(0xFFF2F5F7),
            onSurface = Color(0xFFF2F5F7)
        )
    } else {
        lightColorScheme(
            primary = Brand.Orange,
            onPrimary = Color.White,
            secondary = Brand.Gold,
            background = Color(0xFFFDF7F4),
            surface = Color(0xFFFDF7F4),
            onBackground = Color(0xFF101214),
            onSurface = Color(0xFF101214)
        )
    }
    CompositionLocalProvider(
        LocalAppearanceStore provides store,
        LocalIsDarkTheme provides resolvedDark
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ThapsusTypography,
            content = content
        )
    }
}
