package com.thapsus.cargo.android.ui.customer

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.BigStatTile
import com.thapsus.cargo.android.ui.primitives.BrandWordmark
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.WordmarkSize
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.repository.AuthSession
import com.thapsus.cargo.presentation.WarehouseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DEFAULT_LINES = listOf(
    "31 Collingwood Close",
    "Hazel Grove, Stockport",
    "SK7 4LB",
    "United Kingdom"
)

@Composable
fun HomeScreen(
    session: AuthSession.Authenticated,
    onOpenNewOrder: () -> Unit,
    onOpenParcel: (String) -> Unit,
    onOpenTracking: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val dashVm = remember(session.userId) {
        ThapsusSdk.customerDashboardViewModel(session.userId)
    }
    val warehouseVm = remember { ThapsusSdk.warehouseViewModel() }

    LaunchedEffect(warehouseVm) { warehouseVm.load() }
    DisposableEffect(dashVm) { onDispose { dashVm.clear() } }

    val state by dashVm.state.collectAsStateWithLifecycle()
    val warehouse by warehouseVm.state.collectAsStateWithLifecycle()

    val firstName = remember(session) {
        val name = session.profile?.fullName?.takeIf { it.isNotBlank() }
        name?.split(" ")?.firstOrNull() ?: session.email ?: "there"
    }
    val fullName = session.profile?.fullName?.takeIf { it.isNotBlank() } ?: "Customer"
    val warehouseCode = session.profile?.warehouseId?.takeIf { it.isNotBlank() } ?: "TC-XXXX"
    val lines = (warehouse as? WarehouseViewModel.UiState.Loaded)
        ?.addresses?.get("UK")?.lines ?: DEFAULT_LINES

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandWordmark(size = WordmarkSize.Small)
                Spacer(Modifier.weight(1f))
                NotifBadge(onClick = onOpenNotifications)
            }
            EyebrowPill(label = "Client Terminal")
            EditorialHeader(
                title = "Welcome,\n$firstName",
                subtitle = "Your global logistics overview and active shipments pipeline."
            )

            WarehouseCard(name = fullName, code = warehouseCode, lines = lines)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStatTile(
                    eyebrow = "Active orders",
                    value = state.totalParcels.toString(),
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f)
                )
                BigStatTile(
                    eyebrow = "In flight",
                    value = state.inFlightParcels.toString(),
                    icon = Icons.Filled.AirplanemodeActive,
                    modifier = Modifier.weight(1f),
                    accent = Color(0xFF2196F3)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionRow(
                    label = "VIEW MY PACKAGES",
                    icon = Icons.Filled.Inventory2,
                    background = Brand.ink,
                    foreground = Brand.cream,
                    onClick = onOpenTracking
                )
                ActionRow(
                    label = "WALLET & TOP-UP",
                    icon = Icons.Filled.CreditCard,
                    background = Brand.Orange,
                    foreground = Color.White,
                    onClick = onOpenWallet
                )
            }

            if (state.recentParcels.isNotEmpty()) {
                Text(
                    "Recent",
                    color = Brand.ink,
                    style = MaterialTheme.typography.titleLarge
                )
                state.recentParcels.take(5).forEach { p ->
                    ParcelTile(
                        title = p.description ?: p.retailer ?: "Parcel",
                        subtitle = p.trackingNumber ?: p.id.take(8),
                        onClick = { onOpenParcel(p.id) }
                    )
                }
            }

            Spacer(Modifier.height(72.dp))
        }
        FloatingActionButton(
            onClick = onOpenNewOrder,
            containerColor = Brand.Orange,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(60.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New order")
        }
    }
}

@Composable
private fun WarehouseCard(name: String, code: String, lines: List<String>) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    InkCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Brand.Orange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "YOUR WAREHOUSE ADDRESS",
                    color = Brand.cream,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        name,
                        color = Brand.Orange,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        code,
                        color = Brand.Orange,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    lines.forEach { line ->
                        Text(
                            line,
                            color = Brand.cream.copy(alpha = 0.85f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            OrangeButton(
                text = if (copied) "Copied!" else "Copy address",
                onClick = {
                    clipboard.setText(AnnotatedString((listOf(name, code) + lines).joinToString("\n")))
                    copied = true
                    scope.launch {
                        delay(1800)
                        copied = false
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector,
    background: Color,
    foreground: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = foreground,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.ArrowOutward, contentDescription = null, tint = foreground.copy(alpha = 0.7f))
    }
}

@Composable
private fun ParcelTile(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = 0.78f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Brand.ink, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Brand.cream)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Brand.ink, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Brand.Orange, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NotifBadge(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Brand.cream.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = Brand.ink)
    }
}
