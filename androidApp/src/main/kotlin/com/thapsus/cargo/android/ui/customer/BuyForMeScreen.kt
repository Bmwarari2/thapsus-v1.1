package com.thapsus.cargo.android.ui.customer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.BuyForMeOrderDto
import com.thapsus.cargo.data.dto.RetailerDto
import com.thapsus.cargo.presentation.BuyForMeViewModel

/**
 * "Shop" tab — full Buy-for-me concierge lifecycle. Mirrors iOS
 * BuyForMeView.swift: retailer marquee (UK-only, capped at 12),
 * new-request bottom sheet, action banner, order list with
 * Accept / Reject / Cancel actions keyed off status.
 *
 * Retailer chip tap opens the URL in the user's default browser
 * via Intent.ACTION_VIEW. iOS uses SFSafariViewController inline;
 * Android stays out of the new-dep path by deferring to the system
 * browser. Chrome Custom Tabs would be the natural upgrade if/when
 * we add androidx.browser to the Gradle deps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyForMeScreen() {
    val vm = remember { ThapsusSdk.buyForMeViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }

    LaunchedEffect(vm) {
        vm.load()
        vm.loadRetailers()
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val retailers by vm.retailerCatalog.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var rejectingFor by remember { mutableStateOf<BuyForMeOrderDto?>(null) }
    val context = LocalContext.current
    val createSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rejectSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EyebrowPill(label = "Concierge", icon = Icons.Filled.AutoAwesome)
        EditorialHeader(
            title = "Buy for me",
            subtitle = "Paste a UK retailer link, we buy and ship."
        )

        RetailerMarquee(
            retailers = retailers,
            onOpenUrl = { url ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            }
        )

        OrangeButton(
            text = "New request",
            onClick = { showCreate = true }
        )

        when (val a = action) {
            BuyForMeViewModel.ActionState.InFlight -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand.ink)
                }
            }
            is BuyForMeViewModel.ActionState.Done -> {
                CalloutBanner(title = "Done", message = a.message)
            }
            is BuyForMeViewModel.ActionState.Error -> {
                CalloutBanner(title = "Couldn't complete", message = a.message)
            }
            else -> {}
        }

        when (val s = state) {
            BuyForMeViewModel.UiState.Idle,
            BuyForMeViewModel.UiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand.ink)
                }
            }
            is BuyForMeViewModel.UiState.Error -> {
                CalloutBanner(title = "Couldn't load", message = s.message)
            }
            is BuyForMeViewModel.UiState.Loaded -> {
                if (s.orders.isEmpty()) {
                    SoftCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "No requests yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = Brand.ink
                            )
                            Text(
                                "Drop a retailer link above and we'll quote within 24 hours.",
                                color = Brand.ink.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    s.orders.forEach { order ->
                        OrderRow(
                            order = order,
                            onAccept = { vm.accept(order.id, reason = null) },
                            onReject = { rejectingFor = order },
                            onCancel = { vm.cancel(order.id) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showCreate) {
        ModalBottomSheet(
            onDismissRequest = { showCreate = false },
            sheetState = createSheetState
        ) {
            CreateBuyForMeSheet(
                retailers = retailers.filter { it.country.uppercase() == "UK" }.sortedBy { it.sortOrder },
                onCancel = { showCreate = false },
                onSubmit = { retailerId, url, item, size, qty, notes ->
                    vm.create(
                        retailerUrl = url,
                        itemName = item,
                        size = size,
                        qty = qty,
                        notes = notes
                    )
                    showCreate = false
                }
            )
        }
    }

    val rejectTarget = rejectingFor
    if (rejectTarget != null) {
        ModalBottomSheet(
            onDismissRequest = { rejectingFor = null },
            sheetState = rejectSheetState
        ) {
            RejectQuoteSheet(
                order = rejectTarget,
                onCancel = { rejectingFor = null },
                onSubmit = { reason ->
                    vm.reject(rejectTarget.id, reason)
                    rejectingFor = null
                }
            )
        }
    }
}

@Composable
private fun RetailerMarquee(
    retailers: List<RetailerDto>,
    onOpenUrl: (String) -> Unit
) {
    val curated = retailers
        .filter { it.country.uppercase() == "UK" }
        .sortedBy { it.sortOrder }
        .take(12)
    if (curated.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "POPULAR UK RETAILERS",
            color = Brand.ink.copy(alpha = 0.55f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(curated.size, key = { curated[it].id }) { idx ->
                RetailerChip(retailer = curated[idx], onClick = { onOpenUrl(curated[idx].baseUrl) })
            }
        }
    }
}

/** LazyRow helper — LazyRow doesn't expose `items` overload that takes count + key in a single call without DSL. */
@Suppress("ComposableNaming")
private fun androidx.compose.foundation.lazy.LazyListScope.items(
    count: Int,
    key: (Int) -> Any,
    itemContent: @Composable (Int) -> Unit
) = items(count = count, key = key, itemContent = { idx -> itemContent(idx) })

@Composable
private fun RetailerChip(retailer: RetailerDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(Brand.cream.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
            .border(1.dp, Brand.ink.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Brand.peach, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                retailerIcon(retailer.name),
                contentDescription = null,
                tint = Brand.Orange,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            retailer.name.uppercase(),
            color = Brand.ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
    }
}

private fun retailerIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("amazon") || lower.contains("ebay") || lower.contains("argos") -> Icons.Filled.Inventory2
        lower.contains("zara") || lower.contains("asos") || lower.contains("shein") ||
            lower.contains("next") || lower.contains("marks") -> Icons.Filled.ShoppingBag
        lower.contains("temu") || lower.contains("aliexpress") -> Icons.Filled.Bolt
        lower.contains("currys") || lower.contains("apple") || lower.contains("john lewis") -> Icons.Filled.LaptopMac
        else -> Icons.Filled.Storefront
    }
}

@Composable
private fun OrderRow(
    order: BuyForMeOrderDto,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit
) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    order.itemName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Brand.ink,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(order.status)
            }
            order.estimateGbp?.let { estimate ->
                Text(
                    "Quote: £ %.2f + %d%% service".format(estimate, order.markupPct.toInt()),
                    color = Brand.ink.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
            if (order.retailerUrl.isNotBlank()) {
                Text(
                    order.retailerUrl,
                    color = Brand.ink.copy(alpha = 0.55f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            when (order.status) {
                "quoted" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onAccept,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Brand.Orange,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Accept & buy", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = onReject,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE0E0E0),
                                contentColor = Color(0xFFB3261E)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Cancel, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reject")
                        }
                    }
                }
                "rejected" -> {
                    order.customerDecisionReason?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "You rejected: $it",
                            color = Color(0xFFB3261E),
                            fontSize = 12.sp
                        )
                    }
                }
                "pending_quote" -> {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE0E0E0),
                            contentColor = Brand.ink
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel request")
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "pending_quote" -> Color(0xFFE08B00)
        "quoted" -> Color(0xFF1976D2)
        "paid" -> Color(0xFF2E7D32)
        "purchased" -> Color(0xFF6A4DBA)
        "received" -> Color(0xFF0097A7)
        "shipped" -> Color(0xFF2E7D32)
        "cancelled", "rejected" -> Color(0xFFB3261E)
        else -> Color(0xFF707070)
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            status.replace('_', ' ').uppercase(),
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun CreateBuyForMeSheet(
    retailers: List<RetailerDto>,
    onCancel: () -> Unit,
    onSubmit: (retailerId: String?, url: String, item: String, size: String?, qty: Int, notes: String?) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var item by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var selectedRetailerId by remember { mutableStateOf<String?>(null) }
    val isOther = selectedRetailerId == "__other__"
    val canSubmit = item.isNotBlank() && selectedRetailerId != null && (!isOther || url.isNotBlank())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EyebrowPill(label = "Concierge", icon = Icons.Filled.AutoAwesome)
        EditorialHeader(
            title = "New request",
            subtitle = "Paste a UK retailer link and we'll quote within 24 hours."
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Retailer", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                retailers.forEach { r ->
                    RetailerOption(
                        label = r.name,
                        selected = selectedRetailerId == r.id,
                        onClick = { selectedRetailerId = r.id; url = "" }
                    )
                }
                RetailerOption(
                    label = "Other (paste a URL)",
                    selected = isOther,
                    onClick = { selectedRetailerId = "__other__" }
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(if (isOther) "Retailer URL" else "Item URL (optional)") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (isOther) "Paste the full URL — we'll quote within 24h."
                    else "Pick a retailer above. The URL field is optional unless you chose Other.",
                    color = Brand.ink.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Item details", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = item,
                    onValueChange = { item = it },
                    label = { Text("Item name") },
                    placeholder = { Text("e.g. Blue hoodie size M") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text("Size / variant (optional)") },
                    placeholder = { Text("e.g. M, 42, Black") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quantity", color = Brand.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    QuantityStepper(value = qty, onChange = { qty = it })
                }
            }
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notes for our team", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Colour preferences, alternatives, deadlines…") },
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                )
            }
        }

        Button(
            onClick = {
                val resolved = if (isOther) null else selectedRetailerId
                onSubmit(
                    resolved,
                    url,
                    item,
                    size.takeIf { it.isNotBlank() },
                    qty,
                    notes.takeIf { it.isNotBlank() }
                )
            },
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand.Orange,
                contentColor = Color.White,
                disabledContainerColor = Brand.Orange.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Request a quote", fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Brand.ink
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RetailerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Brand.Orange.copy(alpha = 0.16f) else Brand.cream.copy(alpha = 0.55f)
    val border = if (selected) Brand.Orange else Brand.ink.copy(alpha = 0.1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Brand.ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Brand.Orange, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun QuantityStepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepperButton(label = "−", onClick = { if (value > 1) onChange(value - 1) }, enabled = value > 1)
        Text(
            value.toString(),
            color = Brand.Orange,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp
        )
        StepperButton(label = "+", onClick = { if (value < 20) onChange(value + 1) }, enabled = value < 20)
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                if (enabled) Brand.ink else Brand.ink.copy(alpha = 0.25f),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Brand.cream, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun RejectQuoteSheet(
    order: BuyForMeOrderDto,
    onCancel: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val trimmed = reason.trim()
    val canSubmit = trimmed.length >= 3

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EditorialHeader(
            eyebrow = "Quote",
            title = "Reject quote",
            subtitle = "Tell us why so we can re-quote against something actionable."
        )
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(order.itemName, color = Brand.ink, fontWeight = FontWeight.SemiBold)
                order.estimateGbp?.let { g ->
                    Text(
                        "Quoted: £%.2f + %d%% service".format(g, order.markupPct.toInt()),
                        color = Brand.ink.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }
            }
        }
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Reason") },
            placeholder = { Text("Too expensive, wrong colour, found cheaper…") },
            modifier = Modifier.fillMaxWidth().height(140.dp)
        )
        Button(
            onClick = { onSubmit(trimmed) },
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFB3261E),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFB3261E).copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Reject", fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Brand.ink
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
        Spacer(Modifier.height(20.dp))
    }
}
