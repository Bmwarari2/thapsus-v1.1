package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand

/**
 * "Activity" tab — secondary-surface hub. Parcel tracking, pre-register,
 * invoices, and transactions all live here so they remain one tap away
 * from the customer's bottom-tab bar after the BFM-primary pivot.
 *
 * P1.1 routes the first two cards (Tracking + Pre-register) to existing
 * Android screens. Invoices + Transactions land on a placeholder until
 * P2.2 (CustomerInvoicesScreen) and P2.4 (TransactionsScreen) ship.
 */
@Composable
fun ActivityHubScreen(
    onOpenTracking: () -> Unit,
    onOpenPreRegister: () -> Unit,
    onOpenInvoices: () -> Unit,
    onOpenTransactions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            title = "Activity",
            subtitle = "Tracking, invoices, transactions, and pre-register in one place."
        )

        HubCard(
            icon = Icons.Filled.Inventory2,
            iconBg = Brand.ink,
            title = "Parcel tracking",
            subtitle = "Every parcel we're shipping for you, by status.",
            onClick = onOpenTracking
        )
        HubCard(
            icon = Icons.Filled.PostAdd,
            iconBg = Color(0xFF6A4DBA),
            title = "Pre-register a parcel",
            subtitle = "Already bought something? Tell us it's coming.",
            onClick = onOpenPreRegister
        )
        HubCard(
            icon = Icons.Filled.Description,
            iconBg = Brand.Orange,
            title = "Invoices",
            subtitle = "Active and past shipping invoices in one place.",
            onClick = onOpenInvoices
        )
        HubCard(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            iconBg = Brand.ink,
            title = "Transactions",
            subtitle = "Every card or M-Pesa payment plus your credit activity.",
            onClick = onOpenTransactions
        )
    }
}

@Composable
private fun HubCard(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    SoftCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.fillMaxWidth(0.85f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Brand.ink
                )
                Text(
                    subtitle,
                    color = Brand.ink.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Brand.ink.copy(alpha = 0.45f)
            )
        }
    }
}
