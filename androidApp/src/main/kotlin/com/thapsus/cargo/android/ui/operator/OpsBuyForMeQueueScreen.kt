package com.thapsus.cargo.android.ui.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.BigStatTile
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand

/**
 * "BFM" tab — operator concierge-queue surface. P1.2 ships the route +
 * skeleton with live counts pulled from OpsBuyForMeViewModel.orders.
 * P3.1 builds out the full queue list + quote sheet.
 */
@Composable
fun OpsBuyForMeQueueScreen() {
    val vm = remember { ThapsusSdk.opsBuyForMeViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val orders by vm.orders.collectAsStateWithLifecycle()

    val unquoted = orders.count { it.status == "pending_quote" }
    val awaitingPayment = orders.count { it.status == "quoted" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EyebrowPill(label = "Hub operations", icon = Icons.Filled.AutoAwesome)
        EditorialHeader(
            title = "Buy-for-me queue",
            subtitle = "Quote new requests, watch for paid orders to fulfil."
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStatTile(
                eyebrow = "Unquoted",
                value = unquoted.toString(),
                icon = Icons.Filled.AutoAwesome,
                modifier = Modifier.weight(1f)
            )
            BigStatTile(
                eyebrow = "Awaiting payment",
                value = awaitingPayment.toString(),
                icon = Icons.Filled.HourglassEmpty,
                modifier = Modifier.weight(1f)
            )
        }

        if (orders.isEmpty()) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "No requests in queue",
                        style = MaterialTheme.typography.titleMedium,
                        color = Brand.ink
                    )
                    Text(
                        "New concierge requests will appear here live via Realtime.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            Text(
                "${orders.size} request${if (orders.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                color = Brand.ink
            )
            orders.forEach { order ->
                SoftCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            order.itemName,
                            style = MaterialTheme.typography.titleSmall,
                            color = Brand.ink
                        )
                        Text(
                            order.status,
                            color = Brand.Orange,
                            fontWeight = FontWeight.SemiBold
                        )
                        order.retailerUrl.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                color = Brand.ink.copy(alpha = 0.55f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
