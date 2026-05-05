package com.thapsus.cargo.android.ui.operator

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.BigStatTile
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.PackageDto

@Composable
fun OperatorTodayScreen() {
    val vm = remember { ThapsusSdk.operatorTodayViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Hub operations",
            title = "Today",
            subtitle = "Live across the warehouse pipeline"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStatTile(
                eyebrow = "Expected",
                value = state.expectedToday.size.toString(),
                icon = Icons.Filled.Inventory2,
                modifier = Modifier.weight(1f)
            )
            BigStatTile(
                eyebrow = "Ready to consol.",
                value = state.readyToConsolidate.size.toString(),
                icon = Icons.Filled.Inbox,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStatTile(
                eyebrow = "In transit",
                value = state.inTransit.size.toString(),
                icon = Icons.Filled.AirplanemodeActive,
                modifier = Modifier.weight(1f),
                accent = Color(0xFF2196F3)
            )
            BigStatTile(
                eyebrow = "Held",
                value = state.held.size.toString(),
                icon = Icons.Filled.Warning,
                modifier = Modifier.weight(1f),
                accent = Color(0xFFD32F2F)
            )
        }

        ParcelSection("Ready to consolidate", state.readyToConsolidate)
        ParcelSection("In transit", state.inTransit)
        ParcelSection("Held — needs action", state.held)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ParcelSection(title: String, parcels: List<PackageDto>) {
    if (parcels.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        parcels.forEach { p ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            p.description ?: p.retailer ?: "Parcel",
                            color = Brand.ink,
                            fontWeight = FontWeight.SemiBold
                        )
                        p.barcode?.let {
                            Text(
                                it,
                                color = Brand.Orange,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                    p.chargeableKg?.let {
                        Text(
                            "%.1f kg".format(it),
                            color = Brand.ink.copy(alpha = 0.65f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
