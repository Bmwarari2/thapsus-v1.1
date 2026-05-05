package com.thapsus.cargo.android.ui.primitives

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thapsus.cargo.android.ui.theme.Brand

private val CardCorner = 22.dp

/** Soft material card — the Compose twin of `SoftCard`. Lives over the gradient. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(
            containerColor = (tint ?: Brand.cream).copy(alpha = if (tint == null) 0.78f else 1f)
        ),
        border = BorderStroke(1.dp, Brand.ink.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

/** High-contrast ink card — the Compose twin of `InkCard`. */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(
            containerColor = Brand.ink,
            contentColor = Brand.cream
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            content()
        }
    }
}

/** Soft callout banner — the Compose twin of `CalloutBanner`. */
@Composable
fun CalloutBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.WarningAmber,
    tint: Color = Brand.Orange.copy(alpha = 0.16f)
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint),
        border = BorderStroke(1.dp, Brand.Orange.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = Brand.Orange)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Brand.ink)
                Text(message, color = Brand.ink.copy(alpha = 0.7f))
            }
        }
    }
}
