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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.hardware.LabelPrinter
import com.thapsus.cargo.android.hardware.LabelWelcome
import com.thapsus.cargo.android.hardware.WarehouseLabel
import com.thapsus.cargo.android.hardware.WarehouseSku
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.OrderDimensionsDto
import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.dto.ScreeningResult
import com.thapsus.cargo.domain.model.ParcelDimensions
import com.thapsus.cargo.presentation.IntakeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorReceiveScreen(
    operatorId: String,
    onOpenScanner: () -> Unit
) {
    val todayVm = remember { ThapsusSdk.operatorTodayViewModel() }
    val intakeVm = remember(operatorId) { ThapsusSdk.intakeViewModel(operatorId) }
    DisposableEffect(todayVm, intakeVm) {
        onDispose { todayVm.clear(); intakeVm.reset() }
    }
    val state by todayVm.state.collectAsStateWithLifecycle()

    var sheetParcel by remember { mutableStateOf<PackageDto?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Hub operations",
            title = "Receive",
            subtitle = "Pick a parcel, print its SKU label, mark received."
        )
        CalloutBanner(
            title = "How this works",
            message = "Match the parcel to a row by description or sender. Tap it, print the SKU label, stick it on the box. The order will move to Received."
        )
        OrangeButton(
            text = "Scan SKU",
            onClick = onOpenScanner
        )

        Text(
            "Expected (${state.expectedToday.size})",
            color = Brand.ink,
            style = MaterialTheme.typography.titleLarge
        )

        if (state.expectedToday.isEmpty()) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nothing waiting.", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Pre-registered parcels will appear here once customers create orders.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            state.expectedToday.forEach { p ->
                ParcelRow(p, onClick = { sheetParcel = p })
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    val parcel = sheetParcel
    if (parcel != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetParcel = null },
            sheetState = sheetState,
            containerColor = Brand.cream
        ) {
            ReceiveSheetBody(
                parcel = parcel,
                vm = intakeVm,
                onDone = { sheetParcel = null; todayVm.refresh() }
            )
        }
    }
}

@Composable
private fun ParcelRow(p: PackageDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = 0.78f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(p.description ?: p.retailer ?: "Parcel", color = Brand.ink, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.retailer?.let { Text(it, color = Brand.ink.copy(alpha = 0.6f), fontSize = 12.sp) }
                p.barcode?.let {
                    Text(it, color = Brand.Orange, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                } ?: Text(
                    "NO SKU YET",
                    color = Brand.Orange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Brand.Orange.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Brand.Orange)
    }
}

@Composable
private fun ReceiveSheetBody(
    parcel: PackageDto,
    vm: com.thapsus.cargo.presentation.IntakeViewModel,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    var sku by remember(parcel.id) { mutableStateOf(WarehouseSku.mint()) }
    var lengthCm by remember { mutableStateOf("30") }
    var widthCm by remember { mutableStateOf("20") }
    var heightCm by remember { mutableStateOf("20") }
    var actualKg by remember { mutableStateOf("1.0") }
    var printed by remember { mutableStateOf(false) }
    val welcome = remember(parcel.id) { LabelWelcome.random() }

    var customerName by remember(parcel.id) { mutableStateOf("Customer") }
    var customerWarehouseCode by remember(parcel.id) { mutableStateOf<String?>(null) }
    var customerDeliveryAddress by remember(parcel.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(parcel.id) {
        runCatching {
            ThapsusSdk.packages().fetchCustomer(parcel.orderId ?: parcel.id)
        }.getOrNull()?.let { res ->
            customerName = res.fullName?.takeIf { it.isNotBlank() } ?: "Customer"
            customerWarehouseCode = res.warehouseId
            customerDeliveryAddress = res.deliveryAddress
        }
    }

    LaunchedEffect(state) {
        if (state is IntakeState.Done) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Receive parcel", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(parcel.description ?: "Parcel", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                parcel.retailer?.let { Text(it, color = Brand.ink.copy(alpha = 0.7f)) }
                Text(
                    "Order ${parcel.id.take(8)}",
                    color = Brand.ink.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )

                // Customer delivery address — populated from
                // /api/ops/parcels/:id/customer. Hidden when the customer
                // hasn't filled an address; we don't render an empty
                // "deliver to: —" placeholder.
                val address = customerDeliveryAddress?.trim().orEmpty()
                if (address.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "DELIVER TO",
                            color = Brand.ink.copy(alpha = 0.55f),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = address,
                            color = Brand.ink,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Label preview", color = Brand.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "RE-MINT",
                        color = Brand.Orange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable { sku = WarehouseSku.mint() }
                            .padding(6.dp)
                    )
                }
                val previewBmp = remember(sku, customerName, customerWarehouseCode, parcel.retailer, parcel.description, welcome) {
                    LabelPrinter.renderBitmap(
                        WarehouseLabel(
                            sku = sku,
                            customerName = customerName,
                            customerWarehouseCode = customerWarehouseCode,
                            warehouseCode = "STK-01",
                            retailer = parcel.retailer,
                            description = parcel.description,
                            welcomeMessage = welcome
                        )
                    )
                }
                androidx.compose.foundation.Image(
                    bitmap = previewBmp.asImageBitmap(),
                    contentDescription = "Label preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Measurements", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("L cm", lengthCm, Modifier.weight(1f)) { lengthCm = it }
                    NumField("W cm", widthCm, Modifier.weight(1f)) { widthCm = it }
                    NumField("H cm", heightCm, Modifier.weight(1f)) { heightCm = it }
                    NumField("kg", actualKg, Modifier.weight(1f), allowDecimal = true) { actualKg = it }
                }
            }
        }

        when (val s = state) {
            is IntakeState.Submitting -> CalloutBanner(
                title = "Saving…",
                message = "Posting receive to the server."
            )
            is IntakeState.Failed -> CalloutBanner(
                title = "Couldn't save",
                message = s.message
            )
            else -> Unit
        }

        OrangeButton(
            text = "Print label",
            onClick = {
                LabelPrinter.print(
                    context,
                    WarehouseLabel(
                        sku = sku,
                        customerName = customerName,
                        customerWarehouseCode = customerWarehouseCode,
                        warehouseCode = "STK-01",
                        retailer = parcel.retailer,
                        description = parcel.description,
                        welcomeMessage = welcome
                    )
                )
                printed = true
            }
        )
        InkButton(
            text = if (printed) "Mark received" else "Mark received (skip print)",
            enabled = state !is IntakeState.Submitting,
            onClick = {
                val dims = ParcelDimensions(
                    lengthCm = lengthCm.toDoubleOrNull() ?: 30.0,
                    widthCm = widthCm.toDoubleOrNull() ?: 20.0,
                    heightCm = heightCm.toDoubleOrNull() ?: 20.0,
                    actualKg = actualKg.toDoubleOrNull() ?: 1.0
                )
                vm.selectExisting(parcel)
                vm.submitMeasurements(
                    existing = parcel,
                    dims = dims,
                    photoUrl = null,
                    screening = ScreeningResult.CLEAN,
                    barcode = sku
                )
            }
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NumField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            onChange(v.filter { it.isDigit() || (allowDecimal && it == '.') })
        },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier
    )
}
