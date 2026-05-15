package com.thapsus.cargo.android.ui.primitives

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thapsus.cargo.android.R
import com.thapsus.cargo.android.ui.theme.Brand

enum class WordmarkSize(val logo: Dp, val text: androidx.compose.ui.unit.TextUnit) {
    Small(28.dp, 16.sp),
    Medium(44.dp, 22.sp),
    Large(96.dp, 36.sp)
}

/**
 * Brand wordmark — chevron logo PNG + "Thapsus" (ink) + "Cargo" (orange).
 * Mirrors the iOS `BrandWordmark(size:)` view. The drawable
 * `thapsus_logo.png` is the same 180×180 asset iOS uses, ported into
 * Android's drawable bucket so the splash, sign-in hero, and home
 * top-bar all show identical artwork.
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
        Image(
            painter = painterResource(R.drawable.thapsus_logo),
            contentDescription = "Thapsus Cargo",
            modifier = Modifier.size(size.logo)
        )
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
