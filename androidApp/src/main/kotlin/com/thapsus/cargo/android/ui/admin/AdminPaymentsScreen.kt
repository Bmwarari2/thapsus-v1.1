package com.thapsus.cargo.android.ui.admin

// Phase 0 stub — the original AdminPaymentsScreen was written against
// the legacy `transactions` table shape (TransactionDto, payerName,
// customerEmail, mpesaMessage etc.) and stopped compiling after the
// wallet → user_credits + payments rip in migration 028.
//
// Scheduled rewrite: Phase 5.3 (AdminRevenueScreen + admin payments
// alignment). When that PR lands it should:
//   - consume `PaymentDto` from shared/.../data/dto/PaymentDto.kt
//   - mirror iOS AdminPaymentsView.swift's column set
//   - surface the modern Stripe + M-Pesa approve queue
//
// Full original code is preserved in git history at commit pre-stub —
// retrieve with `git log --all -- androidApp/.../AdminPaymentsScreen.kt`
// when the rewrite begins.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand

@Composable
fun AdminPaymentsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Payments",
            color = Brand.ink,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Pending rewrite (P5.3)",
            color = Brand.ink.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium
        )
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This screen is being rebuilt against the post-mig-028 payments table.",
                    color = Brand.ink
                )
                Text(
                    "Until then, manage Stripe + M-Pesa approvals on the web admin console.",
                    color = Brand.ink.copy(alpha = 0.7f)
                )
            }
        }
        InkButton(text = "Back", onClick = onBack)
    }
}
