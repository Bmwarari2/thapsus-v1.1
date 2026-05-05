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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.ConsolidationDto
import com.thapsus.cargo.data.dto.ConsolidationStatus
import com.thapsus.cargo.presentation.ConsolidationListViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsolidationListScreen(onOpenDetail: (String) -> Unit) {
    val vm = remember { ThapsusSdk.consolidationListViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }

    val list by vm.list.collectAsStateWithLifecycle()
    val createState by vm.createState.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EditorialHeader(
                eyebrow = "Hub operations",
                title = "Consolidations",
                subtitle = "Flight units",
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showCreate = true }) {
                Icon(
                    Icons.Filled.AddCircle,
                    contentDescription = "New consolidation",
                    tint = Brand.Orange,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        (createState as? ConsolidationListViewModel.CreateState.Error)?.let {
            CalloutBanner(title = "Create failed", message = it.message)
        }

        if (list.isEmpty()) {
            SoftCard {
                Text(
                    "No consolidations yet. Tap + to start one whenever you're ready.",
                    color = Brand.ink.copy(alpha = 0.7f)
                )
            }
        } else {
            list.forEach { c ->
                ConsolidationRow(c = c, onClick = { onOpenDetail(c.id) })
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showCreate) {
        ModalBottomSheet(
            onDismissRequest = { showCreate = false },
            sheetState = sheetState,
            containerColor = Brand.cream
        ) {
            CreateConsolidationSheet(
                onCreate = { week, cutoff, departure, notes ->
                    showCreate = false
                    vm.create(week, cutoff, departure, notes)
                }
            )
        }
    }
}

@Composable
private fun ConsolidationRow(c: ConsolidationDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = 0.78f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Week of ${c.weekStart}", color = Brand.ink, fontWeight = FontWeight.SemiBold)
            Text(
                "Cut-off ${c.cutoffAt.take(16).replace('T', ' ')}",
                color = Brand.ink.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(label = friendly(c.status), color = Brand.Gold.copy(alpha = 0.4f))
                c.masterAwbNo?.takeIf { it.isNotBlank() }?.let {
                    Pill(label = "AWB $it", color = Brand.Orange.copy(alpha = 0.18f))
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${c.totalParcels} parcels", color = Brand.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "%.1f kg".format(c.totalKg),
                color = Brand.ink.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Pill(label: String, color: Color) {
    Text(
        label,
        color = Brand.ink,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

private fun friendly(s: ConsolidationStatus) = when (s) {
    ConsolidationStatus.OPEN -> "Open"
    ConsolidationStatus.CUTOFF_LOCKED -> "Locked"
    ConsolidationStatus.MANIFESTED -> "Manifested"
    ConsolidationStatus.HANDED_TO_TUDOR -> "Handed to Tudor"
    ConsolidationStatus.IN_TRANSIT -> "In transit"
    ConsolidationStatus.JKIA_ARRIVED -> "Arrived JKIA"
    ConsolidationStatus.CLEARED -> "Cleared"
    ConsolidationStatus.CLOSED -> "Closed"
}

@Composable
private fun CreateConsolidationSheet(
    onCreate: (week: String, cutoff: String, departure: String?, notes: String?) -> Unit
) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    var weekStart by remember { mutableStateOf(today.toString()) }
    var cutoffAt by remember { mutableStateOf(today.plus(7, DateTimeUnit.DAY).toString() + "T18:00:00Z") }
    var departureAt by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("New consolidation", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = weekStart,
                    onValueChange = { weekStart = it },
                    label = { Text("Week start (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cutoffAt,
                    onValueChange = { cutoffAt = it },
                    label = { Text("Cut-off (ISO 8601)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = departureAt,
                    onValueChange = { departureAt = it },
                    label = { Text("Departure (optional, ISO 8601)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        InkButton(
            text = "Create",
            enabled = weekStart.isNotBlank() && cutoffAt.isNotBlank(),
            onClick = {
                onCreate(
                    weekStart.trim(),
                    cutoffAt.trim(),
                    departureAt.trim().ifBlank { null },
                    notes.trim().ifBlank { null }
                )
            }
        )
        Spacer(Modifier.height(12.dp))
    }
}
