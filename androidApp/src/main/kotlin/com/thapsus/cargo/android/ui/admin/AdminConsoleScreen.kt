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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
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
    onOpenIssueInvoice: () -> Unit = {}
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
                        modifier = Modifier.weight(1f),
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
            }
        }
        Spacer(Modifier.height(24.dp))
    }
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
