package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.android.ui.theme.LocalAppearanceStore
import com.thapsus.cargo.android.ui.theme.ThapsusThemePreference

@Composable
fun AppearanceSettingsScreen() {
    val store = LocalAppearanceStore.current
    val current = store.theme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        EyebrowPill(label = "Appearance")
        EditorialHeader(
            title = "Choose your theme",
            subtitle = "System matches your device. Light and Dark override it."
        )

        SoftCard {
            Column {
                ThapsusThemePreference.entries.forEachIndexed { index, theme ->
                    AppearanceRow(
                        theme = theme,
                        selected = theme == current,
                        onSelect = { store.set(theme) }
                    )
                    if (index < ThapsusThemePreference.entries.lastIndex) {
                        HorizontalDivider(
                            color = Brand.ink.copy(alpha = 0.08f),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppearanceRow(
    theme: ThapsusThemePreference,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Brand.Orange.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconFor(theme),
                contentDescription = null,
                tint = Brand.Orange,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                theme.label,
                color = Brand.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                theme.detail,
                color = Brand.ink.copy(alpha = 0.6f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Brand.Orange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        Brand.ink.copy(alpha = 0.18f),
                        CircleShape
                    )
            )
        }
    }
}

private fun iconFor(theme: ThapsusThemePreference): ImageVector = when (theme) {
    ThapsusThemePreference.System -> Icons.Filled.Brightness6
    ThapsusThemePreference.Light -> Icons.Filled.LightMode
    ThapsusThemePreference.Dark -> Icons.Filled.Brightness4
}
