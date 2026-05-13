package com.thapsus.cargo.android.ui.operator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.RunStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val ZONES = listOf("Westlands", "Kilimani", "Karen", "Kasarani", "Eastlands", "Lavington", "CBD")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchScreen() {
    val vm = remember { ThapsusSdk.dispatchViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }

    // DispatchViewModel renamed readyParcels → pendingParcels in the
    // shared module. Same semantics: parcels in 'released' status, ready
    // for a last-mile run.
    val ready by vm.pendingParcels.collectAsStateWithLifecycle()
    val runs by vm.runsList.collectAsStateWithLifecycle()
    var showNewRun by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Last mile",
            title = "Dispatch",
            subtitle = "Released parcels ready for last-mile"
        )

        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Released and waiting", color = Brand.cream.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(
                    "${ready.size}",
                    color = Brand.cream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 56.sp
                )
            }
        }

        OrangeButton(text = "Create new run", onClick = { showNewRun = true })

        if (runs.isNotEmpty()) {
            Text("Today's runs", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
            runs.forEach { run ->
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(run.zone, color = Brand.ink, fontWeight = FontWeight.SemiBold)
                            // LastMileRunDto.riderId is nullable now (a run
                            // can be PLANNED before a rider is assigned).
                            Text(
                                run.riderId?.let { "Rider ${it.take(8)}" }
                                    ?: "No rider assigned",
                                color = Brand.ink.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            friendly(run.status),
                            color = Brand.ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .background(Brand.Orange.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showNewRun) {
        ModalBottomSheet(
            onDismissRequest = { showNewRun = false },
            sheetState = sheetState,
            containerColor = Brand.cream
        ) {
            NewRunSheet(
                onCreate = { riderId, zone ->
                    showNewRun = false
                    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                    vm.createRun(riderId = riderId, zone = zone, runDate = today, parcelIds = emptyList())
                }
            )
        }
    }
}

@Composable
private fun NewRunSheet(onCreate: (riderId: String, zone: String) -> Unit) {
    var zone by remember { mutableStateOf(ZONES.first()) }
    var riderId by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EditorialHeader(eyebrow = "Dispatch", title = "New rider run")
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Zone", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZONES.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { z ->
                                val active = z == zone
                                Text(
                                    z,
                                    color = Brand.ink,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .background(
                                            if (active) Brand.Orange.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.5f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { zone = z }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = riderId,
                    onValueChange = { riderId = it },
                    label = { Text("Rider user ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        InkButton(
            text = "Create run",
            enabled = riderId.isNotBlank(),
            onClick = { onCreate(riderId.trim(), zone) }
        )
        Spacer(Modifier.height(12.dp))
    }
}

// See RiderScaffold.friendly() for the rationale — RunStatus is now
// 4-state (PLANNED / IN_PROGRESS / COMPLETED / CANCELLED) and labels
// mirror iOS DispatchView.
private fun friendly(s: RunStatus) = when (s) {
    RunStatus.PLANNED -> "Planned"
    RunStatus.IN_PROGRESS -> "In progress"
    RunStatus.COMPLETED -> "Completed"
    RunStatus.CANCELLED -> "Cancelled"
}
