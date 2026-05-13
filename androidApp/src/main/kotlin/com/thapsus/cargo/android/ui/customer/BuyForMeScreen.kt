package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.BuyForMeViewModel

/**
 * "Shop" tab — concierge-purchase entry point. P1.1 ships the route +
 * skeleton so the new BFM-primary tab bar lands intact. P2.1 fleshes
 * out the full lifecycle (retailer marquee, create sheet, accept/reject,
 * in-app browser).
 */
@Composable
fun BuyForMeScreen() {
    val vm = remember { ThapsusSdk.buyForMeViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(vm) { vm.load() }
    DisposableEffect(vm) { onDispose { /* VM has no clear() yet */ } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EyebrowPill(label = "Concierge", icon = Icons.Filled.AutoAwesome)
        EditorialHeader(
            title = "Buy for me",
            subtitle = "Paste a UK retailer link, we buy and ship."
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "How it works",
                    style = MaterialTheme.typography.titleMedium,
                    color = Brand.ink
                )
                Text(
                    "1. Send us a retailer link\n2. We quote you in GBP\n3. Pay by card or M-Pesa\n4. We buy and ship to Kenya",
                    color = Brand.ink.copy(alpha = 0.78f)
                )
                OrangeButton(
                    text = "New request",
                    onClick = { /* P2.1 wires the create sheet */ }
                )
            }
        }

        when (val s = state) {
            BuyForMeViewModel.UiState.Idle,
            BuyForMeViewModel.UiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Brand.ink)
                }
            }
            is BuyForMeViewModel.UiState.Error -> {
                SoftCard(tint = Brand.peach) {
                    Text(s.message, color = Brand.ink)
                }
            }
            is BuyForMeViewModel.UiState.Loaded -> {
                if (s.orders.isEmpty()) {
                    SoftCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "No requests yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = Brand.ink
                            )
                            Text(
                                "Start your first concierge order — tap New request above.",
                                color = Brand.ink.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Text(
                        "${s.orders.size} request${if (s.orders.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Brand.ink
                    )
                    s.orders.forEach { order ->
                        SoftCard {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    order.itemName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Brand.ink
                                )
                                Text(
                                    order.status,
                                    color = Brand.Orange
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
