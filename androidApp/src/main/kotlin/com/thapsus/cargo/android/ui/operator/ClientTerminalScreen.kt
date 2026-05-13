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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.hardware.LabelPrinter
import com.thapsus.cargo.android.hardware.ManifestPrinter
import com.thapsus.cargo.android.hardware.WarehouseLabel
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.ConsolidationDto
import com.thapsus.cargo.data.dto.OpsScannedParcelDto
import com.thapsus.cargo.data.repository.AuthSession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Operator parcel-print + manifest hub. Two modes:
 *
 * 1. Parcel label: enter or scan a barcode → fetch the package via
 *    PackageRepository.fetchByBarcode → render the WarehouseLabel and
 *    hand to PrintHelper.
 * 2. Open consolidations: list the operator's active consolidations
 *    sourced from ConsolidationRepository.observeAll. Tap one →
 *    ManifestPrinter renders an HTML manifest and hands to the system
 *    PrintManager.
 *
 * Mirrors the operator workflow described in the parity plan; iOS
 * doesn't have a single equivalent screen — parcel-print fires from
 * the intake flow there, and manifests print from the consolidation
 * detail view. Android consolidates both behind one hub.
 */
@Composable
fun ClientTerminalScreen(session: AuthSession.Authenticated) {
    val context = LocalContext.current
    val packagesRepo = remember { ThapsusSdk.packages() }
    val consolidationsRepo = remember { ThapsusSdk.consolidations() }
    val scope = rememberCoroutineScope()

    var barcodeQuery by remember { mutableStateOf("") }
    var lookupBusy by remember { mutableStateOf(false) }
    var lookupResult by remember { mutableStateOf<OpsScannedParcelDto?>(null) }
    var lookupError by remember { mutableStateOf<String?>(null) }

    var consolidations by remember { mutableStateOf<List<ConsolidationDto>>(emptyList()) }

    LaunchedEffect(consolidationsRepo) {
        runCatching { consolidationsRepo.refreshAll() }
        consolidationsRepo.observeAll().collectLatest { consolidations = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EyebrowPill(label = "Print", icon = Icons.Filled.Print)
        EditorialHeader(
            title = "Client terminal",
            subtitle = "Print parcel labels and consolidation manifests."
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Parcel label", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = barcodeQuery,
                    onValueChange = { barcodeQuery = it.uppercase() },
                    label = { Text("Barcode or tracking number") },
                    placeholder = { Text("e.g. THP-3F8C2A") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                InkButton(
                    text = if (lookupBusy) "Looking up…" else "Find parcel",
                    enabled = barcodeQuery.trim().isNotEmpty() && !lookupBusy,
                    onClick = {
                        lookupResult = null
                        lookupError = null
                        lookupBusy = true
                        scope.launch {
                            packagesRepo.lookupByScannedBarcode(barcodeQuery.trim())
                                .onSuccess { pkg ->
                                    if (pkg == null) lookupError = "No parcel found with that barcode."
                                    else lookupResult = pkg
                                }
                                .onFailure { lookupError = it.message ?: "Lookup failed." }
                            lookupBusy = false
                        }
                    }
                )
                lookupError?.let { CalloutBanner(title = "Couldn't find", message = it) }
                lookupResult?.let { pkg ->
                    ParcelLabelPreview(
                        parcel = pkg,
                        onPrint = {
                            val label = WarehouseLabel(
                                sku = pkg.barcode ?: pkg.packageId.take(8),
                                customerName = pkg.customer?.fullName ?: "Customer",
                                customerWarehouseCode = pkg.customer?.warehouseId,
                                warehouseCode = pkg.trackingNumber ?: pkg.packageId.take(8),
                                retailer = pkg.retailer,
                                description = pkg.description,
                                welcomeMessage = "Thank you for shipping with Thapsus Cargo."
                            )
                            LabelPrinter.print(context, label)
                        }
                    )
                }
            }
        }

        Text("Open manifests", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        if (consolidations.isEmpty()) {
            SoftCard {
                Text(
                    "No active consolidations. Build one from the Consols tab first.",
                    color = Brand.ink.copy(alpha = 0.7f)
                )
            }
        } else {
            val openOnly = consolidations.filter { it.status.name.uppercase() != "ARRIVED" }
            (openOnly.ifEmpty { consolidations }).take(12).forEach { c ->
                ConsolidationRow(
                    consolidation = c,
                    onPrint = {
                        scope.launch {
                            val parcels = packagesRepo.refreshAll().getOrNull().orEmpty()
                                .filter { it.consolidationId == c.id }
                            val totalKg = parcels.sumOf { it.chargeableKg ?: 0.0 }
                            val totalDeclaredGbp =
                                parcels.sumOf { (it.declaredValueGbpPence / 100.0) }
                            ManifestPrinter.print(
                                context = context,
                                consolidationId = c.id,
                                parcels = parcels,
                                totalParcels = parcels.size,
                                totalChargeableKg = totalKg,
                                totalDeclaredValueGbp = totalDeclaredGbp,
                                masterAwb = c.masterAwbNo,
                                operatorName = session.profile?.fullName ?: session.email ?: "Operator"
                            )
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ParcelLabelPreview(
    parcel: OpsScannedParcelDto,
    onPrint: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            (parcel.barcode ?: parcel.packageId.take(8)).uppercase(),
            color = Brand.Orange,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp
        )
        Text(
            parcel.customer?.fullName ?: "Customer",
            color = Brand.ink,
            fontWeight = FontWeight.SemiBold
        )
        parcel.customer?.warehouseId?.let {
            Text(it, color = Brand.ink.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Text(
            parcel.description ?: parcel.retailer ?: "Parcel",
            color = Brand.ink.copy(alpha = 0.75f),
            fontSize = 13.sp
        )
        OrangeButton(text = "Print label", onClick = onPrint)
    }
}

@Composable
private fun ConsolidationRow(consolidation: ConsolidationDto, onPrint: () -> Unit) {
    SoftCard(modifier = Modifier.clickable(onClick = onPrint)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Brand.ink, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Brand.cream)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Week ${consolidation.weekStart.take(10)}",
                    color = Brand.ink,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    consolidation.status.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() } +
                        " · ${consolidation.totalParcels} parcel${if (consolidation.totalParcels == 1) "" else "s"}",
                    color = Brand.ink.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                consolidation.masterAwbNo?.takeIf { it.isNotBlank() }?.let { awb ->
                    Text(
                        "AWB $awb",
                        color = Brand.Orange,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
            Icon(
                Icons.Filled.Print,
                contentDescription = "Print manifest",
                tint = Brand.Orange,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
