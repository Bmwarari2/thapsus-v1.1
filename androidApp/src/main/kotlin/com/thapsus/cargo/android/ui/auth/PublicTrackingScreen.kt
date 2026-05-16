package com.thapsus.cargo.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.TrackingDto
import com.thapsus.cargo.data.dto.TrackingPackageDto
import com.thapsus.cargo.presentation.PublicTrackingViewModel

private val TIMELINE_STEPS = listOf(
    "pending" to "Pending",
    "received_at_warehouse" to "Received",
    "consolidating" to "Consolidating",
    "in_transit" to "In transit",
    "customs" to "Customs",
    "out_for_delivery" to "Out for delivery",
    "delivered" to "Delivered"
)

/**
 * Public (unauthenticated) parcel tracking — Android twin of the
 * iOS PublicTrackingCard flow on TrackingView. Backed by the shared
 * PublicTrackingViewModel which hits GET /api/tracking/:trackingNumber
 * with no auth.
 *
 * Hosted inside a ModalBottomSheet on SignInScreen so customers
 * tracking a shipment don't need to create an account just to see
 * where their parcel is.
 */
@Composable
fun PublicTrackingScreen(onClose: () -> Unit) {
    val vm = remember { ThapsusSdk.publicTrackingViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }

    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EyebrowPill(label = "Track")
        EditorialHeader(
            title = "Track without signing in",
            subtitle = "Enter the tracking number we emailed you. Looks like THP-20260512-A1B2 or similar."
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.trim() },
                    label = { Text("Tracking number") },
                    placeholder = { Text("THP-20260512-A1B2") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { vm.search(query) },
                    enabled = query.isNotBlank() && state !is PublicTrackingViewModel.State.Loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brand.ink,
                        contentColor = Brand.cream
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    if (state is PublicTrackingViewModel.State.Loading) {
                        CircularProgressIndicator(
                            color = Brand.cream,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Search", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        when (val s = state) {
            PublicTrackingViewModel.State.Idle,
            is PublicTrackingViewModel.State.Loading -> Unit
            is PublicTrackingViewModel.State.Error -> CalloutBanner(
                title = "Couldn't find that",
                message = s.message
            )
            is PublicTrackingViewModel.State.Found -> ResultBlock(s.tracking)
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand.ink.copy(alpha = 0.08f),
                contentColor = Brand.ink
            ),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) { Text("Close") }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ResultBlock(tracking: TrackingDto) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    tracking.trackingNumber,
                    color = Brand.Orange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                tracking.retailer?.let {
                    Text(it, color = Brand.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                tracking.description?.let {
                    Text(it, color = Brand.ink.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }

        val hold = tracking.holdReason
        if (!hold.isNullOrBlank() && tracking.holdResolvedAt.isNullOrEmpty()) {
            HeldBanner(hold)
        }

        Timeline(currentStatus = tracking.status)

        if (tracking.packages.isNotEmpty()) {
            Text("Packages", color = Brand.ink, fontWeight = FontWeight.SemiBold)
            tracking.packages.forEach { p -> PackageRow(p) }
        }
    }
}

@Composable
private fun HeldBanner(holdReason: String) {
    val message = when (holdReason) {
        "held_at_nairobi_hub" ->
            "Held at hub. Two delivery attempts failed — please contact support to arrange collection or redelivery."
        else ->
            "On hold: ${holdReason.replace('_', ' ')}"
    }
    SoftCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color(0xFFD9A441))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Action needed", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                Text(message, color = Brand.ink.copy(alpha = 0.65f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Timeline(currentStatus: String?) {
    val currentIdx = TIMELINE_STEPS.indexOfFirst { it.first == currentStatus }
        .takeIf { it >= 0 } ?: 0
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TIMELINE_STEPS.forEachIndexed { idx, step ->
                val reached = idx <= currentIdx
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                if (reached) Color(0xFF0F8A4F).copy(alpha = 0.18f) else Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (reached) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (reached) Color(0xFF0F8A4F) else Brand.ink.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        step.second,
                        color = when {
                            idx == currentIdx -> Brand.ink
                            reached -> Brand.ink.copy(alpha = 0.7f)
                            else -> Brand.ink.copy(alpha = 0.5f)
                        },
                        fontWeight = if (idx == currentIdx) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PackageRow(pkg: TrackingPackageDto) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                pkg.description ?: "Package",
                color = Brand.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pkg.weightKg?.let { kg ->
                    Text(
                        formatKg(kg),
                        color = Brand.ink.copy(alpha = 0.55f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
                pkg.status?.let {
                    Text(it, color = Brand.ink.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
        }
    }
}

private fun formatKg(kg: Double): String {
    val rounded = (kg * 10.0).toLong() / 10.0
    return "$rounded kg"
}
