package com.thapsus.cargo.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.thapsus.cargo.presentation.AdminDashboardViewModel

@Composable
fun AdminConsoleScreen(
    onOpenUsers: () -> Unit,
    onOpenPayments: () -> Unit,
    onOpenCreateBfm: () -> Unit = {},
    onOpenIssueInvoice: () -> Unit = {},
    onOpenCustomerConsolidations: () -> Unit = {},
    onOpenOrders: () -> Unit = {},
    onOpenRevenue: () -> Unit = {},
    onOpenAuditLogs: () -> Unit = {},
    onOpenErrorLogs: () -> Unit = {},
    onOpenDsarQueue: () -> Unit = {},
    onOpenAmlQueue: () -> Unit = {}
) {
    val vm = remember { ThapsusSdk.adminDashboardViewModel() }
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
        EyebrowPill(label = "Founder console")
        EditorialHeader(
            title = "Console",
            subtitle = "Pending actions across the platform."
        )

        when (val s = state) {
            is AdminDashboardViewModel.UiState.Loaded -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigStatTile(
                        eyebrow = "Pending payments",
                        value = "${s.stats.pendingPayments}",
                        icon = Icons.Filled.Payments,
                        modifier = Modifier.weight(1f)
                    )
                    BigStatTile(
                        eyebrow = "Open AML flags",
                        value = "${s.flags.size}",
                        icon = Icons.Filled.Group,
                        modifier = Modifier.weight(1f).clickable(onClick = onOpenAmlQueue),
                        accent = Color(0xFFD32F2F)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigStatTile(
                        eyebrow = "Total orders",
                        value = "${s.stats.totalOrders}",
                        icon = Icons.Filled.Group,
                        modifier = Modifier.weight(1f)
                    )
                    BigStatTile(
                        eyebrow = "Active orders",
                        value = "${s.stats.activeOrders}",
                        icon = Icons.Filled.Payments,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Customer receipts — gross customer payments received from
                // /admin/stats (Swiftcargo-main#209 unions legacy transactions
                // with the modern payments table). Without this card, the iOS
                // dashboard only surfaced Thapsus margin and customers' card +
                // M-Pesa receipts were invisible — same problem on Android
                // before this row landed.
                ReceiptsCard(
                    totalKes = s.stats.revenueKes,
                    cardKes = s.stats.paidViaCardKes,
                    mpesaKes = s.stats.paidViaMpesaKes,
                    onOpenRevenue = onOpenRevenue
                )
            }
            is AdminDashboardViewModel.UiState.Error -> SoftCard {
                Text("Couldn't load: ${s.message}", color = Brand.ink)
            }
            else -> SoftCard { Text("Loading…", color = Brand.ink) }
        }

        Text("On behalf", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // BFM-primary pivot: "Create Buy-for-me" leads the admin tools
                // group, mirroring the web /admin nav rearrangement.
                LinkRow("Create Buy-for-me", Icons.Filled.AutoAwesome, onOpenCreateBfm)
                Spacer(Modifier.height(1.dp))
                LinkRow("Issue invoice", Icons.Filled.Description, onOpenIssueInvoice)
            }
        }

        Text("Operations", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LinkRow("Customers & operators", Icons.Filled.Group, onOpenUsers)
                Spacer(Modifier.height(1.dp))
                LinkRow("Pending payments", Icons.Filled.Payments, onOpenPayments)
                Spacer(Modifier.height(1.dp))
                LinkRow("Orders", Icons.Filled.ShoppingCart, onOpenOrders)
                Spacer(Modifier.height(1.dp))
                LinkRow("Customer consolidations", Icons.Filled.Inventory2, onOpenCustomerConsolidations)
                Spacer(Modifier.height(1.dp))
                LinkRow("Revenue", Icons.Filled.RequestQuote, onOpenRevenue)
            }
        }

        Text("Compliance & logs", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LinkRow("AML risk queue", Icons.Filled.Shield, onOpenAmlQueue)
                Spacer(Modifier.height(1.dp))
                LinkRow("DSAR queue", Icons.Filled.PrivacyTip, onOpenDsarQueue)
                Spacer(Modifier.height(1.dp))
                LinkRow("Audit logs", Icons.Filled.History, onOpenAuditLogs)
                Spacer(Modifier.height(1.dp))
                LinkRow("Error logs", Icons.Filled.BugReport, onOpenErrorLogs)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReceiptsCard(
    totalKes: Double,
    cardKes: Double,
    mpesaKes: Double,
    onOpenRevenue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.ink, RoundedCornerShape(22.dp))
            .clickable(onClick = onOpenRevenue)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "CUSTOMER RECEIPTS",
                color = Brand.cream.copy(alpha = 0.55f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "KES " + formatThousands(totalKes.toLong()),
                color = Brand.cream,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ReceiptsLine("Card (Stripe)", cardKes)
                ReceiptsLine("M-Pesa", mpesaKes)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Brand.cream.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ReceiptsLine(label: String, amountKes: Double) {
    Column {
        Text(
            label.uppercase(),
            color = Brand.cream.copy(alpha = 0.5f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            "KES " + formatThousands(amountKes.toLong()),
            color = Brand.cream,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

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

@Composable
private fun LinkRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Brand.Orange, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Brand.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Brand.ink.copy(alpha = 0.5f)
        )
    }
}
