package com.thapsus.cargo.android.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.BigStatTile
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand

@Composable
fun KPIDashboardScreen() {
    val vm = remember { ThapsusSdk.kpiDashboardViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.refresh() }

    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val server by vm.serverStats.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EyebrowPill(label = "Founder dashboard")
        EditorialHeader(title = "KPI", subtitle = "Cache snapshot + server-derived totals.")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStatTile(
                eyebrow = "Total parcels",
                value = snap.totalParcels.toString(),
                icon = Icons.Filled.Inventory2,
                modifier = Modifier.weight(1f)
            )
            BigStatTile(
                eyebrow = "Chargeable kg",
                value = "%.1f".format(snap.chargeableKgThisWeek),
                icon = Icons.Filled.Scale,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStatTile(
                eyebrow = "Delivered",
                value = snap.deliveredCount.toString(),
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.weight(1f),
                accent = Color(0xFF2E7D32)
            )
            BigStatTile(
                eyebrow = "In transit",
                value = snap.inTransitCount.toString(),
                icon = Icons.Filled.AirplanemodeActive,
                modifier = Modifier.weight(1f),
                accent = Color(0xFF2196F3)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStatTile(
                eyebrow = "Held",
                value = snap.heldCount.toString(),
                icon = Icons.Filled.Warning,
                modifier = Modifier.weight(1f),
                accent = Color(0xFFD32F2F)
            )
            BigStatTile(
                eyebrow = "On-time %",
                value = "%.0f%%".format(snap.onTimePercent),
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.weight(1f)
            )
        }

        server?.let { s ->
            val users = s.stats.users
            val orders = s.stats.orders
            val revenue = s.stats.revenue
            Text("Server stats", color = Brand.ink, fontWeight = FontWeight.SemiBold)
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("Total users", "${users.total}")
                    Stat("Active users", "${users.activeUsers}")
                    Stat("New today", "${users.newToday}")
                    Stat("Total orders", "${orders.totalOrders}")
                    Stat("In transit", "${orders.inTransit}")
                    Stat("Total revenue (KES)", "%.2f".format(revenue.totalRevenue))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = Brand.ink.copy(alpha = 0.65f), modifier = Modifier.weight(1f))
        Text(value, color = Brand.ink, fontWeight = FontWeight.SemiBold)
    }
}
