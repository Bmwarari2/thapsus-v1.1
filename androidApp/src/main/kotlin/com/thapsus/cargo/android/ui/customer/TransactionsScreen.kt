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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.PaymentDto

/**
 * Customer-facing payments ledger. Every card / M-Pesa payment for
 * this user, paginated via TransactionsViewModel.loadPayments. Mirrors
 * iOS TransactionsView.
 */
@Composable
fun TransactionsScreen() {
    val vm = remember { ThapsusSdk.transactionsViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.loadPayments(reset = true) }

    val state by vm.paymentsState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Activity",
            title = "Transactions",
            subtitle = "Card + M-Pesa payments + your credit activity."
        )

        if (state.loading && state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        }

        state.error?.let { msg ->
            CalloutBanner(title = "Couldn't load", message = msg)
        }

        if (state.items.isEmpty() && !state.loading && state.error == null) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No payments yet", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Once you pay an invoice via card or M-Pesa, it'll appear here.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
            }
        }

        state.items.forEach { payment ->
            PaymentRow(payment)
        }

        if (!state.done && state.items.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.loadPayments() }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (state.loading) "Loading…" else "Load more",
                    color = Brand.Orange,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PaymentRow(payment: PaymentDto) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    payment.targetLabel ?: targetLabel(payment.targetKind),
                    color = Brand.ink,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        payment.method.uppercase(),
                        color = Brand.ink.copy(alpha = 0.6f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    payment.createdAt?.take(10)?.let {
                        Text(it, color = Brand.ink.copy(alpha = 0.55f), fontSize = 11.sp)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "KES " + formatThousandsLong(payment.amountDueKes),
                    color = Brand.ink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
                StatusChip(payment.status)
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = when (status) {
        "succeeded", "paid" -> Color(0xFF2E7D32)
        "awaiting_review" -> Color(0xFFE08B00)
        "pending" -> Color(0xFF707070)
        "failed", "rejected" -> Color(0xFFB3261E)
        else -> Color(0xFF707070)
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            status.replace('_', ' ').uppercase(),
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )
    }
}

private fun targetLabel(kind: String): String = when (kind) {
    "consolidation" -> "Shipping invoice"
    "buy_for_me" -> "Buy-for-me order"
    "order" -> "Parcel order"
    else -> kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
