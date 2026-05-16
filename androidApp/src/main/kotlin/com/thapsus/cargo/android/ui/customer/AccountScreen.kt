package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.repository.AuthSession

@Composable
fun AccountScreen(
    session: AuthSession.Authenticated,
    onSignOut: () -> Unit,
    onOpenNewOrder: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfileEdit: () -> Unit,
    onOpenCredit: () -> Unit = {},
    onOpenTransactions: () -> Unit = {},
    onOpenConsolidations: () -> Unit = {},
    onOpenInvoices: () -> Unit = {},
    onOpenTickets: () -> Unit = {},
    onOpenReferral: () -> Unit = {},
    onOpenProhibitedSearch: () -> Unit = {},
    onOpenDsar: () -> Unit = {},
    onOpenAccountDeletion: () -> Unit = {},
    onOpenAppearance: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        EyebrowPill(label = "Account")
        EditorialHeader(
            title = "Account",
            subtitle = "Manage profile, orders and support."
        )

        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SIGNED IN",
                        color = Brand.cream.copy(alpha = 0.6f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .background(Brand.Orange.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "CUSTOMER",
                            color = Brand.Orange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
                Text(
                    session.profile?.fullName ?: "—",
                    color = Brand.cream,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                session.email?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Brand.cream.copy(alpha = 0.75f))
                }
                session.profile?.warehouseId?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = Brand.Orange,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Section(title = "Send & ship") {
            LinkRow("New order", Icons.Filled.Add, onOpenNewOrder)
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            LinkRow("Consolidations", Icons.Filled.Inventory2, onOpenConsolidations)
        }

        Section(title = "Money") {
            LinkRow("Credit centre", Icons.Filled.AccountBalanceWallet, onOpenCredit)
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            LinkRow("Invoices", Icons.Filled.Description, onOpenInvoices)
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            LinkRow("Transactions", Icons.AutoMirrored.Filled.ReceiptLong, onOpenTransactions)
        }

        Section(title = "Account") {
            LinkRow("Notifications", Icons.Filled.Notifications, onOpenNotifications)
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            LinkRow("Edit profile", Icons.Filled.Edit, onOpenProfileEdit)
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            LinkRow("Appearance", Icons.Filled.Brightness6, onOpenAppearance)
        }

        Section(title = "Help & rewards") {
            LinkRow("Support tickets", Icons.AutoMirrored.Filled.Help, onOpenTickets)
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            LinkRow("Refer a friend", Icons.Filled.Group, onOpenReferral)
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            LinkRow("Can I ship this?", Icons.Filled.Search, onOpenProhibitedSearch)
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            LinkRow("My data (GDPR)", Icons.Filled.PrivacyTip, onOpenDsar)
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            LinkRow("Delete account", Icons.Filled.DeleteForever, onOpenAccountDeletion)
        }

        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFB3261E),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sign out", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Brand.ink, style = MaterialTheme.typography.titleLarge)
        SoftCard { Column { content() } }
    }
}

@Composable
private fun LinkRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Brand.Orange, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = Brand.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Brand.ink.copy(alpha = 0.5f)
        )
    }
}
