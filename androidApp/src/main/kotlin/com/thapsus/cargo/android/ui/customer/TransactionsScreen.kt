package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand

/**
 * "Transactions" — card / M-Pesa / credit activity. P2.2 ships the
 * route stub; the full repository-backed list lands in P2.4 alongside
 * the Wallet → CreditCenter split.
 */
@Composable
fun TransactionsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Activity",
            title = "Transactions",
            subtitle = "Card + M-Pesa payments and credit activity."
        )
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Coming next",
                    style = MaterialTheme.typography.titleMedium,
                    color = Brand.ink
                )
                Text(
                    "The full transactions ledger lands in P2.4 alongside the Wallet → Credit centre split.",
                    color = Brand.ink.copy(alpha = 0.7f)
                )
            }
        }
    }
}
