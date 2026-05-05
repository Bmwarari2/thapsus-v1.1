package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.SyncAlt
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.PaymentMethod
import com.thapsus.cargo.data.dto.TransactionDto
import com.thapsus.cargo.data.dto.TransactionStatus
import com.thapsus.cargo.data.dto.TransactionType
import com.thapsus.cargo.presentation.DepositStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(userId: String) {
    val vm = remember(userId) { ThapsusSdk.walletViewModel(userId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }

    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    var showDeposit by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Your account",
            title = "Wallet",
            subtitle = "Top up once, ship many. Balance moves live with the web app."
        )

        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AVAILABLE",
                        color = Brand.cream.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF2E7D32), RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("LIVE", color = Brand.cream, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }
                Text(
                    "%s %.2f".format(wallet?.currency ?: "KES", wallet?.balance ?: 0.0),
                    color = Brand.cream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp
                )
            }
        }

        InkButton(text = "Confirm M-Pesa deposit", onClick = { showDeposit = true })

        when (val s = status) {
            is DepositStatus.Sent -> CalloutBanner(
                title = "Deposit submitted",
                message = s.reference?.let { "Reference $it. Admins will verify shortly." }
                    ?: "Admins will verify shortly."
            )
            is DepositStatus.Error -> CalloutBanner(
                title = "Couldn't submit deposit",
                message = s.message
            )
            is DepositStatus.Submitting -> SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Brand.ink)
                    Spacer(Modifier.width(12.dp))
                    Text("Sending deposit…", color = Brand.ink)
                }
            }
            else -> Unit
        }

        Text("Recent activity", color = Brand.ink, style = MaterialTheme.typography.titleLarge)

        if (txs.isEmpty()) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No transactions yet", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Top-ups, payments and refunds will show up here in real time.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            txs.forEach { TransactionRow(it) }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showDeposit) {
        ModalBottomSheet(
            onDismissRequest = { showDeposit = false },
            sheetState = sheetState,
            containerColor = Brand.cream
        ) {
            DepositSheetBody { amount, sms ->
                showDeposit = false
                vm.confirmMpesaDeposit(amount, sms, null)
            }
        }
    }
}

@Composable
private fun DepositSheetBody(onSubmit: (Double, String) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var sms by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val canSubmit = amount > 0 && sms.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Add funds",
            title = "Confirm M-Pesa deposit",
            subtitle = "Pay our Paybill, then paste the M-Pesa confirmation SMS below. Admins verify and credit your wallet."
        )
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Amount (KES)", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("M-Pesa confirmation SMS", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = sms,
                    onValueChange = { sms = it },
                    placeholder = { Text("e.g. ABC123 Confirmed. KES 1,500.00 received…") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        InkButton(
            text = "Submit deposit",
            enabled = canSubmit,
            onClick = { onSubmit(amount, sms) }
        )
    }
}

@Composable
private fun TransactionRow(tx: TransactionDto) {
    val (icon, badge, color) = decorateTransaction(tx)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = 0.78f), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(badge, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Brand.cream)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(typeLabel(tx.type), color = Brand.ink, fontWeight = FontWeight.SemiBold)
            Text(
                "${methodLabel(tx.paymentMethod)} · ${statusLabel(tx.status)}",
                color = Brand.ink.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        val sign = if (tx.type == TransactionType.PAYMENT) "−" else "+"
        Text(
            "$sign %.2f %s".format(tx.amount, tx.currency),
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun decorateTransaction(tx: TransactionDto): Triple<ImageVector, Color, Color> = when (tx.type) {
    TransactionType.DEPOSIT -> Triple(Icons.Filled.ArrowDownward, Color(0xFF2E7D32), Color(0xFF2E7D32))
    TransactionType.PAYMENT -> Triple(Icons.Filled.CreditCard, Brand.Orange, Brand.Orange)
    TransactionType.REFUND -> Triple(Icons.Filled.SyncAlt, Color(0xFF2E7D32), Color(0xFF2E7D32))
    TransactionType.REFERRAL_CREDIT -> Triple(Icons.Filled.CardGiftcard, Color(0xFF2E7D32), Color(0xFF2E7D32))
}

private fun typeLabel(t: TransactionType) = when (t) {
    TransactionType.DEPOSIT -> "Top-up"
    TransactionType.PAYMENT -> "Payment"
    TransactionType.REFUND -> "Refund"
    TransactionType.REFERRAL_CREDIT -> "Referral credit"
}

private fun methodLabel(m: PaymentMethod?) = when (m) {
    PaymentMethod.MPESA -> "M-Pesa"
    PaymentMethod.STRIPE -> "Stripe"
    PaymentMethod.PAYPAL -> "PayPal"
    PaymentMethod.WALLET -> "Wallet"
    null -> "—"
}

private fun statusLabel(s: TransactionStatus) = when (s) {
    TransactionStatus.PENDING -> "Pending"
    TransactionStatus.COMPLETED -> "Completed"
    TransactionStatus.FAILED -> "Failed"
}
