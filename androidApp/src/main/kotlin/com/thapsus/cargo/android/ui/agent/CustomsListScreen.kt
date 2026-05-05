package com.thapsus.cargo.android.ui.agent

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.thapsus.cargo.data.dto.CustomsEntryDto
import com.thapsus.cargo.data.dto.CustomsStatus

@Composable
fun CustomsListScreen(agentId: String) {
    val vm = remember(agentId) { ThapsusSdk.customsAgentViewModel(agentId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }

    val consols by vm.assignedConsolidations.collectAsStateWithLifecycle()
    val selected by vm.selectedId.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EyebrowPill(label = "Customs")
        EditorialHeader(
            title = "Assigned consolidations",
            subtitle = "Pick a consolidation to file customs against."
        )

        if (consols.isEmpty()) {
            SoftCard {
                Text(
                    "Nothing assigned to you yet. Admin will assign consolidations once they're ready for clearing.",
                    color = Brand.ink.copy(alpha = 0.7f)
                )
            }
        } else {
            consols.forEach { c ->
                val isOn = c.id == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isOn) Brand.Orange.copy(alpha = 0.18f) else Brand.cream.copy(alpha = 0.78f),
                            RoundedCornerShape(22.dp)
                        )
                        .clickable { vm.select(c.id) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Week of ${c.weekStart}", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                        c.masterAwbNo?.let {
                            Text("AWB $it", color = Brand.Orange, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                    Text(
                        "${c.totalParcels} parcels",
                        color = Brand.ink.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (selected != null) {
            Text("Customs entries for selected", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
            if (entries.isEmpty()) {
                SoftCard {
                    Text("No entries yet for this consolidation.", color = Brand.ink.copy(alpha = 0.7f))
                }
            } else {
                entries.forEach { e -> EntryRow(e) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EntryRow(e: CustomsEntryDto) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(
                    "Parcel ${e.parcelId.take(8)}",
                    color = Brand.ink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    statusLabel(e.status),
                    color = Brand.Orange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .background(Brand.Orange.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            e.idfNo?.let { Text("IDF $it", color = Brand.ink.copy(alpha = 0.65f), fontSize = 12.sp) }
            e.entryNo?.let { Text("Entry $it", color = Brand.ink.copy(alpha = 0.65f), fontSize = 12.sp) }
            Text(
                "Duty %.2f · VAT %.2f · IDF %.2f KES".format(
                    e.dutyKes ?: 0.0,
                    e.vatKes ?: 0.0,
                    e.idfKes ?: 0.0
                ),
                color = Brand.ink.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

private fun statusLabel(s: CustomsStatus) = when (s) {
    CustomsStatus.PRE_ALERT -> "PRE-ALERT"
    CustomsStatus.IDF_SUBMITTED -> "IDF SUBMITTED"
    CustomsStatus.ENTRY_FILED -> "ENTRY FILED"
    CustomsStatus.DUTY_ASSESSED -> "DUTY ASSESSED"
    CustomsStatus.DUTY_PAID -> "DUTY PAID"
    CustomsStatus.RELEASED -> "RELEASED"
    CustomsStatus.REJECTED -> "REJECTED"
}
