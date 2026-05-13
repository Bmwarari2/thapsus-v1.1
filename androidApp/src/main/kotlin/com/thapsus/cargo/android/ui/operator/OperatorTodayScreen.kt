package com.thapsus.cargo.android.ui.operator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HourglassEmpty
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
fun OperatorTodayScreen(onOpenBfmQueue: () -> Unit = {}) {
    val vm = remember { ThapsusSdk.operatorTodayViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsStateWithLifecycle()

    // BFM queue VM — reused from OpsBuyForMeQueueScreen so the two tiles
    // share Realtime subscription state with the dedicated queue surface.
    val bfmVm = remember { ThapsusSdk.opsBuyForMeViewModel() }
    DisposableEffect(bfmVm) { onDispose { bfmVm.clear() } }
    val bfmOrders by bfmVm.orders.collectAsStateWithLifecycle()
    val unquoted = bfmOrders.count { it.status == "pending_quote" }
    val awaitingPayment = bfmOrders.count { it.status == "quoted" }

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
            subtitle = "Concierge requests first; parcel pipeline below."
        )

        // BFM queue leads after the BFM-primary pivot. Two live-count
        // tiles deep-link straight into the queue.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BfmStatTile(
                label = "Unquoted BFM",
                value = unquoted,
                icon = Icons.Filled.AutoAwesome,
                onClick = onOpenBfmQueue,
                modifier = Modifier.weight(1f)
            )
            BfmStatTile(
                label = "Awaiting payment",
                value = awaitingPayment,
                icon = Icons.Filled.HourglassEmpty,
                onClick = onOpenBfmQueue,
                modifier = Modifier.weight(1f)
            )
        }

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
private fun BfmStatTile(
    label: String,
    value: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brand.Orange.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Brand.Orange, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label.uppercase(),
                color = Brand.ink.copy(alpha = 0.6f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp
            )
            Text(
                value.toString(),
                color = Brand.ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Brand.ink.copy(alpha = 0.5f)
        )
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
