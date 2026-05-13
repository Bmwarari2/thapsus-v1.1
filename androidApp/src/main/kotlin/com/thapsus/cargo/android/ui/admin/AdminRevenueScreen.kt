package com.thapsus.cargo.android.ui.admin

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.AdminRevenueResponse
import com.thapsus.cargo.data.dto.AdminRevenueRow
import com.thapsus.cargo.data.dto.AdminRevenueSummaryRow

/**
 * Admin revenue snapshot. Reads `/admin/revenue` directly via
 * AdminRepository; no dedicated VM. Mirrors iOS AdminRevenueView.
 *
 * Two sections:
 *   1. Per-method summary (deposits + payments + total per
 *      payment_method). The roadmap's "paid_via_card / paid_via_mpesa"
 *      requirement lives here.
 *   2. Recent line items (date · method · type · count · total).
 */
@Composable
fun AdminRevenueScreen(onBack: () -> Unit) {
    val adminRepo = remember { ThapsusSdk.adminRepo() }
    var data by remember { mutableStateOf<AdminRevenueResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { adminRepo.revenue() }
            .onSuccess { data = it; loading = false }
            .onFailure { error = it.message ?: "Couldn't load revenue"; loading = false }
    }

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
            title = "Revenue",
            subtitle = "Customer receipts unioned across payments + legacy transactions."
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        }
        error?.let { CalloutBanner(title = "Couldn't load", message = it) }

        val resp = data
        if (resp != null) {
            Text("Per method", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
            resp.summary.forEach { row -> SummaryCard(row) }

            if (resp.revenue.isNotEmpty()) {
                Text("Recent activity", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
                resp.revenue.take(50).forEach { row -> RevenueRow(row) }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SummaryCard(row: AdminRevenueSummaryRow) {
    InkCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                (row.paymentMethod ?: "—").uppercase(),
                color = Brand.cream.copy(alpha = 0.6f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )
            Text(
                "KES " + formatThousands(row.total.toLong()),
                color = Brand.cream,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MicroStat("Deposits", row.deposits)
                MicroStat("Payments", row.payments)
            }
        }
    }
}

@Composable
private fun MicroStat(label: String, amount: Double) {
    Column {
        Text(
            label.uppercase(),
            color = Brand.cream.copy(alpha = 0.55f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            "KES " + formatThousands(amount.toLong()),
            color = Brand.cream,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun RevenueRow(row: AdminRevenueRow) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.date.take(10),
                    color = Brand.ink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.paymentMethod?.let {
                        Text(
                            it.uppercase(),
                            color = Brand.Orange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp
                        )
                    }
                    row.type?.let {
                        Text(
                            it,
                            color = Brand.ink.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "KES " + formatThousands(row.total.toLong()),
                    color = Brand.ink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
                Text(
                    "${row.count} txn",
                    color = Brand.ink.copy(alpha = 0.55f),
                    fontSize = 11.sp
                )
            }
        }
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
