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
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.AdminOrderDetailDto
import com.thapsus.cargo.data.dto.OrderCostLine
import com.thapsus.cargo.presentation.AdminOrderDetailViewModel

/**
 * Admin order detail. Mirrors iOS AdminOrderDetailView. Read-only view
 * with the live cost_breakdown the server recomputes from current
 * pricing; packages list at the bottom.
 */
@Composable
fun AdminOrderDetailScreen(orderId: String, onBack: () -> Unit) {
    val vm = remember(orderId) { ThapsusSdk.adminOrderDetailViewModel(orderId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.load() }
    val state by vm.state.collectAsStateWithLifecycle()

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

        when (val s = state) {
            AdminOrderDetailViewModel.UiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Brand.ink)
            }
            is AdminOrderDetailViewModel.UiState.Error -> CalloutBanner(
                title = "Couldn't load",
                message = s.message
            )
            is AdminOrderDetailViewModel.UiState.Loaded -> OrderBody(order = s.order)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun OrderBody(order: AdminOrderDetailDto) {
    EditorialHeader(
        title = order.description?.takeIf { it.isNotBlank() } ?: "Order ${order.id.take(8)}",
        subtitle = order.retailer?.let { "$it · ${order.market ?: "—"}" } ?: order.market
    )

    InkCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "STATUS",
                    color = Brand.cream.copy(alpha = 0.6f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(order.status)
            }
            order.trackingNumber?.let {
                Text(it, color = Brand.Orange, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
            order.actualCost?.let { amt ->
                Text(
                    "£%.2f".format(amt),
                    color = Brand.cream,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp
                )
            }
        }
    }

    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Order details", color = Brand.ink, fontWeight = FontWeight.SemiBold)
            DetailRow("Tracking", order.trackingNumber ?: "—")
            DetailRow("Retailer", order.retailer ?: "—")
            DetailRow("Market", order.market ?: "—")
            DetailRow("Weight", order.weightKg?.let { "%.2f kg".format(it) } ?: "—")
            DetailRow("Speed", order.shippingSpeed ?: "—")
            DetailRow("Declared", order.declaredValue?.let { "£%.2f".format(it) } ?: "—")
            DetailRow("Created", order.createdAt?.take(10) ?: "—")
            order.orderNotes?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Brand.ink.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
    }

    val cost = order.costBreakdown
    if (cost != null) {
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cost breakdown", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                CostRow(line = cost.breakdown.baseShipping, fallbackLabel = "Base shipping")
                CostRow(line = cost.breakdown.handlingFee, fallbackLabel = "Handling fee")
                CostRow(line = cost.breakdown.electronicsHandling, fallbackLabel = "Electronics handling")
                CostRow(line = cost.breakdown.insurance, fallbackLabel = "Insurance")
                CostRow(line = cost.breakdown.customsEstimate, fallbackLabel = "Customs estimate")
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "TOTAL",
                        color = Brand.ink.copy(alpha = 0.6f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "£%.2f".format(cost.total),
                        color = Brand.ink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    if (order.packages.isNotEmpty()) {
        Text("Packages", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        order.packages.forEach { p ->
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        p.description ?: "Parcel ${p.id.take(8)}",
                        color = Brand.ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        p.status?.let {
                            Text(
                                it.replace('_', ' ').uppercase(),
                                color = Brand.Orange,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 1.5.sp
                            )
                        }
                        p.weightKg?.let {
                            Text(
                                "%.2f kg".format(it),
                                color = Brand.ink.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                        p.warehouseLocation?.let {
                            Text(
                                it,
                                color = Brand.ink.copy(alpha = 0.55f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Brand.ink.copy(alpha = 0.65f), modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text(value, color = Brand.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun CostRow(line: OrderCostLine?, fallbackLabel: String) {
    if (line == null || line.amount == 0.0) return
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            line.label ?: fallbackLabel,
            color = Brand.ink.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
            fontSize = 13.sp
        )
        Text(
            "£%.2f".format(line.amount),
            color = Brand.ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
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
            fontSize = 10.sp,
            letterSpacing = 1.5.sp
        )
    }
}
