package com.thapsus.cargo.android.ui.operator

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.BigStatTile
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.BuyForMeOrderDto
import com.thapsus.cargo.presentation.OpsBuyForMeViewModel

/**
 * Operator-facing queue for Buy-for-me concierge requests. Lists every
 * order with its status, lets the operator open a Send-quote sheet
 * (POST /api/buy-for-me/:id/quote — server fires the customer's
 * "quote ready" email automatically). Mirrors iOS OpsBuyForMeQueueView.
 *
 * P3.1 replaces the P1.2 stub: real per-order cards with retailer
 * link / customer name / size + qty / paid-with-tracking pill, status
 * filter chips, action banner (Done / Error / InFlight) and the full
 * quote bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpsBuyForMeQueueScreen() {
    val vm = remember { ThapsusSdk.opsBuyForMeViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val orders by vm.orders.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    var quotingFor by remember { mutableStateOf<BuyForMeOrderDto?>(null) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val unquoted = orders.count { it.status == "pending_quote" }
    val awaitingPayment = orders.count { it.status == "quoted" }
    val visibleOrders = orders.filter { statusFilter == null || it.status == statusFilter }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EyebrowPill(label = "Operations", icon = Icons.Filled.AutoAwesome)
        EditorialHeader(
            title = "Buy-for-me queue",
            subtitle = "Quote concierge requests · paid orders show parcel tracking."
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStatTile(
                eyebrow = "Unquoted",
                value = unquoted.toString(),
                icon = Icons.Filled.AutoAwesome,
                modifier = Modifier.weight(1f)
            )
            BigStatTile(
                eyebrow = "Awaiting payment",
                value = awaitingPayment.toString(),
                icon = Icons.Filled.HourglassEmpty,
                modifier = Modifier.weight(1f)
            )
        }

        FilterChipsRow(current = statusFilter, onChange = { statusFilter = it })

        when (val a = action) {
            OpsBuyForMeViewModel.ActionState.InFlight -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand.ink)
                }
            }
            is OpsBuyForMeViewModel.ActionState.Done -> CalloutBanner(title = "Done", message = a.message)
            is OpsBuyForMeViewModel.ActionState.Error -> CalloutBanner(title = "Couldn't send", message = a.message)
            else -> {}
        }

        if (visibleOrders.isEmpty()) {
            SoftCard {
                Text(
                    if (orders.isEmpty()) "No pending requests."
                    else "No requests match this filter.",
                    color = Brand.ink.copy(alpha = 0.7f)
                )
            }
        } else {
            visibleOrders.forEach { order ->
                OrderCard(order = order, onQuote = { quotingFor = order })
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    val target = quotingFor
    if (target != null) {
        ModalBottomSheet(
            onDismissRequest = { quotingFor = null },
            sheetState = sheetState
        ) {
            QuoteSheet(
                order = target,
                onCancel = { quotingFor = null },
                onSubmit = { estimateGbp, markupPct, notes ->
                    vm.submitQuote(target.id, estimateGbp, markupPct, notes)
                    quotingFor = null
                }
            )
        }
    }
}

@Composable
private fun FilterChipsRow(current: String?, onChange: (String?) -> Unit) {
    val options = listOf(
        null to "All",
        "pending_quote" to "Unquoted",
        "quoted" to "Quoted",
        "paid" to "Paid",
        "rejected" to "Rejected"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            val selected = current == value
            Box(
                modifier = Modifier
                    .background(
                        if (selected) Brand.ink else Brand.cream.copy(alpha = 0.78f),
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { onChange(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    color = if (selected) Brand.cream else Brand.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OrderCard(order: BuyForMeOrderDto, onQuote: () -> Unit) {
    val context = LocalContext.current
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(order.status)
                Spacer(Modifier.weight(1f))
                order.estimateGbp?.let { g ->
                    Text(
                        "£%.2f · %d%%".format(g, order.markupPct.toInt()),
                        color = Brand.ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                order.itemName,
                style = MaterialTheme.typography.titleMedium,
                color = Brand.ink
            )

            val url = order.retailerUrl
            val isWebUrl = url.startsWith("http://") || url.startsWith("https://")
            if (isWebUrl) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = Brand.Orange, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        url,
                        color = Brand.Orange,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            } else if (url.isNotBlank()) {
                Text(url, color = Brand.ink.copy(alpha = 0.55f), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
            }

            val sizeStr = order.size?.takeIf { it.isNotBlank() }
            val descriptor = buildString {
                if (sizeStr != null) {
                    append("Size: ")
                    append(sizeStr)
                    append(" · ")
                }
                append("Qty: ")
                append(order.qty)
            }
            Text(descriptor, color = Brand.ink.copy(alpha = 0.7f), fontSize = 12.sp)

            (order.name ?: order.email)?.let { who ->
                Text(who, color = Brand.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            order.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Text("\"$note\"", color = Brand.ink.copy(alpha = 0.65f), fontSize = 12.sp)
            }

            if (order.status == "rejected") {
                order.customerDecisionReason?.takeIf { it.isNotBlank() }?.let {
                    Text("Customer rejected: $it", color = Color(0xFFB3261E), fontSize = 12.sp)
                }
            }

            if (order.status == "paid") {
                order.parcelTrackingNumber?.let { tn ->
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF2E7D32).copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.padding(end = 6.dp))
                        Text("Pre-registered as ", color = Brand.ink.copy(alpha = 0.7f), fontSize = 11.sp)
                        Text(tn, color = Brand.ink, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                }
            }

            Button(
                onClick = onQuote,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Brand.Orange,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel(order.status), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun buttonLabel(status: String): String = when (status) {
    "rejected" -> "Re-quote"
    "quoted" -> "Edit quote"
    "paid" -> "View"
    else -> "Send quote"
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "pending_quote" -> Color(0xFFE08B00)
        "quoted" -> Color(0xFF1976D2)
        "paid", "shipped" -> Color(0xFF2E7D32)
        "purchased" -> Color(0xFF6A4DBA)
        "received" -> Color(0xFF0097A7)
        "rejected", "cancelled" -> Color(0xFFB3261E)
        else -> Color(0xFF707070)
    }
    Box(
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
private fun QuoteSheet(
    order: BuyForMeOrderDto,
    onCancel: () -> Unit,
    onSubmit: (estimateGbp: Double, markupPct: Double, notes: String?) -> Unit
) {
    var estimateText by remember(order.id) {
        mutableStateOf(order.estimateGbp?.let { "%.2f".format(it) } ?: "")
    }
    var markupText by remember(order.id) {
        mutableStateOf(order.markupPct.toInt().toString())
    }
    var notes by remember(order.id) { mutableStateOf(order.notes.orEmpty()) }

    val estimate = estimateText.toDoubleOrNull()
    val canSubmit = estimate != null && estimate > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorialHeader(
            eyebrow = "Quote",
            title = "Send quote",
            subtitle = order.email ?: order.name ?: order.itemName
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(order.itemName, color = Brand.ink, fontWeight = FontWeight.SemiBold)
                if (order.size?.isNotBlank() == true) {
                    Text(
                        "Size: ${order.size} · Qty: ${order.qty}",
                        color = Brand.ink.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                } else {
                    Text("Qty: ${order.qty}", color = Brand.ink.copy(alpha = 0.65f), fontSize = 12.sp)
                }
            }
        }

        OutlinedTextField(
            value = estimateText,
            onValueChange = { estimateText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Estimate (GBP)") },
            placeholder = { Text("0.00") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = markupText,
            onValueChange = { markupText = it.filter { c -> c.isDigit() } },
            label = { Text("Service markup %") },
            placeholder = { Text("10") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Note for customer (optional)") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )

        OrangeButton(
            text = "Send quote",
            enabled = canSubmit,
            onClick = {
                onSubmit(
                    estimate ?: 0.0,
                    markupText.toDoubleOrNull() ?: 10.0,
                    notes.takeIf { it.isNotBlank() }
                )
            }
        )
        TextButton(onClick = onCancel) { Text("Cancel") }
        Spacer(Modifier.height(20.dp))
    }
}
