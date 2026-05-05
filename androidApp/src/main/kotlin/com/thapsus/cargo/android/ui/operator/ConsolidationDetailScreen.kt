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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.hardware.ManifestPrinter
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.PackageDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsolidationDetailScreen(
    consolidationId: String,
    operatorName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm = remember(consolidationId) { ThapsusSdk.consolidationDetailViewModel(consolidationId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }

    val parcels by vm.parcels.collectAsStateWithLifecycle()
    val available by vm.availableParcels.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var awb by remember { mutableStateOf("") }
    var tudor by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Consolidation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            error?.let {
                CalloutBanner(title = "Error", message = it)
            }

            InkCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manifest summary", color = Brand.cream, fontWeight = FontWeight.SemiBold)
                    Row {
                        Stat("Parcels", summary.totalParcels.toString(), Modifier.weight(1f))
                        Stat("Chargeable", "%.1f kg".format(summary.totalChargeableKg), Modifier.weight(1f))
                        Stat("Declared", "£%.0f".format(summary.totalDeclaredValueGbpPence / 100.0), Modifier.weight(1f))
                    }
                }
            }

            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Text("Ready to consolidate", color = Brand.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            "${available.size} available",
                            color = Brand.ink.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    OrangeButton(
                        text = if (available.isEmpty()) "No parcels ready" else "Add parcels to manifest",
                        enabled = available.isNotEmpty() && !saving,
                        onClick = { showAdd = true }
                    )
                }
            }

            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Manifest", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Print an A4 manifest for the master AWB packet — parcel list, weights, declared values, totals.",
                        color = Brand.ink.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    InkButton(
                        text = if (parcels.isEmpty()) "No parcels yet" else "Print manifest",
                        enabled = parcels.isNotEmpty(),
                        onClick = {
                            ManifestPrinter.print(
                                context = context,
                                consolidationId = consolidationId,
                                parcels = parcels,
                                totalParcels = summary.totalParcels,
                                totalChargeableKg = summary.totalChargeableKg,
                                totalDeclaredValueGbp = summary.totalDeclaredValueGbpPence / 100.0,
                                masterAwb = awb.ifBlank { null },
                                operatorName = operatorName
                            )
                        }
                    )
                }
            }

            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Master AWB", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = awb,
                        onValueChange = { awb = it },
                        label = { Text("AWB number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    InkButton(
                        text = "Submit AWB & mark in-transit",
                        enabled = awb.isNotBlank() && !saving,
                        onClick = { vm.submitMasterAwb(awb, null) }
                    )
                }
            }

            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tudor handover", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = tudor,
                        onValueChange = { tudor = it },
                        label = { Text("Tudor invoice number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OrangeButton(
                        text = "Save Tudor invoice",
                        enabled = tudor.isNotBlank() && !saving,
                        onClick = { vm.submitTudorInvoice(tudor) }
                    )
                }
            }

            if (parcels.isNotEmpty()) {
                Text("Parcels in this manifest", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
                parcels.forEach { p ->
                    SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.description ?: "Parcel", color = Brand.ink, modifier = Modifier.weight(1f))
                            p.chargeableKg?.let {
                                Text(
                                    "%.1f kg".format(it),
                                    color = Brand.ink.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = sheetState,
            containerColor = Brand.cream
        ) {
            AddParcelsSheet(
                available = available,
                onSubmit = { ids ->
                    showAdd = false
                    vm.assignParcels(ids)
                }
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Brand.cream.copy(alpha = 0.7f), fontSize = 11.sp)
        Text(value, color = Brand.cream, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun AddParcelsSheet(
    available: List<PackageDto>,
    onSubmit: (List<String>) -> Unit
) {
    val selected = remember { mutableStateOf<Set<String>>(emptySet()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Add parcels", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        available.forEach { p ->
            val isOn = p.id in selected.value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isOn) Brand.Orange.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.6f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        selected.value = selected.value.toMutableSet().also {
                            if (isOn) it.remove(p.id) else it.add(p.id)
                        }
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(if (isOn) Brand.Orange else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isOn) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.description ?: "Parcel", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    p.chargeableKg?.let {
                        Text(
                            "%.2f kg".format(it),
                            color = Brand.ink.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        InkButton(
            text = "Add (${selected.value.size})",
            enabled = selected.value.isNotEmpty(),
            onClick = { onSubmit(selected.value.toList()) }
        )
        Spacer(Modifier.height(20.dp))
    }
}
