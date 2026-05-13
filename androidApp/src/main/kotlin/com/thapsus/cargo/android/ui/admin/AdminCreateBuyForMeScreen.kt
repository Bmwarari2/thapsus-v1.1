package com.thapsus.cargo.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.AdminUserDto
import com.thapsus.cargo.data.dto.RetailerDto
import com.thapsus.cargo.presentation.AdminCreateBuyForMeViewModel

/**
 * Admin on-behalf BFM creation form. Mirrors iOS AdminCreateBuyForMeView.
 *
 * Flow:
 *   1. Customer search (live filter over the AdminCreateBuyForMeViewModel.users
 *      snapshot — server returns up to 500 customers).
 *   2. Retailer picker (UK-only, sorted, with "Other" fallback) + optional URL.
 *   3. Item name, size, qty, notes.
 *   4. Optional pre-quote: leave blank for status='pending_quote'; enter an
 *      estimate to fire status='quoted' + the quote email instantly.
 */
@Composable
fun AdminCreateBuyForMeScreen(onClose: () -> Unit) {
    val vm = remember { ThapsusSdk.adminCreateBuyForMeViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.bootstrap() }

    val users by vm.users.collectAsStateWithLifecycle()
    val retailers by vm.retailerCatalog.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    var customerQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<AdminUserDto?>(null) }
    var selectedRetailerId by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var preQuoteEnabled by remember { mutableStateOf(false) }
    var estimateText by remember { mutableStateOf("") }
    var markupText by remember { mutableStateOf("10") }

    val ukRetailers = remember(retailers) {
        retailers.filter { it.country.uppercase() == "UK" }.sortedBy { it.sortOrder }
    }
    val isOther = selectedRetailerId == "__other__"
    val canSubmit = selectedUser != null && itemName.isNotBlank() &&
        selectedRetailerId != null && (!isOther || url.isNotBlank()) &&
        (!preQuoteEnabled || (estimateText.toDoubleOrNull() ?: 0.0) > 0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand.ink)
            }
        }
        EyebrowPill(label = "Admin")
        EditorialHeader(
            title = "Create Buy-for-me",
            subtitle = "Place a concierge order on a customer's behalf."
        )

        (action as? AdminCreateBuyForMeViewModel.ActionState.Done)?.let { done ->
            CalloutBanner(
                title = if (done.preQuoted) "Quoted & emailed" else "Created — pending quote",
                message = "Order ${done.orderId.take(8)} · ${done.itemName}" +
                    (done.customerEmail?.let { " · $it" } ?: "")
            )
        }
        (action as? AdminCreateBuyForMeViewModel.ActionState.Error)?.let { err ->
            CalloutBanner(title = "Couldn't create", message = err.message)
        }

        // Customer picker
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Customer", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                val selected = selectedUser
                if (selected != null) {
                    SelectedUserRow(user = selected, onClear = { selectedUser = null })
                } else {
                    OutlinedTextField(
                        value = customerQuery,
                        onValueChange = { customerQuery = it },
                        label = { Text("Search name, email, or phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val q = customerQuery.trim().lowercase()
                    val matches = if (q.isEmpty()) {
                        emptyList()
                    } else {
                        users.asSequence()
                            .filter {
                                (it.email.lowercase().contains(q)) ||
                                    it.name.lowercase().contains(q) ||
                                    (it.phone?.lowercase()?.contains(q) == true)
                            }
                            .take(8)
                            .toList()
                    }
                    if (q.isEmpty()) {
                        Text(
                            "Type 2+ chars to filter the customer list.",
                            color = Brand.ink.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    } else if (matches.isEmpty()) {
                        Text(
                            "No customers match.",
                            color = Brand.ink.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    } else {
                        matches.forEach { u ->
                            UserMatchRow(user = u, onPick = { selectedUser = u })
                        }
                    }
                }
            }
        }

        // Retailer picker
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Retailer", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                ukRetailers.forEach { r ->
                    RetailerOption(label = r.name, selected = selectedRetailerId == r.id) {
                        selectedRetailerId = r.id; url = ""
                    }
                }
                RetailerOption(label = "Other (paste a URL)", selected = isOther) {
                    selectedRetailerId = "__other__"
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(if (isOther) "Retailer URL" else "Item URL (optional)") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Item details
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Item details", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Item name") },
                    placeholder = { Text("e.g. Blue hoodie size M") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text("Size / variant (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quantity", color = Brand.ink, modifier = Modifier.weight(1f))
                    QuantityStepper(value = qty, onChange = { qty = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes for ops (optional)") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
        }

        // Optional pre-quote
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Pre-quote on submit",
                        color = Brand.ink,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.Switch(
                        checked = preQuoteEnabled,
                        onCheckedChange = { preQuoteEnabled = it }
                    )
                }
                Text(
                    "Off: order lands at pending_quote for ops. On: status='quoted' + customer is emailed immediately.",
                    color = Brand.ink.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )
                if (preQuoteEnabled) {
                    OutlinedTextField(
                        value = estimateText,
                        onValueChange = { v -> estimateText = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("Estimate (GBP)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = markupText,
                        onValueChange = { v -> markupText = v.filter { it.isDigit() } },
                        label = { Text("Service markup %") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (action is AdminCreateBuyForMeViewModel.ActionState.Submitting) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        }

        OrangeButton(
            text = if (preQuoteEnabled) "Create & send quote" else "Create order",
            enabled = canSubmit && action !is AdminCreateBuyForMeViewModel.ActionState.Submitting,
            onClick = {
                val user = selectedUser ?: return@OrangeButton
                val resolvedRetailerId = if (isOther) null else selectedRetailerId
                val estimate = if (preQuoteEnabled) estimateText.toDoubleOrNull() else null
                val markup = if (preQuoteEnabled) markupText.toDoubleOrNull() ?: 10.0 else null
                vm.create(
                    userId = user.id,
                    itemName = itemName.trim(),
                    retailerId = resolvedRetailerId,
                    retailerUrl = url.trim().takeIf { it.isNotBlank() },
                    size = size.trim().takeIf { it.isNotBlank() },
                    qty = qty,
                    notes = notes.trim().takeIf { it.isNotBlank() },
                    estimateGbp = estimate,
                    markupPct = markup
                )
            }
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SelectedUserRow(user: AdminUserDto, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.Orange.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Brand.Orange, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, color = Brand.ink, fontWeight = FontWeight.SemiBold)
            Text(user.email, color = Brand.ink.copy(alpha = 0.65f), fontSize = 12.sp)
        }
        androidx.compose.material3.TextButton(onClick = onClear) { Text("Change") }
    }
}

@Composable
private fun UserMatchRow(user: AdminUserDto, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onPick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, color = Brand.ink, fontWeight = FontWeight.SemiBold)
            Text(user.email, color = Brand.ink.copy(alpha = 0.65f), fontSize = 11.sp)
        }
        user.warehouseId?.let {
            Text(it, color = Brand.Orange, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RetailerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Brand.Orange.copy(alpha = 0.16f) else Brand.cream.copy(alpha = 0.55f)
    val borderColor = if (selected) Brand.Orange else Brand.ink.copy(alpha = 0.1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Brand.ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Brand.Orange, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun QuantityStepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepperButton(label = "−", enabled = value > 1, onClick = { onChange(value - 1) })
        Text(
            value.toString(),
            color = Brand.Orange,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp
        )
        StepperButton(label = "+", enabled = value < 20, onClick = { onChange(value + 1) })
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(if (enabled) Brand.ink else Brand.ink.copy(alpha = 0.25f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Brand.cream, fontWeight = FontWeight.ExtraBold)
    }
}
