package com.thapsus.cargo.android.ui.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.thapsus.cargo.android.ui.theme.Brand

/** Cream→peach gradient backdrop, the Android twin of `AppBackground` / `LiquidBackdrop`. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val gradient = Brush.verticalGradient(listOf(Brand.cream, Brand.peach))
    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        content()
    }
}
