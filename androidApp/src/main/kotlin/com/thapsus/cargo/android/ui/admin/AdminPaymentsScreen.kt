package com.thapsus.cargo.android.ui.admin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.PaymentDto
import com.thapsus.cargo.presentation.AdminPaymentsViewModel

/**
 * M-Pesa payments review queue — Android twin of iOS AdminPaymentsView.
 * Reads from /admin/payments/pending (PaymentsRepository.pendingMpesaQueue).
 * Stripe payments do NOT appear here; the webhook auto-flips them to paid.
 *
 * When the customer-claimed amount is strictly less than the invoice
 * (mpesaMessageAmountKes < amountDueKes), the Approve button changes to
 * "Verify w/ override" and opens a sheet that captures a >=10-char
 * reason; that reason is sent as approvalOverrideReason on the server.
 */
@Composable
fun AdminPaymentsScreen(onBack: () -> Unit) {
    val vm = remember { ThapsusSdk.adminPaymentsViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.load() }

    val state by vm.state.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    var rejectTarget by remember { mutableStateOf<PaymentDto?>(null) }
    var overrideTarget by remember { mutableStateOf<PaymentDto?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand.ink)
            }
        }
        EyebrowPill(label = "Admin")
        EditorialHeader(
            title = "M-Pesa review",
            subtitle = "Verify customer-submitted M-Pesa SMS proofs. Approve to mark the payment paid, reject with a reason if it doesn't match."
        )

        when (val a = action) {
            is AdminPaymentsViewModel.ActionState.Done -> CalloutBanner(
                title = "Done",
                message = a.message,
                tint = Color(0xFF0F8A4F).copy(alpha = 0.14f)
            )
            is AdminPaymentsViewModel.ActionState.Error -> CalloutBanner(
                title = "Couldn't update",
                message = a.message
            )
            AdminPaymentsViewModel.ActionState.InFlight -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand.ink) }
            AdminPaymentsViewModel.ActionState.Idle -> Unit
        }

        when (val s = state) {
            AdminPaymentsViewModel.UiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand.ink) }
            is AdminPaymentsViewModel.UiState.Error -> CalloutBanner(
                title = "Couldn't load",
                message = s.message
            )
            is AdminPaymentsViewModel.UiState.Loaded -> {
                if (s.pending.isEmpty()) {
                    SoftCard {
                        Text(
                            "No payments waiting for review.",
                            color = Brand.ink.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    s.pending.forEach { payment ->
                        PaymentCard(
                            payment = payment,
                            onApprove = { vm.approve(payment.id, overrideReason = null) },
                            onApproveWithOverride = { overrideTarget = payment },
                            onReject = { rejectTarget = payment }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    rejectTarget?.let { p ->
        RejectPaymentSheet(
            payment = p,
            onDismiss = { rejectTarget = null },
            onSubmit = { reason ->
                vm.reject(p.id, reason)
                rejectTarget = null
            }
        )
    }
    overrideTarget?.let { p ->
        OverridePaymentSheet(
            payment = p,
            onDismiss = { overrideTarget = null },
            onSubmit = { reason ->
                vm.approve(p.id, overrideReason = reason)
                overrideTarget = null
            }
        )
    }
}

@Composable
private fun PaymentCard(
    payment: PaymentDto,
    onApprove: () -> Unit,
    onApproveWithOverride: () -> Unit,
    onReject: () -> Unit
) {
    var expandedSms by rememberSaveable(payment.id) { mutableStateOf(false) }
    val isShort = isShortPayment(payment)

    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        payment.userName ?: payment.userEmail ?: payment.userId,
                        color = Brand.ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    payment.userEmail?.let {
                        Text(it, color = Brand.ink.copy(alpha = 0.55f), fontSize = 11.sp)
                    }
                }
                Text(
                    "KES " + formatKes(payment.amountDueKes),
                    color = Brand.Orange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }

            Text(
                "${targetLabel(payment.targetKind)} · ${payment.id.take(14)}…",
                color = Brand.ink.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Pill(label = "REF", value = payment.mpesaReference ?: "—")
                payment.mpesaMessageAmountKes?.let { claimed ->
                    val tint = if (claimed != payment.amountDueKes) Color(0xFFD32F2F) else Color(0xFF0F8A4F)
                    Pill(label = "CLAIMED", value = "KES " + formatKes(claimed), tint = tint)
                }
                payment.mpesaPhone?.let { phone ->
                    Pill(label = "PHONE", value = phone)
                }
            }

            TextButton(
                onClick = { expandedSms = !expandedSms },
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Icon(
                    if (expandedSms) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = Brand.ink
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    if (expandedSms) "Hide raw SMS" else "Show raw SMS",
                    color = Brand.ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }

            if (expandedSms) {
                payment.mpesaMessageRaw?.let { raw ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brand.cream.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            raw,
                            color = Brand.ink.copy(alpha = 0.75f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = if (isShort) onApproveWithOverride else onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isShort) Color(0xFFD9A441) else Brand.Orange,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        if (isShort) "Verify w/ override" else "Approve",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F).copy(alpha = 0.14f),
                        contentColor = Color(0xFFD32F2F)
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("Reject", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, value: String, tint: Color = Brand.ink) {
    Column(
        modifier = Modifier
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = Brand.ink.copy(alpha = 0.55f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            value,
            color = tint,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RejectPaymentSheet(
    payment: PaymentDto,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by rememberSaveable { mutableStateOf("") }
    val trimmed = reason.trim()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Reject payment", color = Brand.ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(
                payment.userName ?: payment.userEmail ?: payment.userId,
                color = Brand.ink,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Amount due: KES ${formatKes(payment.amountDueKes)}",
                color = Brand.ink.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            payment.mpesaMessageAmountKes?.let { claimed ->
                Text(
                    "Customer claimed: KES ${formatKes(claimed)}",
                    color = Brand.ink.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            Text("Why reject?", color = Brand.ink, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text("Tell the customer what was wrong.") }
            )
            Text(
                "The customer can resubmit a fresh M-Pesa SMS for the same payment.",
                color = Brand.ink.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
            Button(
                onClick = { onSubmit(trimmed) },
                enabled = trimmed.length >= 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F),
                    contentColor = Color.White
                )
            ) { Text("Reject", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverridePaymentSheet(
    payment: PaymentDto,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by rememberSaveable { mutableStateOf("") }
    val trimmed = reason.trim()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Approve with override",
                color = Brand.ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )
            SoftCard(tint = Brand.cream) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Customer claimed",
                            modifier = Modifier.weight(1f),
                            color = Brand.ink.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                        Text(
                            "KES ${formatKes(payment.mpesaMessageAmountKes ?: 0L)}",
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Invoice due",
                            modifier = Modifier.weight(1f),
                            color = Brand.ink.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                        Text(
                            "KES ${formatKes(payment.amountDueKes)}",
                            color = Brand.ink,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Text(
                "M-Pesa SMS shows less than the invoice. Provide a written reason to approve anyway. The note is recorded on the payment for audit.",
                color = Brand.ink.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Text(
                "Reason (min 10 characters)",
                color = Brand.ink,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text("Explain why this short payment is being approved.") }
            )
            Button(
                onClick = { onSubmit(trimmed) },
                enabled = trimmed.length >= 10,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Brand.Orange,
                    contentColor = Color.White
                )
            ) { Text("Approve", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun targetLabel(targetKind: String): String = when (targetKind) {
    "buy_for_me" -> "Buy-for-me"
    "consolidation" -> "Consolidation"
    "order" -> "Order"
    else -> targetKind.uppercase()
}

private fun isShortPayment(p: PaymentDto): Boolean {
    val claimed = p.mpesaMessageAmountKes ?: return false
    return claimed < p.amountDueKes
}

private fun formatKes(value: Long): String {
    val s = value.toString()
    val sb = StringBuilder()
    val start = if (value < 0) 1 else 0
    val digits = s.substring(start)
    digits.reversed().forEachIndexed { i, c ->
        if (i > 0 && i % 3 == 0) sb.append(',')
        sb.append(c)
    }
    if (value < 0) sb.append('-')
    return sb.reverse().toString()
}
