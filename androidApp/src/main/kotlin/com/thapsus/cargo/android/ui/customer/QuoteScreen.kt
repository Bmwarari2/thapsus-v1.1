package com.thapsus.cargo.android.ui.customer

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand

/**
 * Lightweight Compose port of the iOS QuoteCalculatorView. Uses the volumetric
 * formula directly (length × width × height ÷ 5000) — the full QuoteEngine
 * binding (with surcharges + per-channel pricing) lands in a follow-up PR
 * alongside the public-fees endpoint integration.
 */
@Composable
fun QuoteScreen() {
    var length by remember { mutableStateOf("30") }
    var width by remember { mutableStateOf("20") }
    var height by remember { mutableStateOf("20") }
    var actualKg by remember { mutableStateOf("2.0") }
    var declaredGbp by remember { mutableStateOf("50") }

    val l = length.toDoubleOrNull() ?: 0.0
    val w = width.toDoubleOrNull() ?: 0.0
    val h = height.toDoubleOrNull() ?: 0.0
    val a = actualKg.toDoubleOrNull() ?: 0.0
    val volumetricKg = (l * w * h) / 5000.0
    val chargeable = maxOf(volumetricKg, a)
    // Pricing tier UK air baseline £4.50/kg (matches the seed in 001a — replaced
    // with the live PricingRepository call once the public-fees endpoint is wired).
    val baseline = chargeable * 4.50

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "What will it cost?",
            title = "Shipping\nCalculator",
            subtitle = "Estimate before you buy."
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Parcel dimensions", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                NumberField(label = "Length (cm)", value = length) { length = it }
                NumberField(label = "Width (cm)", value = width) { width = it }
                NumberField(label = "Height (cm)", value = height) { height = it }
                NumberField(label = "Actual weight (kg)", value = actualKg, allowDecimal = true) { actualKg = it }
                NumberField(label = "Declared value (£)", value = declaredGbp, allowDecimal = true) { declaredGbp = it }
            }
        }

        InkButton(text = "Calculate", onClick = { /* recompute is reactive */ })

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estimate", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                BreakdownRow("Volumetric weight", "%.2f kg".format(volumetricKg))
                BreakdownRow("Chargeable weight", "%.2f kg".format(chargeable))
                BreakdownRow("Freight (UK air baseline)", "£ %.2f".format(baseline))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Estimate ≈ £ %.2f".format(baseline),
                    color = Brand.ink,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        CalloutBanner(
            title = "Heads up",
            message = "Final price uses the same QuoteEngine the intake desk uses, including duty + surcharges. This Android calculator is a quick estimate — full breakdown lands in a follow-up."
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumberField(label: String, value: String, allowDecimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            val filtered = v.filter { it.isDigit() || (allowDecimal && it == '.') }
            onChange(filtered)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Brand.ink.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
        Text(value, color = Brand.ink, fontWeight = FontWeight.SemiBold)
    }
}
