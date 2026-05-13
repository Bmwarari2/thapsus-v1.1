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
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
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

@OptIn(ExperimentalMaterial3Api::class)
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

    // Stage-grouped active shipments. Mirrors iOS TrackingView's
    // `recentActivityStages`: bucket every parcel by `LifecycleStage`
    // (5 happy-path stages + Held), drop empty buckets, sort with
    // most-actionable first (Held > Out-for-delivery > JKIA > In flight
    // > At UK hub). Singleton groups jump straight to parcel detail;
    // multi-parcel groups open a list sheet so the customer picks one.
    val stageGroups = remember(parcels) { groupByStage(parcels) }
    var expandedGroup by remember { mutableStateOf<StageGroup?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            title = if (stageGroups.isEmpty()) "No active shipments" else "Active shipments",
            subtitle = if (stageGroups.isEmpty())
                null
            else
                "Parcels grouped by where they are in the journey. Tap to see details."
        )

        if (stageGroups.isEmpty()) {
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
            stageGroups.forEach { g ->
                StageGroupCard(
                    group = g,
                    onClick = {
                        if (g.members.size == 1) onOpenParcel(g.members.first().id)
                        else expandedGroup = g
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    val target = expandedGroup
    if (target != null) {
        ModalBottomSheet(
            onDismissRequest = { expandedGroup = null },
            sheetState = sheetState
        ) {
            StageGroupSheet(
                group = target,
                onPick = { id ->
                    expandedGroup = null
                    onOpenParcel(id)
                }
            )
        }
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

private fun iconFor(s: PackageStatus) = when (s) {
    PackageStatus.IN_TRANSIT,
    PackageStatus.MANIFESTED,
    PackageStatus.JKIA_ARRIVED -> Icons.Filled.AirplanemodeActive
    else -> Icons.Filled.Inventory2
}

/**
 * Coarse pipeline buckets used to group customer parcels in the active
 * feed. Mirrors iOS LifecycleStage — we collapse the 12 raw
 * PackageStatus values down to 5 happy-path stages + Held, since a
 * customer reads the feed top-to-bottom asking "where is my stuff" and
 * the difference between e.g. `weighed` and `screened` is not
 * meaningful at that altitude. Delivered/Abandoned drop out.
 */
private enum class LifecycleStage(
    val rank: Int,
    val progress: Float,
    val title: String,
    val icon: ImageVector,
    val accent: Color
) {
    AT_UK_HUB(1, 0.20f, "At Stockport hub", Icons.Filled.Inventory2, Color(0xFF1A1A1A)),
    ON_A_FLIGHT(2, 0.40f, "On a flight to Nairobi", Icons.Filled.AirplanemodeActive, Color(0xFF1A1A1A)),
    AT_JKIA(3, 0.60f, "Customs at JKIA", Icons.Filled.Shield, Color(0xFF1A1A1A)),
    OUT_FOR_DELIVERY(4, 0.80f, "Out for delivery", Icons.Filled.TwoWheeler, Color(0xFFD9501C)),
    DELIVERED(5, 1.00f, "Delivered", Icons.Filled.CheckCircle, Color(0xFF2E7D32)),
    HELD(100, 0f, "Held — action needed", Icons.Filled.Warning, Color(0xFFB3261E));

    companion object {
        fun from(status: PackageStatus): LifecycleStage? = when (status) {
            PackageStatus.PRE_REGISTERED,
            PackageStatus.RECEIVED_AT_WAREHOUSE,
            PackageStatus.PHOTOGRAPHED,
            PackageStatus.WEIGHED,
            PackageStatus.SCREENED -> AT_UK_HUB
            PackageStatus.MANIFESTED, PackageStatus.IN_TRANSIT -> ON_A_FLIGHT
            PackageStatus.JKIA_ARRIVED,
            PackageStatus.AWAITING_DUTY_PAYMENT,
            PackageStatus.RELEASED -> AT_JKIA
            PackageStatus.OUT_FOR_DELIVERY -> OUT_FOR_DELIVERY
            PackageStatus.DELIVERED -> DELIVERED
            PackageStatus.HELD, PackageStatus.HELD_AT_NAIROBI_HUB -> HELD
            PackageStatus.ABANDONED -> null
        }
    }
}

private data class StageGroup(val stage: LifecycleStage, val members: List<PackageDto>)

/**
 * Bucket parcels by lifecycle stage. Delivered parcels survive only if
 * they were updated in the last 14 days — older deliveries belong in a
 * separate past-deliveries view. Empty buckets drop out. Sort: Held
 * pinned to the top, then closest-to-delivered first.
 */
private fun groupByStage(parcels: List<PackageDto>): List<StageGroup> {
    if (parcels.isEmpty()) return emptyList()
    val cutoffMillis = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
    val buckets = mutableMapOf<LifecycleStage, MutableList<PackageDto>>()
    for (p in parcels) {
        val stage = LifecycleStage.from(p.status) ?: continue
        if (stage == LifecycleStage.DELIVERED) {
            val stamp = parseIsoMillis(p.updatedAt ?: p.createdAt) ?: continue
            if (stamp < cutoffMillis) continue
        }
        buckets.getOrPut(stage) { mutableListOf() }.add(p)
    }
    return buckets
        .map { (stage, members) -> StageGroup(stage, members) }
        .sortedByDescending { it.stage.rank }
}

/**
 * Best-effort parse of the ISO timestamps `packages.updated_at` /
 * `created_at` can ship in (with or without fractional seconds, with or
 * without a `Z`). Returns null if nothing parses — those rows simply
 * don't qualify for the 14-day Delivered window.
 */
private fun parseIsoMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return runCatching { java.time.Instant.parse(raw).toEpochMilli() }
        .getOrElse {
            runCatching {
                java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            }.getOrElse {
                runCatching {
                    java.time.LocalDate.parse(raw.take(10))
                        .atStartOfDay(java.time.ZoneOffset.UTC)
                        .toInstant().toEpochMilli()
                }.getOrNull()
            }
        }
}

@Composable
private fun StageGroupCard(group: StageGroup, onClick: () -> Unit) {
    val stage = group.stage
    val isHeld = stage == LifecycleStage.HELD
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = 0.85f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(stage.accent, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(stage.icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stage.title,
                    color = Brand.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                CountChip(count = group.members.size, accent = stage.accent)
            }
            Text(
                group.members.take(2).mapNotNull { p ->
                    p.description?.takeIf { it.isNotBlank() }
                        ?: p.retailer?.takeIf { it.isNotBlank() }
                        ?: p.trackingNumber
                }.joinToString(" • ").ifBlank { "—" } +
                    if (group.members.size > 2) " • +${group.members.size - 2} more" else "",
                color = Brand.ink.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            if (!isHeld) {
                LinearProgressIndicator(
                    progress = { stage.progress },
                    color = stage.accent,
                    trackColor = Brand.ink.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
            }
        }
    }
}

@Composable
private fun CountChip(count: Int, accent: Color) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            if (count == 1) "1 parcel" else "$count parcels",
            color = accent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun StageGroupSheet(group: StageGroup, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorialHeader(
            eyebrow = group.stage.title,
            title = "${group.members.size} parcels",
            subtitle = "Pick one to see the full timeline."
        )
        group.members.forEach { p ->
            ActiveShipmentRow(parcel = p, onClick = { onPick(p.id) })
        }
        Spacer(Modifier.height(20.dp))
    }
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
