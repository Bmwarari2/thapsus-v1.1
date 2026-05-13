package com.thapsus.cargo.android.ui.customer

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
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.CustomerConsolidationDto
import kotlinx.coroutines.flow.collectLatest

/**
 * Dedicated invoices archive — every shipping or standalone invoice the
 * customer has, split into Active (status == "invoiced") and Past
 * (status == "paid" || "shipped"). Mirrors iOS CustomerInvoicesView.
 *
 * Reads route through the same Supabase + Realtime path TrackingScreen
 * uses (`customerConsolidations.fetchForUser` + `observeForUser`) so a
 * fresh admin invoice or a status flip lands here without a manual
 * refresh.
 */
@Composable
fun CustomerInvoicesScreen(
    userId: String,
    onPayInvoice: (CustomerConsolidationDto) -> Unit
) {
    val repo = remember { ThapsusSdk.customerConsolidations() }
    var consolidations by remember { mutableStateOf<List<CustomerConsolidationDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        runCatching { repo.fetchForUser(userId) }
            .onSuccess { consolidations = it; loading = false }
            .onFailure { loading = false }
        repo.observeForUser(userId).collectLatest { updated ->
            consolidations = consolidations.toMutableList().also { list ->
                val idx = list.indexOfFirst { it.id == updated.id }
                if (idx >= 0) list[idx] = updated else list.add(0, updated)
            }
        }
    }

    // Issued but unpaid — the customer needs to act on these.
    val active = remember(consolidations) {
        consolidations
            .filter { it.status == "invoiced" }
            .sortedByDescending { it.createdAt ?: "" }
    }
    // Settled — paid or already attached to a shipping batch.
    val past = remember(consolidations) {
        consolidations
            .filter { it.status == "paid" || it.status == "shipped" }
            .sortedByDescending { it.invoicePaidAt ?: it.updatedAt ?: "" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Activity",
            title = "Invoices",
            subtitle = "Active charges to clear, plus everything you've already paid."
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        }

        if (!loading && active.isEmpty() && past.isEmpty()) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "No invoices yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Brand.ink
                    )
                    Text(
                        "Once admin issues an invoice for one of your consolidations or a standalone charge, it'll appear here.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (active.isNotEmpty()) {
            SectionTitle(title = "Active", subtitle = "Pay these to clear your batch for the next outgoing shipment.")
            active.forEach { c ->
                InvoiceCard(consolidation = c, isPast = false, onPay = { onPayInvoice(c) })
            }
        }

        if (past.isNotEmpty()) {
            SectionTitle(title = "Past invoices", subtitle = "Paid + shipped — kept here for your records.")
            past.forEach { c ->
                InvoiceCard(consolidation = c, isPast = true, onPay = {})
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(title, color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = Brand.ink.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

@Composable
private fun InvoiceCard(
    consolidation: CustomerConsolidationDto,
    isPast: Boolean,
    onPay: () -> Unit
) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    consolidation.description ?: (if (consolidation.isStandalone) "Standalone invoice" else "Shipping invoice"),
                    style = MaterialTheme.typography.titleMedium,
                    color = Brand.ink,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(consolidation.status)
            }
            consolidation.invoiceAmount?.let { amount ->
                Text(
                    "${consolidation.invoiceCurrency} %,.0f".format(amount),
                    color = Brand.Orange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
            }
            consolidation.parcelCount?.takeIf { it > 0 }?.let {
                Text(
                    "$it parcel${if (it == 1) "" else "s"} in this batch",
                    color = Brand.ink.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )
            }
            consolidation.invoicePaidAt?.let { paid ->
                if (isPast) {
                    Text(
                        "Paid ${paid.take(10)}",
                        color = Color(0xFF2E7D32),
                        fontSize = 12.sp
                    )
                }
            }
            if (!isPast && consolidation.status == "invoiced") {
                Button(
                    onClick = onPay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brand.Orange,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pay invoice", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) {
        "invoiced" -> Color(0xFFE08B00)
        "paid" -> Color(0xFF2E7D32)
        "shipped" -> Color(0xFF1976D2)
        "pending" -> Color(0xFF707070)
        else -> Color(0xFF707070)
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            status.uppercase(),
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 2.sp
        )
    }
}
