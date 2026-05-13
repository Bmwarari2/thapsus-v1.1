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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * Customer-facing consolidations list — every batch admin has grouped
 * for this customer, across all statuses. Read-only. Tapping a row
 * navigates to invoice payment when invoice_status=invoiced (P2.3).
 *
 * Distinct from CustomerInvoicesScreen, which filters to invoiced/paid
 * only. This shows pending consolidations too so the customer can see
 * what's queued for invoicing.
 */
@Composable
fun CustomerConsolidationsScreen(userId: String) {
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

    val sorted = remember(consolidations) {
        consolidations.sortedByDescending { it.createdAt ?: "" }
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
            title = "Consolidations",
            subtitle = "Every batch of your parcels admin has grouped together."
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        }

        if (!loading && sorted.isEmpty()) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No consolidations yet", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Admin batches your parcels into shipments as they arrive at the UK warehouse. You'll see them here as soon as that happens.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
            }
        }

        sorted.forEach { c -> ConsolidationCard(c) }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ConsolidationCard(c: CustomerConsolidationDto) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.description ?: if (c.isStandalone) "Standalone invoice" else "Shipping batch",
                    color = Brand.ink,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(c.status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                c.parcelCount?.takeIf { it > 0 }?.let {
                    Text(
                        "$it parcel${if (it == 1) "" else "s"}",
                        color = Brand.ink.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }
                c.invoiceAmount?.let { amt ->
                    Text(
                        "${c.invoiceCurrency} %,.0f".format(amt),
                        color = Brand.Orange,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
            c.createdAt?.take(10)?.let {
                Text(it, color = Brand.ink.copy(alpha = 0.5f), fontSize = 11.sp)
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
