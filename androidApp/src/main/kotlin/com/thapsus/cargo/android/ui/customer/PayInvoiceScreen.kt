package com.thapsus.cargo.android.ui.customer

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.PaymentsViewModel
import kotlin.math.max

/**
 * Customer payment surface — replaces the wallet (server migration 028).
 * Sheet-style screen invoked from any "Pay" CTA (Invoices, BFM accept,
 * future order pay). Mirrors iOS PayInvoiceView.
 *
 * Flow:
 *   1. Show gross amount, credit applied, net amount due (KES).
 *   2. Customer picks Card (Stripe) or M-Pesa.
 *   3a. Card → Stripe path. Android Stripe SDK is not yet wired into
 *       the Gradle deps; this surfaces a clear "Card payments coming
 *       soon" banner and steers the customer to M-Pesa. Once approval
 *       lands to add `com.stripe:stripe-android`, the Stripe Ready
 *       state can be honoured directly here.
 *   3b. M-Pesa → server returns Till + reference + amount → customer
 *       pays in their M-Pesa app, then pastes the confirmation SMS
 *       into MpesaSubmitBottomSheet → status → 'awaiting_review'.
 *       If the merchant is on Lipana STK, LipanaStkBottomSheet drives
 *       the phone-input → STK init → awaiting-PIN stages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayInvoiceScreen(
    targetKind: String,
    targetId: String,
    targetTitle: String,
    amountKesGross: Long,
    onClose: () -> Unit
) {
    val vm = remember { ThapsusSdk.paymentsViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.bootstrap() }

    val state by vm.state.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    val mpesaSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lipanaSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val confirmationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
        EyebrowPill(label = "Pay", icon = Icons.Filled.CreditCard)
        EditorialHeader(
            title = "Pay your invoice",
            subtitle = targetTitle
        )

        val ready = state as? PaymentsViewModel.UiState.Ready
        SummaryCard(amountKesGross = amountKesGross, creditKes = ready?.creditBalanceKes ?: 0L)

        when (val a = action) {
            PaymentsViewModel.ActionState.Creating,
            PaymentsViewModel.ActionState.Submitting -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand.ink)
                }
            }
            is PaymentsViewModel.ActionState.Error -> {
                CalloutBanner(title = "Couldn't process", message = a.message)
            }
            else -> {}
        }

        when (state) {
            PaymentsViewModel.UiState.Idle,
            PaymentsViewModel.UiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand.ink)
                }
            }
            is PaymentsViewModel.UiState.Error -> {
                CalloutBanner(
                    title = "Couldn't reach payments",
                    message = (state as PaymentsViewModel.UiState.Error).message
                )
            }
            is PaymentsViewModel.UiState.Ready -> {
                val r = state as PaymentsViewModel.UiState.Ready
                if (r.mpesaEnabled) {
                    MethodCard(
                        icon = Icons.Filled.PhoneAndroid,
                        title = "Pay with M-Pesa",
                        subtitle = if (r.mpesaProvider == "lipana")
                            "Get an STK Push on your phone."
                        else
                            "Pay to Till ${r.mpesaTillNumber} then paste the SMS.",
                        onClick = {
                            vm.create(
                                targetKind = targetKind,
                                targetId = targetId,
                                method = "mpesa",
                                applyCredit = true,
                                phone = null
                            )
                        }
                    )
                }
                if (r.stripeEnabled) {
                    MethodCard(
                        icon = Icons.Filled.CreditCard,
                        title = "Pay with card",
                        subtitle = "Card payments via the Android app are coming soon. Use M-Pesa for now, or pay this invoice from the web.",
                        onClick = { /* Stripe SDK not wired; no-op */ },
                        disabled = true
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // M-Pesa manual sheet — customer pastes the confirmation SMS.
    (action as? PaymentsViewModel.ActionState.MpesaReady)?.let { ready ->
        ModalBottomSheet(
            onDismissRequest = { vm.resetAction() },
            sheetState = mpesaSheetState
        ) {
            MpesaSubmitBottomSheet(
                paybill = ready.paybill,
                account = ready.account,
                amountDueKes = ready.amountDueKes,
                onCancel = { vm.resetAction() },
                onSubmit = { sms -> vm.submitMpesaConfirmation(ready.payment.id, sms) }
            )
        }
    }

    // Lipana STK in-flight — customer is being prompted on their phone.
    (action as? PaymentsViewModel.ActionState.LipanaStkInflight)?.let { stk ->
        ModalBottomSheet(
            onDismissRequest = { vm.resetAction() },
            sheetState = lipanaSheetState
        ) {
            LipanaStkBottomSheet(
                amountDueKes = stk.amountDueKes,
                phone = stk.phone,
                onFallbackToManual = {
                    val ready = state as? PaymentsViewModel.UiState.Ready
                    vm.fallbackToManualMpesa(ready?.mpesaTillNumber ?: "5530500")
                },
                onCancel = { vm.resetAction() }
            )
        }
    }

    // Done — celebratory confirmation overlay.
    (action as? PaymentsViewModel.ActionState.Done)?.let { done ->
        ModalBottomSheet(
            onDismissRequest = { vm.resetAction(); onClose() },
            sheetState = confirmationSheetState
        ) {
            PaymentConfirmationOverlay(
                message = done.message,
                onDone = { vm.resetAction(); onClose() }
            )
        }
    }
}

@Composable
private fun SummaryCard(amountKesGross: Long, creditKes: Long) {
    val creditApplied = max(0L, kotlin.math.min(amountKesGross, creditKes))
    val net = amountKesGross - creditApplied
    InkCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryRow("Invoice total", "KES " + formatThousands(amountKesGross), dark = true)
            if (creditApplied > 0) {
                SummaryRow("Credit applied", "− KES " + formatThousands(creditApplied), dark = true, accent = true)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "AMOUNT DUE",
                color = Brand.cream.copy(alpha = 0.6f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp
            )
            Text(
                "KES " + formatThousands(net),
                color = Brand.cream,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, dark: Boolean, accent: Boolean = false) {
    val labelColor = if (dark) Brand.cream.copy(alpha = 0.7f) else Brand.ink.copy(alpha = 0.7f)
    val valueColor = when {
        accent -> Brand.Orange
        dark -> Brand.cream
        else -> Brand.ink
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = labelColor, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text(value, color = valueColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun MethodCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    disabled: Boolean = false
) {
    val alpha = if (disabled) 0.55f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = 0.78f * alpha), RoundedCornerShape(22.dp))
            .border(1.dp, Brand.ink.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Brand.ink, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Brand.cream)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Brand.ink, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Brand.ink.copy(alpha = 0.65f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun MpesaSubmitBottomSheet(
    paybill: String,
    account: String,
    amountDueKes: Long,
    onCancel: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var sms by remember { mutableStateOf("") }
    val canSubmit = sms.trim().length >= 20

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EditorialHeader(
            eyebrow = "M-Pesa",
            title = "Pay then paste the SMS",
            subtitle = "Open M-Pesa → Lipa na M-Pesa → Buy Goods and Services."
        )
        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow("Till number", paybill)
                StatRow("Reference", account)
                StatRow("Amount", "KES " + formatThousands(amountDueKes))
            }
        }
        OutlinedTextField(
            value = sms,
            onValueChange = { sms = it },
            label = { Text("M-Pesa confirmation SMS") },
            placeholder = { Text("Paste the full SMS Safaricom sent you…") },
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        OrangeButton(
            text = "Submit for review",
            enabled = canSubmit,
            onClick = { onSubmit(sms.trim()) }
        )
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
private fun LipanaStkBottomSheet(
    amountDueKes: Long,
    phone: String?,
    onFallbackToManual: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EditorialHeader(
            eyebrow = "M-Pesa STK",
            title = "Check your phone",
            subtitle = "We sent a PIN prompt to ${phone ?: "your phone"}. Enter your M-Pesa PIN to complete."
        )
        InkCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "KES " + formatThousands(amountDueKes),
                    color = Brand.cream,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp
                )
                Text(
                    "Awaiting M-Pesa PIN…",
                    color = Brand.Orange,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                CircularProgressIndicator(color = Brand.cream)
            }
        }
        Button(
            onClick = onFallbackToManual,
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand.cream,
                contentColor = Brand.ink
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Pay manually instead")
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
private fun PaymentConfirmationOverlay(message: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Brand.Orange.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Brand.Orange,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            "Thank you",
            color = Brand.ink,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            message,
            color = Brand.ink.copy(alpha = 0.75f),
            fontSize = 14.sp
        )
        OrangeButton(text = "Done", onClick = onDone)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            color = Brand.cream.copy(alpha = 0.6f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            color = Brand.cream,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatThousands(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder()
    val start = if (n < 0) 1 else 0
    val digits = s.substring(start)
    digits.reversed().forEachIndexed { i, c ->
        if (i > 0 && i % 3 == 0) sb.append(',')
        sb.append(c)
    }
    if (n < 0) sb.append('-')
    return sb.reverse().toString()
}
