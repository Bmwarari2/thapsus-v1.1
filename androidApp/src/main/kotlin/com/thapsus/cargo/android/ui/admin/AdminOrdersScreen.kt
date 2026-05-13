package com.thapsus.cargo.android.ui.admin

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.AdminOrderRow
import com.thapsus.cargo.presentation.AdminOrdersViewModel

/**
 * Admin orders list. Mirrors iOS AdminOrdersView. Paginated via
 * AdminOrdersViewModel.loadMore + status filter chip row. Each row
 * navigates to AdminOrderDetailScreen.
 */
@Composable
fun AdminOrdersScreen(onBack: () -> Unit, onOpenOrder: (String) -> Unit) {
    val vm = remember { ThapsusSdk.adminOrdersViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.load() }

    val state by vm.state.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()

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
            title = "Orders",
            subtitle = "Every customer order, filterable + paginated."
        )

        FilterChipsRow(current = filters.status, onChange = { vm.setStatus(it) })

        when (val s = state) {
            AdminOrdersViewModel.UiState.Loading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
            is AdminOrdersViewModel.UiState.Error -> CalloutBanner(title = "Couldn't load", message = s.message)
            is AdminOrdersViewModel.UiState.Loaded -> {
                if (s.orders.isEmpty()) {
                    SoftCard { Text("No orders match this filter.", color = Brand.ink.copy(alpha = 0.7f)) }
                } else {
                    s.orders.forEach { row ->
                        OrderRow(row = row, onClick = { onOpenOrder(row.id) })
                    }
                    if (s.hasMore) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !s.loadingMore) { vm.loadMore() }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (s.loadingMore) "Loading…" else "Load more (${s.orders.size}/${s.total})",
                                color = Brand.Orange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FilterChipsRow(current: String?, onChange: (String?) -> Unit) {
    val options = listOf(
        null to "All",
        "pending" to "Pending",
        "paid" to "Paid",
        "shipped" to "Shipped",
        "delivered" to "Delivered",
        "cancelled" to "Cancelled"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            val selected = current == value
            Box(
                modifier = Modifier
                    .background(
                        if (selected) Brand.ink else Brand.cream.copy(alpha = 0.78f),
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { onChange(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    color = if (selected) Brand.cream else Brand.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OrderRow(row: AdminOrderRow, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.customerName ?: row.customerEmail ?: "Customer",
                    color = Brand.ink,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(row.status)
            }
            row.trackingNumber?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Brand.Orange, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            row.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Brand.ink.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.retailer?.let {
                    Text(it, color = Brand.ink.copy(alpha = 0.6f), fontSize = 11.sp)
                }
                row.actualCost?.let { amt ->
                    Text(
                        "£%.2f".format(amt),
                        color = Brand.ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                row.createdAt?.take(10)?.let {
                    Text(it, color = Brand.ink.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) {
        "delivered", "paid" -> Color(0xFF2E7D32)
        "shipped", "in_transit" -> Color(0xFF1976D2)
        "pending", "pending_payment" -> Color(0xFFE08B00)
        "cancelled", "rejected" -> Color(0xFFB3261E)
        else -> Color(0xFF707070)
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
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
