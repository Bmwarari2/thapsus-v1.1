package com.thapsus.cargo.android.ui.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand

/**
 * Outbox surface — pending SQLDelight mutations + manual flush.
 * Mirrors iOS OutboxView. Same OutboxViewModel that powers the rider
 * outbox: counts queued POD captures + intake events from
 * `pending_mutation` and lets the operator force a sync.
 *
 * P3.2 ships the operator-side surface; the rider-side inline version
 * inside RiderScaffold continues to exist until a follow-up extracts
 * the two into a single shared composable.
 */
@Composable
fun OutboxScreen() {
    val vm = remember { ThapsusSdk.outboxViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.refresh() }

    val pending by vm.pending.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val lastFlushed by vm.lastFlushed.collectAsStateWithLifecycle()
    val lastError by vm.lastError.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EyebrowPill(label = "Sync")
        EditorialHeader(
            title = "Outbox",
            subtitle = "Queued mutations waiting on connectivity."
        )

        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "PENDING",
                    color = Brand.cream.copy(alpha = 0.6f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                )
                Text(
                    pending.toString(),
                    color = Brand.cream,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 56.sp
                )
                lastFlushed?.let { n ->
                    Text(
                        if (n == 0) "Last flush sent 0 — see error below."
                        else "Last flush sent $n",
                        color = Brand.cream.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        lastError?.takeIf { it.isNotBlank() }?.let { err ->
            CalloutBanner(title = "Last sync error", message = err)
        }

        Button(
            onClick = { vm.flushNow() },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand.Orange,
                contentColor = Color.White,
                disabledContainerColor = Brand.Orange.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.height(20.dp))
            } else {
                Text("Flush now", fontWeight = FontWeight.SemiBold)
            }
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("How this works", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                Text(
                    "Intake events captured on poor signal save locally. The app retries with exponential back-off whenever connectivity is available, or whenever you tap Flush.",
                    color = Brand.ink.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
