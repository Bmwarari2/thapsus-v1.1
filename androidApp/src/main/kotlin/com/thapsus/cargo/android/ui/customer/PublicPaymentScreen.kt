package com.thapsus.cargo.android.ui.customer

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand

/**
 * Universal-link entry: thapsus://pay/<orderId> opens this screen so
 * anyone (logged in or not) can pay a duty/quote invoice. The actual
 * payment goes through Express /api/payment/<orderId> routes via the
 * external browser, so we never collect card details inside the app.
 * Mirrors iOS PublicPaymentView.
 */
@Composable
fun PublicPaymentScreen(orderId: String) {
    val context = LocalContext.current
    val paymentUrl = "https://www.thapsus.uk/pay/$orderId"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EyebrowPill(label = "Pay invoice", icon = Icons.Filled.CreditCard)
        EditorialHeader(
            title = "Order ${orderId.take(8)}…",
            subtitle = "Open the secure payment page to settle this invoice."
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "You can pay by M-Pesa, card, or credit balance.",
                    color = Brand.ink.copy(alpha = 0.75f)
                )
                OrangeButton(
                    text = "Open payment page",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, paymentUrl.toUri()).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                )
            }
        }

        CalloutBanner(
            title = "Secure handoff",
            message = "We never collect card details inside the app — payment runs on thapsus.uk over HTTPS."
        )

        Spacer(Modifier.height(40.dp))
    }
}
