package com.thapsus.cargo.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.thapsus.cargo.data.dto.AmlFlagDto
import kotlinx.coroutines.launch

/**
 * Admin AML risk queue — Android counterpart of the AML section in
 * iOS AdminDashboardView. Reads /admin/aml-flags via AdminRepository
 * (no dedicated shared VM; aligns with AdminRevenueScreen's pattern).
 *
 * Status filter chips mirror the server-side state machine:
 *   open → cleared (false positive) or escalated (real risk).
 */
@Composable
fun AdminAmlQueueScreen(onBack: () -> Unit) {
    val admin = remember { ThapsusSdk.adminRepo() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("open") }
    val flags = remember { mutableStateListOf<AmlFlagDto>() }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        errorMessage = null
        admin.amlFlags(status)
            .onSuccess {
                flags.clear()
                flags.addAll(it)
                loading = false
            }
            .onFailure {
                errorMessage = it.message ?: "Couldn't load AML queue"
                loading = false
            }
    }

    LaunchedEffect(status) { reload() }

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
        EyebrowPill(label = "Compliance")
        EditorialHeader(
            title = "AML risk queue",
            subtitle = "Flags raised against customer or parcel activity. Clear false positives, escalate real risk."
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("open" to "Open", "cleared" to "Cleared", "escalated" to "Escalated").forEach { (key, label) ->
                FilterChip(
                    selected = status == key,
                    onClick = { status = key },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Brand.ink,
                        selectedLabelColor = Brand.cream
                    )
                )
            }
        }

        val actionLocal = actionMessage
        if (actionLocal != null) {
            CalloutBanner(
                title = "Done",
                message = actionLocal,
                tint = Color(0xFF0F8A4F).copy(alpha = 0.14f)
            )
        }
        val errorLocal = errorMessage
        if (errorLocal != null) {
            CalloutBanner(title = "Couldn't load", message = errorLocal)
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        } else if (flags.isEmpty()) {
            SoftCard {
                Text(
                    when (status) {
                        "open" -> "No open AML flags."
                        "cleared" -> "No cleared flags yet."
                        "escalated" -> "No escalated flags yet."
                        else -> "No flags."
                    },
                    color = Brand.ink.copy(alpha = 0.6f)
                )
            }
        } else {
            flags.forEach { flag ->
                FlagCard(
                    flag = flag,
                    onResolve = { newStatus ->
                        scope.launch {
                            admin.resolveAmlFlag(flag.id, newStatus, notes = null)
                                .onSuccess {
                                    actionMessage = when (newStatus) {
                                        "cleared" -> "Flag cleared."
                                        "escalated" -> "Flag escalated."
                                        else -> "Flag updated."
                                    }
                                    reload()
                                }
                                .onFailure {
                                    errorMessage = it.message ?: "Couldn't update"
                                }
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FlagCard(
    flag: AmlFlagDto,
    onResolve: (String) -> Unit
) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    flag.userName ?: flag.userEmail ?: flag.userId,
                    modifier = Modifier.weight(1f),
                    color = Brand.ink,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .background(Brand.Orange.copy(alpha = 0.16f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        flag.status.uppercase(),
                        color = Brand.Orange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
            Text(
                flag.reason,
                color = Brand.ink.copy(alpha = 0.75f),
                fontSize = 14.sp
            )
            flag.parcelId?.let {
                Text(
                    "Parcel · $it",
                    color = Brand.ink.copy(alpha = 0.45f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            flag.notes?.let {
                Text(
                    it,
                    color = Brand.ink.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
            flag.createdAt?.let {
                Text(
                    it,
                    color = Brand.ink.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
            }
            if (flag.status == "open") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onResolve("cleared") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F8A4F).copy(alpha = 0.14f),
                            contentColor = Color(0xFF0F8A4F)
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) { Text("Clear", fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { onResolve("escalated") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F).copy(alpha = 0.14f),
                            contentColor = Color(0xFFD32F2F)
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) { Text("Escalate", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
