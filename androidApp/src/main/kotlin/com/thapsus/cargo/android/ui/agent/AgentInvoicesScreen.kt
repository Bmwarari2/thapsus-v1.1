package com.thapsus.cargo.android.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.AgentInvoicesViewModel

@Composable
fun AgentInvoicesScreen(agentId: String) {
    val vm = remember { ThapsusSdk.agentInvoicesViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.load() }

    val state by vm.state.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    var consolId by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EyebrowPill(label = "Customs")
        EditorialHeader(title = "Invoices", subtitle = "Submit clearing fees per consolidation.")

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Submit invoice", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = consolId,
                    onValueChange = { consolId = it },
                    label = { Text("Consolidation ID (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoiceNo,
                    onValueChange = { invoiceNo = it },
                    label = { Text("Invoice number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (KES)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                InkButton(
                    text = "Submit",
                    enabled = invoiceNo.isNotBlank() && amount.isNotBlank(),
                    onClick = {
                        vm.submit(
                            consolidationId = consolId.trim().ifBlank { null },
                            invoiceNo = invoiceNo.trim().ifBlank { null },
                            amountKes = amount.toDoubleOrNull() ?: 0.0,
                            docUrl = null,
                            notes = notes.trim().ifBlank { null }
                        )
                    }
                )
            }
        }

        (action as? AgentInvoicesViewModel.ActionState.Done)?.let {
            CalloutBanner(title = "Submitted", message = it.message)
        }
        (action as? AgentInvoicesViewModel.ActionState.Error)?.let {
            CalloutBanner(title = "Couldn't submit", message = it.message)
        }

        when (val s = state) {
            is AgentInvoicesViewModel.UiState.Loaded -> {
                Text("Submitted", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                if (s.invoices.isEmpty()) {
                    SoftCard { Text("No invoices yet.", color = Brand.ink.copy(alpha = 0.7f)) }
                } else {
                    s.invoices.forEach { inv ->
                        SoftCard {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text(
                                        inv.invoiceNo ?: inv.id.take(8),
                                        color = Brand.ink,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "%.2f KES".format(inv.amountKes),
                                        color = Brand.Orange,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                inv.consolidationId?.let {
                                    Text(
                                        "Consol $it",
                                        color = Brand.ink.copy(alpha = 0.6f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is AgentInvoicesViewModel.UiState.Error -> CalloutBanner(
                title = "Couldn't load",
                message = s.message
            )
            else -> Unit
        }
        Spacer(Modifier.height(24.dp))
    }
}
