package com.thapsus.cargo.android.ui.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.LastMileRunDto
import com.thapsus.cargo.data.dto.RunStatus

/**
 * Rider "Today" tab — list of every last-mile run currently assigned
 * to this rider. Mirrors iOS RiderRunView. Each row navigates to
 * RunStopListScreen for the parcel drilldown.
 *
 * Backed by RiderRunViewModel.runs — Realtime + cache via
 * LastMileRepository.observeRuns(riderId).
 */
@Composable
fun RiderRunsScreen(riderId: String, onOpenRun: (LastMileRunDto) -> Unit) {
    val vm = remember(riderId) { ThapsusSdk.riderRunViewModel(riderId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.refresh() }
    val runs by vm.runs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EyebrowPill(label = "Last mile")
        EditorialHeader(
            title = "Today's runs",
            subtitle = "Tap a run to see stops."
        )
        if (runs.isEmpty()) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nothing scheduled", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        "New runs appear here as soon as ops dispatches them.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            runs.forEach { run ->
                RunRow(run = run, onClick = { onOpenRun(run) })
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RunRow(run: LastMileRunDto, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Brand.Orange.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, tint = Brand.Orange)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    run.zone,
                    color = Brand.ink,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Run ${run.id.take(8)}",
                    color = Brand.ink.copy(alpha = 0.55f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                StatusChip(run.status)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Brand.ink.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun StatusChip(status: RunStatus) {
    val color = when (status) {
        RunStatus.IN_PROGRESS -> Color(0xFFE08B00)
        RunStatus.COMPLETED -> Color(0xFF2E7D32)
        RunStatus.CANCELLED -> Color(0xFFB3261E)
        RunStatus.PLANNED -> Color(0xFF707070)
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            friendly(status).uppercase(),
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )
    }
}

internal fun friendly(status: RunStatus): String = when (status) {
    RunStatus.PLANNED -> "Planned"
    RunStatus.IN_PROGRESS -> "In progress"
    RunStatus.COMPLETED -> "Completed"
    RunStatus.CANCELLED -> "Cancelled"
}
