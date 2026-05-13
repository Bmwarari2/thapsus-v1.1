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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.InsuranceTier
import com.thapsus.cargo.data.dto.PricingChannel
import com.thapsus.cargo.domain.model.ParcelDimensions
import com.thapsus.cargo.domain.pricing.QuoteEngine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

private const val PHONE_SURCHARGE_GBP = 75.0
private const val LAPTOP_SURCHARGE_GBP = 65.0

/**
 * Customer-facing shipping calculator. Pulls the same QuoteViewModel
 * iOS uses so totals match — and per the BFM-primary pivot mirror:
 *   - server-side QuoteEngine + per-quote /exchange/rates refresh,
 *   - sum-stable line→total (each line rounded to whole KES first,
 *     total is the sum of those rounded values),
 *   - no customs row in the breakdown,
 *   - VAT + Duty footer warning.
 */
@Composable
fun QuoteScreen() {
    val vm = remember { ThapsusSdk.quoteViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.loadPricing() }

    val quote by vm.quote.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val gbpToKes by vm.gbpToKes.collectAsStateWithLifecycle()

    var weightText by remember { mutableStateOf("2.00") }
    var lengthText by remember { mutableStateOf("30") }
    var widthText by remember { mutableStateOf("20") }
    var heightText by remember { mutableStateOf("20") }
    var includesPhone by remember { mutableStateOf(false) }
    var includesLaptop by remember { mutableStateOf(false) }

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
            subtitle = "Same engine that prices your final shipment."
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Parcel dimensions", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                NumberField(label = "Weight (kg)", value = weightText, allowDecimal = true) { weightText = it }
                NumberField(label = "Length (cm)", value = lengthText) { lengthText = it }
                NumberField(label = "Width (cm)", value = widthText) { widthText = it }
                NumberField(label = "Height (cm)", value = heightText) { heightText = it }
            }
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Surcharges", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                SurchargeRow(
                    title = "Phone surcharge",
                    subtitle = "Lithium battery handling · £75",
                    isOn = includesPhone,
                    onChange = { includesPhone = it }
                )
                SurchargeRow(
                    title = "Laptop surcharge",
                    subtitle = "Lithium battery handling · £65",
                    isOn = includesLaptop,
                    onChange = { includesLaptop = it }
                )
            }
        }

        InkButton(
            text = "Calculate",
            onClick = {
                val dims = ParcelDimensions(
                    lengthCm = clamp(parse(lengthText), 1.0, 300.0, fallback = 30.0),
                    widthCm = clamp(parse(widthText), 1.0, 300.0, fallback = 20.0),
                    heightCm = clamp(parse(heightText), 1.0, 300.0, fallback = 20.0),
                    actualKg = clamp(parse(weightText), 0.1, 1000.0, fallback = 2.0)
                )
                // skipCustoms=true mirrors iOS — customer-facing calculator
                // omits the customs estimate row; the footer warns that
                // VAT + Duty may apply on KRA clearance.
                vm.computeQuote(
                    dims = dims,
                    channel = PricingChannel.UK_AIR,
                    insurance = InsuranceTier.STANDARD,
                    declaredValuePence = 0L,
                    skipCustoms = true
                )
            }
        )

        quote?.let { q ->
            QuoteCard(
                quote = q,
                gbpToKes = gbpToKes,
                includesPhone = includesPhone,
                includesLaptop = includesLaptop
            )
        }

        error?.let { msg ->
            CalloutBanner(
                title = "Couldn't price",
                message = msg
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuoteCard(
    quote: QuoteEngine.Quote,
    gbpToKes: Double?,
    includesPhone: Boolean,
    includesLaptop: Boolean
) {
    // Sum-stable total. Convert each visible GBP line to whole KES first,
    // then sum — so what the customer sees on screen actually adds up.
    // Without this, `round(total * rate)` is off-by-1 vs the line sum
    // whenever the GBP fractional × rate fractional combine unevenly.
    val phoneGbp = if (includesPhone) PHONE_SURCHARGE_GBP else 0.0
    val laptopGbp = if (includesLaptop) LAPTOP_SURCHARGE_GBP else 0.0
    val surchargeGbp = phoneGbp + laptopGbp

    val visibleLinesGbp = buildList {
        add(quote.freight.major)
        if (quote.handling.major > 0) add(quote.handling.major)
        if (includesPhone) add(PHONE_SURCHARGE_GBP)
        if (includesLaptop) add(LAPTOP_SURCHARGE_GBP)
        if (quote.processingFee.major > 0) add(quote.processingFee.major)
    }
    val displayedTotal = if (gbpToKes != null) {
        val sumKes = visibleLinesGbp.sumOf { (it * gbpToKes).roundToLong() }
        "KES " + formatThousands(sumKes)
    } else {
        "£ %.2f".format(quote.total.major + surchargeGbp)
    }

    SoftCard(tint = Brand.peach) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "ESTIMATED TOTAL",
                color = Brand.Orange,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )
            Text(
                displayedTotal,
                color = Brand.ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LineRow("Chargeable weight", "%.2f kg".format(quote.volumetric.chargeableKg))
                LineRow("Base shipping", formatMoney(quote.freight.major, gbpToKes))
                if (quote.handling.major > 0) {
                    LineRow("UK handling", formatMoney(quote.handling.major, gbpToKes))
                }
                if (includesPhone) {
                    LineRow("Phone surcharge", formatMoney(PHONE_SURCHARGE_GBP, gbpToKes), emphasis = true)
                }
                if (includesLaptop) {
                    LineRow("Laptop surcharge", formatMoney(LAPTOP_SURCHARGE_GBP, gbpToKes), emphasis = true)
                }
                if (quote.processingFee.major > 0) {
                    LineRow("Card processing", formatMoney(quote.processingFee.major, gbpToKes))
                }
            }

            Text(
                "Customs (VAT + Duty) may be charged separately by Kenya Revenue Authority on clearance and are not included in this estimate.",
                color = Brand.ink.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LineRow(label: String, value: String, emphasis: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = if (emphasis) Brand.Orange else Brand.ink.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            color = if (emphasis) Brand.Orange else Brand.ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SurchargeRow(
    title: String,
    subtitle: String,
    isOn: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Brand.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Brand.ink.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Switch(
            checked = isOn,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Brand.Orange,
                checkedTrackColor = Brand.Orange.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    allowDecimal: Boolean = false,
    onChange: (String) -> Unit
) {
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

private fun parse(text: String): Double =
    text.replace(',', '.').toDoubleOrNull() ?: 0.0

private fun clamp(value: Double, low: Double, high: Double, fallback: Double): Double =
    if (value <= 0.0) fallback else min(max(value, low), high)

private fun formatMoney(gbp: Double, rate: Double?): String =
    if (rate != null && rate > 0) "KES " + formatThousands((gbp * rate).roundToLong())
    else "£ %.2f".format(gbp)

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
