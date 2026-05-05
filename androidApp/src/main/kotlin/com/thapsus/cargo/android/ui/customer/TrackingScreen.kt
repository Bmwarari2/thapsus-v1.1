package com.thapsus.cargo.android.ui.customer

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.dto.PackageStatus
import com.thapsus.cargo.presentation.PublicTrackingViewModel

@Composable
fun TrackingScreen(
    userId: String,
    onOpenParcel: (String) -> Unit
) {
    val publicVm = remember { ThapsusSdk.publicTrackingViewModel() }
    DisposableEffect(publicVm) { onDispose { publicVm.clear() } }

    val packagesRepo = remember { ThapsusSdk.packages() }
    var parcels by remember { mutableStateOf<List<PackageDto>>(emptyList()) }
    LaunchedEffect(userId) {
        runCatching { packagesRepo.refreshForUser(userId) }
        packagesRepo.observeForUser(userId).collect { parcels = it }
    }

    val publicState by publicVm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val matched = remember(parcels, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) null
        else parcels.firstOrNull {
            it.id.lowercase().contains(q) ||
                it.trackingNumber?.lowercase()?.contains(q) == true ||
                it.barcode?.lowercase()?.contains(q) == true
        }
    }

    val active = remember(parcels) { parcels.filter { isActive(it.status) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Where's my parcel?",
            title = "Track your\npackage",
            subtitle = "Enter a tracking number to see live status."
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.uppercase() },
                    label = { Text("Tracking number") },
                    placeholder = { Text("e.g. THP-3F8C2A") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                InkButton(
                    text = "Track",
                    enabled = query.isNotBlank(),
                    onClick = {
                        if (matched == null) publicVm.search(query.trim())
                    }
                )
            }
        }

        when (val s = publicState) {
            is PublicTrackingViewModel.State.Loading -> SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Brand.ink)
                    Spacer(Modifier.width(12.dp))
                    Text("Searching…", color = Brand.ink)
                }
            }
            is PublicTrackingViewModel.State.Error -> CalloutBanner(
                title = "Couldn't find that one",
                message = s.message
            )
            is PublicTrackingViewModel.State.Found -> PublicResultCard(state = s)
            else -> Unit
        }

        if (matched != null) {
            SectionHeader("Match")
            ActiveShipmentRow(parcel = matched, onClick = { onOpenParcel(matched.id) })
        }

        SectionHeader(
            title = if (active.isEmpty()) "No active shipments" else "Active shipments",
            subtitle = if (active.isEmpty()) null else "Tap a parcel for the full timeline."
        )

        if (active.isEmpty()) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nothing in flight", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Once a parcel is received at Stockport it'll appear here.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            active.forEach { p ->
                ActiveShipmentRow(parcel = p, onClick = { onOpenParcel(p.id) })
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActiveShipmentRow(parcel: PackageDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = 0.78f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Brand.ink, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconFor(parcel.status), contentDescription = null, tint = Brand.cream)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                parcel.description ?: parcel.retailer ?: "Parcel",
                color = Brand.ink,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                statusLabel(parcel.status),
                color = Brand.ink.copy(alpha = 0.65f),
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                parcel.trackingNumber?.let {
                    Text(
                        it,
                        color = Brand.Orange,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
                parcel.chargeableKg?.let {
                    Text(
                        "%.1f kg".format(it),
                        color = Brand.ink.copy(alpha = 0.55f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicResultCard(state: PublicTrackingViewModel.State.Found) {
    val tracking = state.tracking
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    tracking.trackingNumber,
                    color = Brand.Orange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                tracking.retailer?.let { Text(it, color = Brand.ink) }
                tracking.description?.let { Text(it, color = Brand.ink.copy(alpha = 0.7f), fontSize = 12.sp) }
            }
        }
        TrackingTimeline(currentStatus = tracking.status ?: "")
    }
}

private val publicSteps = listOf(
    "pending" to "Pending",
    "received_at_warehouse" to "Received",
    "consolidating" to "Consolidating",
    "in_transit" to "In transit",
    "customs" to "Customs",
    "out_for_delivery" to "Out for delivery",
    "delivered" to "Delivered"
)

@Composable
private fun TrackingTimeline(currentStatus: String) {
    val currentIdx = publicSteps.indexOfFirst { it.first == currentStatus }.coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        publicSteps.forEachIndexed { idx, (_, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (idx <= currentIdx) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                    contentDescription = null,
                    tint = if (idx <= currentIdx) Color(0xFF2E7D32) else Brand.ink.copy(alpha = 0.4f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    color = if (idx == currentIdx) Brand.ink else Brand.ink.copy(alpha = 0.6f),
                    fontWeight = if (idx == currentIdx) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(title, color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, color = Brand.ink.copy(alpha = 0.7f), fontSize = 13.sp) }
    }
}

private fun isActive(s: PackageStatus): Boolean = when (s) {
    PackageStatus.DELIVERED, PackageStatus.ABANDONED -> false
    else -> true
}

private fun iconFor(s: PackageStatus) = when (s) {
    PackageStatus.IN_TRANSIT,
    PackageStatus.MANIFESTED,
    PackageStatus.JKIA_ARRIVED -> Icons.Filled.AirplanemodeActive
    else -> Icons.Filled.Inventory2
}

internal fun statusLabel(s: PackageStatus): String = when (s) {
    PackageStatus.PRE_REGISTERED -> "Pre-registered"
    PackageStatus.RECEIVED_AT_WAREHOUSE -> "At Stockport hub"
    PackageStatus.PHOTOGRAPHED -> "Photographed"
    PackageStatus.WEIGHED -> "Weighed"
    PackageStatus.SCREENED -> "Screened"
    PackageStatus.MANIFESTED -> "On manifest"
    PackageStatus.IN_TRANSIT -> "In transit ✈︎"
    PackageStatus.JKIA_ARRIVED -> "Arrived JKIA"
    PackageStatus.AWAITING_DUTY_PAYMENT -> "Awaiting duty"
    PackageStatus.RELEASED -> "Customs cleared"
    PackageStatus.OUT_FOR_DELIVERY -> "Out for delivery"
    PackageStatus.DELIVERED -> "Delivered"
    PackageStatus.HELD -> "Held — action needed"
    PackageStatus.HELD_AT_NAIROBI_HUB -> "At Nairobi hub"
    PackageStatus.ABANDONED -> "Abandoned"
}
