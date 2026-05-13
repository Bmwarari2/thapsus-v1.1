package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand

/**
 * Credit centre — balance + ledger. Replaces the legacy WalletScreen
 * surface (mig-028 removed wallet/deposits/withdrawals; credit now
 * lives in user_credits and ledger in credit_ledger). Mirrors iOS
 * CreditCenterView.
 */
@Composable
fun CreditCenterScreen() {
    val txVm = remember { ThapsusSdk.transactionsViewModel() }
    val payVm = remember { ThapsusSdk.paymentsViewModel() }
    DisposableEffect(txVm, payVm) { onDispose { txVm.clear(); payVm.clear() } }

    LaunchedEffect(txVm) {
        txVm.loadCredit(reset = true)
        payVm.bootstrap()
    }

    val creditState by txVm.creditState.collectAsStateWithLifecycle()
    val payState by payVm.state.collectAsStateWithLifecycle()
    val balanceKes = (payState as? com.thapsus.cargo.presentation.PaymentsViewModel.UiState.Ready)?.creditBalanceKes ?: 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Account",
            title = "Credit centre",
            subtitle = "Your top-up balance + every credit movement."
        )

        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "AVAILABLE CREDIT",
                    color = Brand.cream.copy(alpha = 0.6f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                )
                Text(
                    "KES " + formatThousandsLong(balanceKes),
                    color = Brand.cream,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp
                )
                Text(
                    "Credit is automatically applied to any new invoice you settle.",
                    color = Brand.cream.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        Text("Ledger", color = Brand.ink, style = MaterialTheme.typography.titleLarge)

        if (creditState.loading && creditState.items.isEmpty()) {
            CircularProgressIndicator(color = Brand.ink)
        }

        if (creditState.items.isEmpty() && !creditState.loading) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No credit activity yet", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Credits will appear here when admin issues a refund or you overpay an invoice.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
            }
        }

        creditState.items.forEach { entry ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.reason.replace('_', ' ').replaceFirstChar { it.uppercase() },
                            color = Brand.ink,
                            fontWeight = FontWeight.SemiBold
                        )
                        entry.createdAt?.take(10)?.let {
                            Text(it, color = Brand.ink.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                    }
                    val positive = entry.deltaKes > 0
                    Text(
                        (if (positive) "+ " else "− ") + "KES " + formatThousandsLong(kotlin.math.abs(entry.deltaKes)),
                        color = if (positive) Color(0xFF2E7D32) else Color(0xFFB3261E),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

internal fun formatThousandsLong(n: Long): String {
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
