package com.thapsus.cargo.android.ui.admin

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
import androidx.compose.material3.TextButton
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
import com.thapsus.cargo.presentation.AdminIssueInvoiceViewModel

/**
 * Admin standalone-invoice issuance. Mirrors iOS AdminIssueInvoiceView.
 * Server creates a customer_consolidation row with isStandalone=true so the
 * customer pays via the regular consolidation payment flow.
 */
@Composable
fun AdminIssueInvoiceScreen(onClose: () -> Unit) {
    val vm = remember { ThapsusSdk.adminIssueInvoiceViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.loadUsers() }

    val users by vm.users.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    var customerQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<AdminUserDto?>(null) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val canSubmit = selectedUser != null && (amountText.toDoubleOrNull() ?: 0.0) > 0.0 &&
        description.trim().isNotBlank() &&
        action !is AdminIssueInvoiceViewModel.ActionState.Submitting

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
            title = "Issue invoice",
            subtitle = "Standalone charge — customer pays via the regular invoice flow."
        )

        (action as? AdminIssueInvoiceViewModel.ActionState.Done)?.let { done ->
            CalloutBanner(
                title = "Invoice issued",
                message = "KES %,.0f · ${done.description}".format(done.amountKes) +
                    (done.customerEmail?.let { " · $it" } ?: "")
            )
        }
        (action as? AdminIssueInvoiceViewModel.ActionState.Error)?.let { err ->
            CalloutBanner(title = "Couldn't issue", message = err.message)
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Customer", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                val selected = selectedUser
                if (selected != null) {
                    SelectedUserChip(user = selected, onClear = { selectedUser = null })
                } else {
                    OutlinedTextField(
                        value = customerQuery,
                        onValueChange = { customerQuery = it },
                        label = { Text("Search name, email, or phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val q = customerQuery.trim().lowercase()
                    val matches = if (q.isEmpty()) emptyList() else users.asSequence()
                        .filter {
                            it.email.lowercase().contains(q) ||
                                it.name.lowercase().contains(q) ||
                                (it.phone?.lowercase()?.contains(q) == true)
                        }
                        .take(8)
                        .toList()
                    matches.forEach { u ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brand.cream.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .clickable { selectedUser = u }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.name, color = Brand.ink, fontWeight = FontWeight.SemiBold)
                                Text(u.email, color = Brand.ink.copy(alpha = 0.65f), fontSize = 11.sp)
                            }
                            u.warehouseId?.let {
                                Text(it, color = Brand.Orange, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }
                    if (q.isEmpty()) {
                        Text("Type 2+ chars to filter.", color = Brand.ink.copy(alpha = 0.6f), fontSize = 12.sp)
                    } else if (matches.isEmpty()) {
                        Text("No matches.", color = Brand.ink.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Invoice", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Amount (KES)") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Line item description") },
                    placeholder = { Text("e.g. Customs duty for parcel THP-3F8C2A") },
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Internal notes (optional)") },
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )
            }
        }

        if (action is AdminIssueInvoiceViewModel.ActionState.Submitting) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        }

        OrangeButton(
            text = "Issue invoice",
            enabled = canSubmit,
            onClick = {
                val user = selectedUser ?: return@OrangeButton
                vm.issue(
                    userId = user.id,
                    amountKes = amountText.toDoubleOrNull() ?: 0.0,
                    description = description.trim(),
                    notes = notes.trim().takeIf { it.isNotBlank() }
                )
            }
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SelectedUserChip(user: AdminUserDto, onClear: () -> Unit) {
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
        TextButton(onClick = onClear) { Text("Change") }
    }
}
