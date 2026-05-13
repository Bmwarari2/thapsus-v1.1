package com.thapsus.cargo.android.ui.customer

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.ReferralViewModel

/** Referral hub — share code, see history. Mirrors iOS ReferralView. */
@Composable
fun ReferralScreen() {
    val vm = remember { ThapsusSdk.referralViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.load() }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Earn",
            title = "Refer a friend",
            subtitle = "Share your code. Both of you get rewarded on their first parcel."
        )

        when (val s = state) {
            ReferralViewModel.UiState.Loading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
            is ReferralViewModel.UiState.Error -> CalloutBanner(title = "Couldn't load", message = s.message)
            is ReferralViewModel.UiState.Loaded -> {
                val code = s.summary.referral.referralCode
                val stats = s.summary.referral.statistics
                InkCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "YOUR CODE",
                            color = Brand.cream.copy(alpha = 0.6f),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            code ?: "—",
                            color = Brand.Orange,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 32.sp
                        )
                        if (code != null) {
                            Button(
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Use my Thapsus Cargo code $code to ship UK→Kenya — we both get rewarded. https://www.thapsus.uk")
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(send, "Share code").apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Brand.Orange,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Share code", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox(label = "Friends", value = stats.totalReferrals.toString(), modifier = Modifier.weight(1f))
                    StatBox(label = "Completed", value = stats.completedReferrals.toString(), modifier = Modifier.weight(1f))
                    StatBox(label = "Pending", value = stats.pendingReferrals.toString(), modifier = Modifier.weight(1f))
                }
                if (s.history.isNotEmpty()) {
                    Text("History", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
                    s.history.forEach { entry ->
                        SoftCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.refereeName ?: entry.refereeEmail ?: "Friend", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                                    entry.referredAt?.take(10)?.let {
                                        Text(it, color = Brand.ink.copy(alpha = 0.6f), fontSize = 12.sp)
                                    }
                                }
                                Text(
                                    entry.rewardStatus.uppercase(),
                                    color = when (entry.rewardStatus) {
                                        "completed" -> Color(0xFF2E7D32)
                                        "pending" -> Color(0xFFE08B00)
                                        else -> Brand.ink.copy(alpha = 0.55f)
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                    }
                }
            }
            else -> {}
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier) {
        Column {
            Text(value, color = Brand.ink, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(label.uppercase(), color = Brand.ink.copy(alpha = 0.6f), fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 2.sp)
        }
    }
}
