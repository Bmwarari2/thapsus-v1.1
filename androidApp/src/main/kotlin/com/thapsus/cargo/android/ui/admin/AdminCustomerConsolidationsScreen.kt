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
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.CustomerConsolidationDto

/**
 * Admin-side customer-consolidations list. Mirrors iOS
 * AdminCustomerConsolidationsView. Read + filter by status; tapping a
 * row drills into the existing operator ConsolidationDetailScreen.
 */
@Composable
fun AdminCustomerConsolidationsScreen(onBack: () -> Unit) {
    val repo = remember { ThapsusSdk.customerConsolidations() }
    var rows by remember { mutableStateOf<List<CustomerConsolidationDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusFilter) {
        loading = true
        error = null
        runCatching { repo.listForAdmin(status = statusFilter) }
            .onSuccess { rows = it; loading = false }
            .onFailure { error = it.message ?: "Couldn't load consolidations"; loading = false }
    }

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
            title = "Customer consolidations",
            subtitle = "Every batch admin has grouped, across all customers."
        )

        FilterChipsRow(current = statusFilter, onChange = { statusFilter = it })

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        }
        error?.let { CalloutBanner(title = "Couldn't load", message = it) }

        if (!loading && rows.isEmpty() && error == null) {
            SoftCard {
                Text(
                    "No consolidations match this filter.",
                    color = Brand.ink.copy(alpha = 0.7f)
                )
            }
        }

        rows.forEach { row -> ConsolidationCard(row) }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FilterChipsRow(current: String?, onChange: (String?) -> Unit) {
    val options = listOf(
        null to "All",
        "pending" to "Pending",
        "invoiced" to "Invoiced",
        "paid" to "Paid",
        "shipped" to "Shipped"
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
private fun ConsolidationCard(c: CustomerConsolidationDto) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.userName ?: c.userEmail ?: "Customer",
                    color = Brand.ink,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(c.status)
            }
            Text(
                c.description ?: if (c.isStandalone) "Standalone invoice" else "Shipping batch",
                color = Brand.ink.copy(alpha = 0.78f),
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                c.parcelCount?.takeIf { it > 0 }?.let {
                    Text(
                        "$it parcel${if (it == 1) "" else "s"}",
                        color = Brand.ink.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                c.invoiceAmount?.let { amt ->
                    Text(
                        "${c.invoiceCurrency} %,.0f".format(amt),
                        color = Brand.Orange,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                c.createdAt?.take(10)?.let {
                    Text(it, color = Brand.ink.copy(alpha = 0.5f), fontSize = 11.sp)
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
