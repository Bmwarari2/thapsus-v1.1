package com.thapsus.cargo.android.ui.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.DispatchParcelRow
import com.thapsus.cargo.data.dto.OutboxFailureDto

/**
 * Drilldown from RiderRunsScreen. Lists the parcels on a single run,
 * grouped by recipient (matches iOS RunStopListView's "Phase 3"
 * grouping). Tap a stop card to open POD capture (P4.2 wires that).
 *
 * Also surfaces inline retry banners for failed POD sync attempts
 * sourced from OutboxFailures keyed off parcel ids in the group.
 */
@Composable
fun RunStopListScreen(
    runId: String,
    zone: String,
    onClose: () -> Unit,
    onOpenPod: (parcelIds: List<String>, recipientName: String) -> Unit
) {
    val vm = remember(runId) { ThapsusSdk.runStopsViewModel(runId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.refresh() }

    val parcels by vm.parcels.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val failures by vm.outboxFailures.collectAsStateWithLifecycle()

    val groups = remember(parcels) { groupByUser(parcels) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand.ink)
            }
        }
        EditorialHeader(
            eyebrow = "Run · $zone",
            title = "Stops",
            subtitle = "Tap a recipient to deliver."
        )

        if (loading && parcels.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        }

        error?.takeIf { it.isNotBlank() }?.let { msg ->
            CalloutBanner(title = "Couldn't load", message = msg)
        }

        if (!loading && groups.isEmpty() && error == null) {
            SoftCard {
                Text("No stops yet on this run.", color = Brand.ink.copy(alpha = 0.7f))
            }
        }

        groups.forEachIndexed { idx, group ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StopCard(
                    sequence = idx + 1,
                    group = group,
                    onClick = { onOpenPod(group.parcelIds, group.recipient ?: "Recipient") }
                )
                val groupFailures = failuresFor(group, failures)
                groupFailures.forEach { fail ->
                    PodSyncFailureBanner(
                        failure = fail,
                        onRetry = { vm.retryFailure(fail.mutationId) },
                        onDismiss = { vm.dismissFailure(fail.mutationId) }
                    )
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** One stop = one recipient (user_id). Parcels without a user_id go in their own singleton group. */
internal data class StopGroup(
    val id: String,
    val parcels: List<DispatchParcelRow>
) {
    val primary: DispatchParcelRow get() = parcels.first()
    val recipient: String? get() = primary.name
    val address: String? get() = primary.effectiveAddress
    val phone: String? get() = primary.phone
    val parcelIds: List<String> get() = parcels.map { it.id }
    val allDelivered: Boolean get() = parcels.all { it.hasPod }
}

private fun groupByUser(parcels: List<DispatchParcelRow>): List<StopGroup> {
    val groups = mutableListOf<StopGroup>()
    val indexByUser = mutableMapOf<String, Int>()
    for (p in parcels) {
        val key = p.userId ?: "anon-${p.id}"
        val idx = indexByUser[key]
        if (idx != null) {
            groups[idx] = groups[idx].copy(parcels = groups[idx].parcels + p)
        } else {
            indexByUser[key] = groups.size
            groups.add(StopGroup(id = key, parcels = listOf(p)))
        }
    }
    return groups
}

private fun failuresFor(group: StopGroup, all: List<OutboxFailureDto>): List<OutboxFailureDto> {
    val ids = group.parcelIds.toSet()
    return all.filter { (it.targetId ?: "") in ids }
}

@Composable
private fun StopCard(sequence: Int, group: StopGroup, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Brand.Orange.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    sequence.toString(),
                    color = Brand.ink,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.recipient ?: "Recipient",
                        color = Brand.ink,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (group.parcels.size > 1) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Brand.Orange.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${group.parcels.size} parcels",
                                color = Brand.Orange,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                group.address?.let { addr ->
                    Text(addr, color = Brand.ink.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                group.phone?.let { phone ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Phone, contentDescription = null, tint = Brand.ink.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(phone, color = Brand.ink.copy(alpha = 0.55f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                group.parcels.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Brand.ink.copy(alpha = 0.45f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            p.description ?: "Parcel",
                            color = Brand.ink.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
                if (group.allDelivered) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delivered", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Brand.ink.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun PodSyncFailureBanner(
    failure: OutboxFailureDto,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFB3261E).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB3261E).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color(0xFFB3261E))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "POD didn't sync — tap retry",
                color = Color(0xFFB3261E),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            failure.errorMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                Text(msg, color = Brand.ink.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 2)
            }
        }
        TextButton(onClick = onRetry) {
            Text("Retry", color = Color(0xFFB3261E), fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onDismiss) {
            Text("✕", color = Brand.ink.copy(alpha = 0.55f))
        }
    }
}
