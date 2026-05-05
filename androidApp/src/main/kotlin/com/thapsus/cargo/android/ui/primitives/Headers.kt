package com.thapsus.cargo.android.ui.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thapsus.cargo.android.ui.theme.Brand

enum class WordmarkSize(val logo: Dp, val text: androidx.compose.ui.unit.TextUnit) {
    Small(28.dp, 16.sp),
    Medium(44.dp, 22.sp),
    Large(96.dp, 36.sp)
}

/**
 * Brand wordmark — chevron logo + "Thapsus" (ink) + "Cargo" (orange).
 * Mirrors the iOS `BrandWordmark(size:)` view.
 */
@Composable
fun BrandWordmark(
    size: WordmarkSize = WordmarkSize.Medium,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Placeholder chevron disc — the iOS app uses a real PNG; we'll port that
        // asset into drawable/ during Phase B polish. For now a solid orange dot
        // keeps layout intact and matches brand color.
        Box(
            modifier = Modifier
                .size(size.logo)
                .background(Brand.Orange, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.FlightTakeoff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size.logo * 0.55f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Thapsus",
                color = Brand.ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = size.text
            )
            Text(
                "Cargo",
                color = Brand.Orange,
                fontWeight = FontWeight.ExtraBold,
                fontSize = size.text
            )
        }
    }
}

/** Eyebrow pill above an editorial header — Compose twin of `EyebrowPill`. */
@Composable
fun EyebrowPill(
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Brand.Orange.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Brand.Orange, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = Brand.Orange,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp
        )
    }
}

/** Editorial header — eyebrow + display title + subtitle. */
@Composable
fun EditorialHeader(
    title: String,
    eyebrow: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (eyebrow != null) {
            Text(
                eyebrow,
                color = Brand.Orange,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp
            )
        }
        Text(
            title,
            color = Brand.ink,
            style = MaterialTheme.typography.displayLarge
        )
        if (subtitle != null) {
            Text(subtitle, color = Brand.ink.copy(alpha = 0.7f))
        }
    }
}
