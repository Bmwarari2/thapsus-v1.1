package com.thapsus.cargo.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Brand tokens — exact hex match for the iOS BrandOrange/Ink/Cream/Peach/Navy/Gold colorsets.
// Light/dark variants on Ink/Cream/Peach mirror the iOS asset catalog appearance entries
// and resolve through [LocalIsDarkTheme] so a user-overridden Light/Dark choice
// (driven by [AppearanceStore]) flips the entire palette, not just MaterialTheme.
object Brand {
    val Orange = Color(0xFFF5731A)
    val Navy = Color(0xFF1E3A5F)
    val Gold = Color(0xFFC9A24A)

    private val InkLight = Color(0xFF101214)
    private val InkDark = Color(0xFFF2F5F7)

    private val CreamLight = Color(0xFFFDF7F4)
    private val CreamDark = Color(0xFF100C0C)

    private val PeachLight = Color(0xFFFCEAE6)
    private val PeachDark = Color(0xFF1A1616)

    val ink: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) InkDark else InkLight

    val cream: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) CreamDark else CreamLight

    val peach: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) PeachDark else PeachLight
}
