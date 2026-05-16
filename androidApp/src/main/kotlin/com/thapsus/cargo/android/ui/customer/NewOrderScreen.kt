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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.InsuranceTier
import com.thapsus.cargo.data.repository.AuthSession
import com.thapsus.cargo.presentation.ParcelPreRegViewModel
import com.thapsus.cargo.presentation.WarehouseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RETAILERS = listOf("Amazon", "Shein", "Next", "Asos", "Superdrug", "eBay", "ZARA", "H&M", "Other")

private val DEFAULT_WAREHOUSE_LINES = listOf(
    "31 Collingwood Close",
    "Hazel Grove, Stockport",
    "SK7 4LB",
    "United Kingdom"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(
    userId: String,
    onClose: () -> Unit,
    session: AuthSession.Authenticated? = null
) {
    val vm = remember(userId) { ThapsusSdk.parcelPreRegViewModel(userId) }
    DisposableEffect(vm) { onDispose { vm.reset() } }

    // Warehouse address VM — folded in from the standalone
    // WarehouseAddressScreen so the customer sees the shipping
    // address right when they need it (before picking a retailer).
    val warehouseVm = remember { ThapsusSdk.warehouseViewModel() }
    LaunchedEffect(warehouseVm) { warehouseVm.load() }
    val warehouseState by warehouseVm.state.collectAsStateWithLifecycle()

    val state by vm.state.collectAsStateWithLifecycle()

    var retailer by remember { mutableStateOf("") }
    var customRetailer by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var declaredGbp by remember { mutableStateOf("") }

    val isSubmitting = state is ParcelPreRegViewModel.State.Submitting
    val saved = state as? ParcelPreRegViewModel.State.Saved

    val resolvedRetailer = if (retailer == "Other") customRetailer else retailer
    val canSubmit = resolvedRetailer.isNotBlank()
        && description.isNotBlank()
        && declaredGbp.toDoubleOrNull() != null
        && !isSubmitting
        && saved == null

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("New order") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EyebrowPill(label = "Send a parcel")
            EditorialHeader(
                title = "New order",
                subtitle = "Ship to our UK warehouse — we'll consolidate and book a slot on the next weekly flight."
            )

            WarehouseAddressInlineCard(
                session = session,
                warehouseState = warehouseState
            )

            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pick a retailer", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    RetailerGrid(selected = retailer, onSelect = { retailer = it })
                    if (retailer == "Other") {
                        OutlinedTextField(
                            value = customRetailer,
                            onValueChange = { customRetailer = it },
                            label = { Text("Retailer name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tell us about it", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("e.g. Blue hoodie size M") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = declaredGbp,
                        onValueChange = { declaredGbp = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Declared value (£)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            when (val s = state) {
                is ParcelPreRegViewModel.State.Submitting -> CalloutBanner(
                    title = "Submitting…",
                    message = "Sending your order to Thapsus Cargo."
                )
                is ParcelPreRegViewModel.State.Saved -> CalloutBanner(
                    title = "Order created",
                    message = "Tracking number ${s.order.trackingNumber ?: s.order.id.take(8)}. A confirmation email is on its way."
                )
                is ParcelPreRegViewModel.State.Failed -> CalloutBanner(
                    title = "Couldn't create the order",
                    message = s.message
                )
                else -> Unit
            }

            if (saved != null) {
                InkButton(text = "Done", onClick = onClose)
            } else {
                InkButton(
                    text = if (isSubmitting) "Submitting…" else "Create order",
                    enabled = canSubmit,
                    onClick = {
                        val pence = ((declaredGbp.toDoubleOrNull() ?: 0.0) * 100).toLong()
                        vm.submit(
                            ParcelPreRegViewModel.PreRegInput(
                                retailer = resolvedRetailer,
                                description = description,
                                declaredValueGbpPence = pence,
                                // Insurance offering removed 2026-04-30. The shared
                                // PreRegInput still requires an InsuranceTier so we
                                // always send the no-cost `standard` baseline (same
                                // shape iOS NewOrderView passes).
                                insuranceTier = InsuranceTier.STANDARD,
                                // China market stripped 2026-05-11. UK is the only
                                // origin we ship from now.
                                market = "UK",
                                shippingSpeed = "economy",
                                hsTier = "general"
                            )
                        )
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WarehouseAddressInlineCard(
    session: AuthSession.Authenticated?,
    warehouseState: WarehouseViewModel.UiState
) {
    val fullName = session?.profile?.fullName?.takeIf { it.isNotBlank() }
    val warehouseCode = session?.profile?.warehouseId?.takeIf { it.isNotBlank() } ?: "TC-XXXX"
    val lines = (warehouseState as? WarehouseViewModel.UiState.Loaded)
        ?.addresses?.get("UK")?.lines
        ?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_WAREHOUSE_LINES

    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    InkCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Brand.Orange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "SHIP YOUR PARCEL HERE",
                    color = Brand.cream,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
            }
            Text(
                "Use this address when you check out at the UK retailer. The warehouse code on line 2 is what tags the parcel to your account.",
                color = Brand.cream.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (fullName != null) {
                        Text(
                            fullName,
                            color = Brand.Orange,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                    Text(
                        warehouseCode,
                        color = Brand.Orange,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    lines.forEach { line ->
                        Text(
                            line,
                            color = Brand.cream.copy(alpha = 0.85f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            OrangeButton(
                text = if (copied) "Copied!" else "Copy address",
                onClick = {
                    val payload = (listOfNotNull(fullName, warehouseCode) + lines)
                        .joinToString("\n")
                    clipboard.setText(AnnotatedString(payload))
                    copied = true
                    scope.launch {
                        delay(1800)
                        copied = false
                    }
                }
            )
        }
    }
}

@Composable
private fun RetailerGrid(selected: String, onSelect: (String) -> Unit) {
    val rows = RETAILERS.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { name ->
                    val active = name == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (active) Brand.Orange.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(name) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name,
                            color = Brand.ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
