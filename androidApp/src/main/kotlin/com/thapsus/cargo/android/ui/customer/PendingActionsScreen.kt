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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.CustomerConsolidationDto
import com.thapsus.cargo.data.repository.AuthSession
import kotlinx.coroutines.flow.collectLatest

/**
 * Unified pending-actions list. Surfaces every unpaid item the customer
 * has across all three "invoice" surfaces:
 *
 *   - Buy-for-me quotes (`status='quoted'`) — pre-accept, the customer
 *     was billed but hasn't approved yet.
 *   - Buy-for-me pending payments (`PaymentDto.status='pending'` +
 *     `target_kind='buy_for_me'`) — post-accept, mid-payment.
 *   - Shipping/consolidation invoices (`status='invoiced'`).
 *
 * The home page's `BfmInvoicesSection` + `ActiveInvoicesSection` collapse
 * into a single "Pending actions" summary card once the total crosses 1;
 * tapping it brings the customer here. Card styling matches the home
 * surfaces 1:1 so the carry-over is seamless.
 */
@Composable
fun PendingActionsScreen(
    session: AuthSession.Authenticated,
    onBack: () -> Unit,
    onPayConsolidation: (CustomerConsolidationDto) -> Unit,
    onPayBfmQuote: (com.thapsus.cargo.data.dto.BuyForMeOrderDto) -> Unit,
    onPayBfmInvoice: (com.thapsus.cargo.data.dto.PaymentDto) -> Unit
) {
    val dashVm = remember(session.userId) {
        ThapsusSdk.customerDashboardViewModel(session.userId)
    }
    DisposableEffect(dashVm) { onDispose { dashVm.clear() } }

    // Consolidation invoices come from the customer-consolidations repo
    // with a Supabase-realtime observe — mirrors the same pattern
    // HomeScreen uses so a status flip from `invoiced` → `paid` lands
    // here without a refresh.
    val invoicesRepo = remember { ThapsusSdk.customerConsolidations() }
    var consolidations by remember(session.userId) {
        mutableStateOf<List<CustomerConsolidationDto>>(emptyList())
    }
    LaunchedEffect(session.userId) {
        runCatching { invoicesRepo.fetchForUser(session.userId) }
            .onSuccess { consolidations = it }
        invoicesRepo.observeForUser(session.userId).collectLatest { updated ->
            consolidations = consolidations.toMutableList().also { list ->
                val idx = list.indexOfFirst { it.id == updated.id }
                if (idx >= 0) list[idx] = updated else list.add(0, updated)
            }
        }
    }
    val activeInvoices = remember(consolidations) {
        consolidations
            .filter { it.status == "invoiced" }
            .sortedByDescending { it.createdAt ?: "" }
    }
    val bfmPending by dashVm.bfmPendingInvoices.collectAsStateWithLifecycle()
    val bfmQuoted by dashVm.quotedBfmOrders.collectAsStateWithLifecycle()
    val total = activeInvoices.size + bfmPending.size + bfmQuoted.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand.ink)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Pending actions",
            color = Brand.ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (total == 0) "You're all caught up — nothing to settle right now."
            else "You have $total invoice${if (total == 1) "" else "s"} to settle.",
            color = Brand.ink.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(20.dp))

        if (bfmQuoted.isNotEmpty() || bfmPending.isNotEmpty()) {
            val bfmTotal = bfmQuoted.size + bfmPending.size
            EyebrowPill(
                label = if (bfmTotal == 1) "Buy-for-me invoice due"
                else "$bfmTotal buy-for-me invoices due",
                icon = Icons.AutoMirrored.Filled.ReceiptLong
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bfmQuoted.forEach { order ->
                    BfmQuotedInvoiceCard(order = order) { onPayBfmQuote(order) }
                }
                bfmPending.forEach { payment ->
                    BfmPendingInvoiceCard(payment = payment) { onPayBfmInvoice(payment) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (activeInvoices.isNotEmpty()) {
            EyebrowPill(
                label = if (activeInvoices.size == 1) "Shipping invoice due"
                else "${activeInvoices.size} shipping invoices due",
                icon = Icons.AutoMirrored.Filled.ReceiptLong
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                activeInvoices.forEach { c ->
                    ActiveInvoiceCard(consolidation = c) { onPayConsolidation(c) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (total == 0) {
            // Tiny empty-state — the carousel + home cards already explain
            // there's nothing pending, so we keep this minimal.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Brand.peach, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = Brand.Orange
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}
