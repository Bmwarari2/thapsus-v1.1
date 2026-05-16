package com.thapsus.cargo.android.ui.customer

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.AccountDeletionRequestDto
import com.thapsus.cargo.presentation.AccountDeletionViewModel

/**
 * Customer-self-serve account deletion with a 14-day cooldown — Android
 * counterpart of iOS AccountDeletionView. Backed by the shared
 * AccountDeletionViewModel.
 *
 * Idle      → explainer card + destructive "Start deletion" CTA.
 * Active    → countdown, download CTA (signed URL), refresh-link
 *             button, cancel CTA.
 * Cancelled → banner explaining "your previous request was cancelled"
 *             + the idle card so the customer can re-request.
 * Completed → stale-session note.
 */
@Composable
fun AccountDeletionScreen(onBack: () -> Unit) {
    val vm = remember { ThapsusSdk.accountDeletionViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.load() }

    val state by vm.state.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

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
        EyebrowPill(label = "Account")
        EditorialHeader(
            title = "Delete your account",
            subtitle = "Closing your account starts a 14-day cooldown. We'll email you a copy of everything we hold on you as a single HTML file, in case you want it for your records."
        )

        when (val a = action) {
            is AccountDeletionViewModel.ActionState.Done -> CalloutBanner(
                title = "Done",
                message = a.message,
                tint = Color(0xFF0F8A4F).copy(alpha = 0.14f)
            )
            is AccountDeletionViewModel.ActionState.Error -> CalloutBanner(
                title = "Couldn't update",
                message = a.message
            )
            AccountDeletionViewModel.ActionState.InFlight -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand.ink) }
            AccountDeletionViewModel.ActionState.Idle -> Unit
        }

        when (val s = state) {
            AccountDeletionViewModel.UiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand.ink) }
            AccountDeletionViewModel.UiState.Idle -> IdleView(
                onStart = { showConfirmDialog = true }
            )
            is AccountDeletionViewModel.UiState.Active -> ActiveView(
                request = s.request,
                onRefresh = { vm.refreshExportUrl() },
                onCancel = { showCancelDialog = true }
            )
            is AccountDeletionViewModel.UiState.Cancelled -> CancelledView(
                request = s.request,
                onRestart = { showConfirmDialog = true }
            )
            is AccountDeletionViewModel.UiState.Completed -> CompletedView()
            is AccountDeletionViewModel.UiState.Error -> CalloutBanner(
                title = "Couldn't load",
                message = s.message
            )
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Start 14-day deletion?") },
            text = {
                Text("Your account will be permanently deleted 14 days from now. You can cancel any time before then. We'll email you a download of your data.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.startDeletion()
                    showConfirmDialog = false
                }) {
                    Text("Start deletion", color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel deletion?") },
            text = {
                Text("Your account will stay as it is. You can request deletion again at any time.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.cancelDeletion(null)
                    showCancelDialog = false
                }) { Text("Cancel deletion", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep deletion") }
            }
        )
    }
}

@Composable
private fun IdleView(onStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "What happens",
                    color = Brand.ink,
                    fontWeight = FontWeight.SemiBold
                )
                BulletRow("We email you a single HTML file with everything we hold on you (profile, orders, payments, tickets…).")
                BulletRow("Your account is held in cooldown for 14 days. You can cancel from this screen any time.")
                BulletRow("On the 14th day, your account and every order, package, payment, ticket, and message is permanently deleted.")
                BulletRow("Payment-proof artefacts (M-Pesa SMS contents, payer phone, Stripe IDs) are not included in the export.")
            }
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD32F2F),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) { Text("Start deletion", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ActiveView(
    request: AccountDeletionRequestDto,
    onRefresh: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val signedUrl = request.exportSignedUrl

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${request.daysRemaining} days remaining",
                    color = Brand.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    "Scheduled deletion: ${formatScheduled(request.scheduledDeletionAt)}",
                    color = Brand.ink.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                if (request.exportEmailedAt != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Email,
                            contentDescription = null,
                            tint = Color(0xFF0F8A4F),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Data export emailed",
                            color = Color(0xFF0F8A4F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Your data, as HTML",
                    color = Brand.ink,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Open in any browser. The link in the email and here both stay live for 30 days.",
                    color = Brand.ink.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                if (signedUrl != null) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, signedUrl.toUri()).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Brand.Orange,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Download my data", fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Brand.ink.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (signedUrl != null) "Refresh download link" else "Generate download link",
                        color = Brand.ink.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand.ink.copy(alpha = 0.08f),
                contentColor = Brand.ink
            ),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) { Text("Cancel deletion", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun CancelledView(
    request: AccountDeletionRequestDto,
    onRestart: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF0F8A4F),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Previous request cancelled",
                        color = Brand.ink,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                request.cancelledAt?.let {
                    Text(
                        "Cancelled ${formatScheduled(it)}",
                        color = Brand.ink.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }
                Text(
                    "You can request deletion again any time.",
                    color = Brand.ink.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
        }
        IdleView(onStart = onRestart)
    }
}

@Composable
private fun CompletedView() {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Account already deleted",
                color = Brand.ink,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "If you're seeing this, your session is stale. Sign out and back in to confirm.",
                color = Brand.ink.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "•",
            color = Brand.Orange,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(14.dp)
        )
        Text(
            text,
            color = Brand.ink.copy(alpha = 0.7f),
            fontSize = 13.sp
        )
    }
}

private fun formatScheduled(iso: String): String {
    // Render the ISO timestamp as YYYY-MM-DD HH:MM (UTC) — good enough
    // for a "scheduled for" line; the customer's locale is handled by
    // the system clock face elsewhere in the app.
    return try {
        val cleaned = iso.replace("T", " ").replace(Regex("\\.\\d+Z?"), "").replace("Z", "")
        cleaned.take(16)
    } catch (_: Throwable) { iso }
}
