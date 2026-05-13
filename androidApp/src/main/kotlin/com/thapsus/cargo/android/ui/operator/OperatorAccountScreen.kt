package com.thapsus.cargo.android.ui.operator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun OperatorAccountScreen(
    session: AuthSession.Authenticated,
    onSignOut: () -> Unit,
    onOpenTodaySummary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        EyebrowPill(label = "Account")
        EditorialHeader(title = "Account", subtitle = "Operator profile.")

        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text(
                        "SIGNED IN",
                        color = Brand.cream.copy(alpha = 0.6f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "OPERATOR",
                        color = Brand.Orange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(Brand.Orange.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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
            }
        }

        // Today summary — demoted from a top-level tab in the BFM-primary
        // pivot but kept one tap away so operators can still get the
        // pipeline overview.
        SoftCard(modifier = Modifier.clickable(onClick = onOpenTodaySummary)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Brand.ink, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = Brand.cream)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Today's summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = Brand.ink
                    )
                    Text(
                        "Parcel pipeline + BFM tiles.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Brand.ink.copy(alpha = 0.45f)
                )
            }
        }

        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(48.dp),
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
