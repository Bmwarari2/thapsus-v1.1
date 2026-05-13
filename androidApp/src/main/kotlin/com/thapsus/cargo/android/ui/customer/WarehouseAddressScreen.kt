package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.OrangeButton
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

/**
 * Standalone warehouse-address surface. The same card lives inside
 * HomeScreen, but customers asked for a dedicated full-screen view
 * for screenshotting and sharing with overseas vendors. Mirrors iOS
 * WarehouseAddressView.
 */
@Composable
fun WarehouseAddressScreen(session: AuthSession.Authenticated) {
    val vm = remember { ThapsusSdk.warehouseViewModel() }
    DisposableEffect(vm) { onDispose { /* WarehouseViewModel has no clear in v1 */ } }
    LaunchedEffect(vm) { vm.load() }
    val state by vm.state.collectAsStateWithLifecycle()

    val fullName = session.profile?.fullName?.takeIf { it.isNotBlank() } ?: "Customer"
    val warehouseCode = session.profile?.warehouseId?.takeIf { it.isNotBlank() } ?: "TC-XXXX"
    val lines = (state as? WarehouseViewModel.UiState.Loaded)?.addresses?.get("UK")?.lines ?: DEFAULT_LINES

    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Account",
            title = "Warehouse address",
            subtitle = "Use this address when you check out at a UK retailer."
        )

        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Brand.Orange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "SHIP TO THIS ADDRESS",
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
                            fullName,
                            color = Brand.Orange,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            warehouseCode,
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
                        clipboard.setText(AnnotatedString((listOf(fullName, warehouseCode) + lines).joinToString("\n")))
                        copied = true
                        scope.launch {
                            delay(1800)
                            copied = false
                        }
                    }
                )
                Text(
                    "Tip: the warehouse code on line 2 is what tells our team this parcel is yours when it arrives.",
                    color = Brand.cream.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
