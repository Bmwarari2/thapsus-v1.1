package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
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
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.primitives.WordmarkSize
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.CustomerConsolidationDto
import com.thapsus.cargo.data.repository.AuthSession
import com.thapsus.cargo.presentation.WarehouseViewModel
import com.thapsus.cargo.presentation.home.HomeGreetingDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val DEFAULT_LINES = listOf(
    "31 Collingwood Close",
    "Hazel Grove, Stockport",
    "SK7 4LB",
    "United Kingdom"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: AuthSession.Authenticated,
    onOpenBuyForMe: () -> Unit,
    onOpenPreRegister: () -> Unit,
    onOpenParcel: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onPayInvoice: (CustomerConsolidationDto) -> Unit,
    onGreetingTap: (HomeGreetingDestination) -> Unit
) {
    // Carousel taps whose destination is `NpsSurvey` flip this; everything
    // else bubbles up to the scaffold via [onGreetingTap]. NPS lives as a
    // bottom-sheet (not a nav push) on both platforms.
    var npsSheetVisible by remember { mutableStateOf(false) }
    val npsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dashVm = remember(session.userId) {
        ThapsusSdk.customerDashboardViewModel(session.userId)
    }
    val warehouseVm = remember { ThapsusSdk.warehouseViewModel() }
    val invoicesRepo = remember { ThapsusSdk.customerConsolidations() }

    LaunchedEffect(warehouseVm) { warehouseVm.load() }
    DisposableEffect(dashVm) { onDispose { dashVm.clear() } }

    val state by dashVm.state.collectAsStateWithLifecycle()
    val warehouse by warehouseVm.state.collectAsStateWithLifecycle()

    // Active invoices that the customer needs to clear. Mirrors iOS
    // CustomerDashboardView's `activeInvoicesSection` — same Supabase
    // path CustomerInvoicesScreen uses, so a status flip lands here
    // without a manual refresh.
    var invoices by remember(session.userId) {
        mutableStateOf<List<CustomerConsolidationDto>>(emptyList())
    }
    LaunchedEffect(session.userId) {
        runCatching { invoicesRepo.fetchForUser(session.userId) }
            .onSuccess { invoices = it }
        invoicesRepo.observeForUser(session.userId).collectLatest { updated ->
            invoices = invoices.toMutableList().also { list ->
                val idx = list.indexOfFirst { it.id == updated.id }
                if (idx >= 0) list[idx] = updated else list.add(0, updated)
            }
        }
    }
    val activeInvoices = remember(invoices) {
        invoices
            .filter { it.status == "invoiced" }
            .sortedByDescending { it.createdAt ?: "" }
    }

    val fullName = session.profile?.fullName?.takeIf { it.isNotBlank() } ?: "Customer"
    val warehouseCode = session.profile?.warehouseId?.takeIf { it.isNotBlank() } ?: "TC-XXXX"
    val lines = (warehouse as? WarehouseViewModel.UiState.Loaded)
        ?.addresses?.get("UK")?.lines ?: DEFAULT_LINES

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
        HomeGreetingCarousel(
            dashVm = dashVm,
            onTap = { destination ->
                if (destination is HomeGreetingDestination.NpsSurvey) {
                    npsSheetVisible = true
                } else {
                    onGreetingTap(destination)
                }
            }
        )

        // BFM-primary pivot: hero card leads with the concierge flow,
        // pre-register sits below as a co-equal secondary path. Mirrors
        // CustomerDashboardView's actionGrid on iOS.
        BfmHeroCard(onClick = onOpenBuyForMe)
        PreRegisterCard(onClick = onOpenPreRegister)

        if (activeInvoices.isNotEmpty()) {
            ActiveInvoicesSection(invoices = activeInvoices, onPay = onPayInvoice)
        }

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

        if (state.recentParcels.isNotEmpty()) {
            Text(
                "Recent",
                color = Brand.ink,
                style = MaterialTheme.typography.titleLarge
            )
            state.recentParcels.take(5).forEach { p ->
                ParcelTile(
                    title = p.description?.takeIf { it.isNotBlank() }
                        ?: p.retailer?.takeIf { it.isNotBlank() }
                        ?: "Parcel",
                    // Only surface a tracking number when we have a real one —
                    // never leak the internal UUID prefix (P3-era bug where
                    // p.id.take(8) was rendered as if it were a tracking ref).
                    subtitle = p.trackingNumber?.takeIf { it.isNotBlank() },
                    onClick = { onOpenParcel(p.id) }
                )
            }
        }

        HowItWorksSection()

        Spacer(Modifier.height(48.dp))
    }

    if (npsSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { npsSheetVisible = false },
            sheetState = npsSheetState,
            containerColor = Brand.cream
        ) {
            // General feedback survey — no parcel context (the
            // auto-prompt-on-delivery path stays disabled, see
            // CustomerDashboardView's comment for why).
            NpsSurveyContent(parcelId = null, onDismiss = { npsSheetVisible = false })
        }
    }
}

@Composable
private fun BfmHeroCard(onClick: () -> Unit) {
    val gradient = Brush.linearGradient(
        colors = listOf(Brand.Orange, Color(0xFFD9501C))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Start a Buy-for-me request",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Text(
                "Send us a link from any UK retailer — we buy on your behalf, ship to Kenya, deliver to your door.",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 13.sp
            )
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun PreRegisterCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = 0.78f), RoundedCornerShape(22.dp))
            .border(1.dp, Brand.ink.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Brand.peach, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PostAdd, contentDescription = null, tint = Brand.Orange)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Pre-register a parcel",
                color = Brand.ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Text(
                "Already bought somewhere we don't cover? Tell us it's coming.",
                color = Brand.ink.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Brand.ink.copy(alpha = 0.6f))
    }
}

/**
 * 4-step BFM narrative mirroring iOS HowItWorksView. Alternates light
 * SoftCard and dark InkCard for visual rhythm.
 */
@Composable
private fun HowItWorksSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "OUR WORKFLOW",
                color = Brand.Orange,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "HOW IT WORKS",
                color = Brand.ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .background(Brand.Orange, RoundedCornerShape(2.dp))
            )
        }
        Spacer(Modifier.height(4.dp))
        StepCard(
            number = "01",
            title = "Send us a retailer link",
            body = "Found something on Amazon, ASOS, John Lewis — anywhere in the UK? Drop us the link and we'll take it from there. No UK card or address required.",
            icon = Icons.Filled.Link,
            dark = false
        )
        StepCard(
            number = "02",
            title = "We buy on your behalf",
            body = "An operator quotes you the total in GBP, you pay by card or M-Pesa, and we order it on your behalf.",
            icon = Icons.Filled.AutoAwesome,
            dark = true
        )
        StepCard(
            number = "03",
            title = "UK warehouse and air freight",
            body = "Your purchases land at our Stockport hub, get consolidated into the next UK→Nairobi flight, and clear customs in Kenya.",
            icon = Icons.Filled.AirplanemodeActive,
            dark = false
        )
        StepCard(
            number = "04",
            title = "Door-step delivery in Kenya",
            body = "A rider drops it at your address within 48 hours of touchdown. Already bought somewhere else? Pre-register the parcel and it joins the same flight.",
            icon = Icons.Filled.TwoWheeler,
            dark = true
        )
    }
}

@Composable
private fun StepCard(
    number: String,
    title: String,
    body: String,
    icon: ImageVector,
    dark: Boolean
) {
    val titleColor = if (dark) Brand.cream else Brand.ink
    val bodyColor = if (dark) Brand.cream.copy(alpha = 0.7f) else Brand.ink.copy(alpha = 0.7f)
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Brand.Orange, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Text(
                "$number. $title",
                color = titleColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Text(body, color = bodyColor)
        }
    }
    if (dark) {
        InkCard { content() }
    } else {
        SoftCard { content() }
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
private fun ActiveInvoicesSection(
    invoices: List<CustomerConsolidationDto>,
    onPay: (CustomerConsolidationDto) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EyebrowPill(
            label = if (invoices.size == 1) "Invoice due" else "${invoices.size} invoices due",
            icon = Icons.AutoMirrored.Filled.ReceiptLong
        )
        invoices.forEach { c ->
            ActiveInvoiceCard(consolidation = c, onPay = { onPay(c) })
        }
    }
}

@Composable
private fun ActiveInvoiceCard(
    consolidation: CustomerConsolidationDto,
    onPay: () -> Unit
) {
    InkCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                consolidation.description
                    ?: if (consolidation.isStandalone) "Standalone invoice" else "Shipping invoice",
                color = Brand.cream,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            consolidation.invoiceAmount?.let { amount ->
                Text(
                    "${consolidation.invoiceCurrency} %,.0f".format(amount),
                    color = Brand.Orange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
            }
            consolidation.parcelCount?.takeIf { it > 0 }?.let {
                Text(
                    "$it parcel${if (it == 1) "" else "s"} in this batch",
                    color = Brand.cream.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            OrangeButton(text = "Pay invoice", onClick = onPay)
        }
    }
}

@Composable
private fun ParcelTile(title: String, subtitle: String?, onClick: () -> Unit) {
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
            subtitle?.let {
                Text(it, color = Brand.Orange, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
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
