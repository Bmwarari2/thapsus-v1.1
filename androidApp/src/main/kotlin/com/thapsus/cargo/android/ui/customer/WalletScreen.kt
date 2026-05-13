package com.thapsus.cargo.android.ui.customer

// Phase 0 stub — the original WalletScreen was written against the
// pre-mig-028 wallet model (TransactionDto / TransactionStatus /
// TransactionType / DepositStatus / PaymentMethod) and stopped
// compiling after the wallet → user_credits + payments rip.
//
// Scheduled rewrite: Phase 2.4 (WalletScreen → CreditCenterScreen +
// TransactionsScreen split). Will mirror iOS:
//   - CreditCenterView (user_credits balance + history via
//     CreditLedgerRepository)
//   - TransactionsView (payments rows for this user via
//     PaymentsRepository)
//
// Full original code is preserved in git history pre-stub.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand

@Composable
fun WalletScreen(userId: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Wallet",
            color = Brand.ink,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Pending rebuild (P2.4)",
            color = Brand.ink.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium
        )
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Your wallet has been replaced by Credit + Transactions. " +
                        "These two new tabs are coming in the next Android pass.",
                    color = Brand.ink
                )
                Text(
                    "In the meantime, view your credit balance and payment " +
                        "history on the web at thapsus.uk/credit and /transactions.",
                    color = Brand.ink.copy(alpha = 0.7f)
                )
            }
        }
    }
}
