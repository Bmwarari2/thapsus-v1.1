package com.thapsus.cargo.android.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.OpsSettingsViewModel

@Composable
fun OpsSettingsScreen() {
    val vm = remember { ThapsusSdk.opsSettingsViewModel() }
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
        EyebrowPill(label = "Operations")
        EditorialHeader(title = "Settings", subtitle = "Pricing, exchange rates and surcharges.")

        when (val s = state) {
            is OpsSettingsViewModel.UiState.Loaded -> {
                Text("Exchange rates", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
                val edits = remember(s.rates) {
                    mutableStateMapOf<String, String>().apply {
                        s.rates.forEach { put(it.currencyPair, it.rate.toString()) }
                    }
                }
                SoftCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        s.rates.forEach { rate ->
                            OutlinedTextField(
                                value = edits[rate.currencyPair] ?: "",
                                onValueChange = { edits[rate.currencyPair] = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text(rate.currencyPair) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        InkButton(
                            text = "Save all rates",
                            onClick = {
                                val map = edits.mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                                vm.setRates(map)
                            }
                        )
                    }
                }

                Text("Fees", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
                if (s.fees.isEmpty()) {
                    SoftCard { Text("No fees configured.", color = Brand.ink.copy(alpha = 0.7f)) }
                } else {
                    s.fees.forEach { fee ->
                        SoftCard {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fee.label, color = Brand.ink, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${fee.code} · ${if (fee.isPercentage) "${fee.amount}%" else "£${fee.amount}"}",
                                        color = Brand.ink.copy(alpha = 0.6f)
                                    )
                                }
                                Text(
                                    if (fee.isActive) "ON" else "OFF",
                                    color = if (fee.isActive) Brand.Orange else Brand.ink.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Text("Pricing tiers", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
                if (s.tiers.isEmpty()) {
                    SoftCard { Text("No tiers seeded.", color = Brand.ink.copy(alpha = 0.7f)) }
                } else {
                    s.tiers.forEach { tier ->
                        SoftCard {
                            Column {
                                Text(
                                    "${tier.channel.name} · ${tier.minKg}–${tier.maxKg} kg",
                                    color = Brand.ink,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "£%.2f / kg".format(tier.gbpPerKg),
                                    color = Brand.Orange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
            is OpsSettingsViewModel.UiState.Error -> SoftCard {
                Text("Couldn't load settings: ${s.message}", color = Brand.ink)
            }
            else -> SoftCard { Text("Loading…", color = Brand.ink) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
