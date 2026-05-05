package com.thapsus.cargo.android.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.thapsus.cargo.android.ui.admin.AdminScaffold
import com.thapsus.cargo.android.ui.agent.AgentScaffold
import com.thapsus.cargo.android.ui.customer.CustomerScaffold
import com.thapsus.cargo.android.ui.operator.OperatorScaffold
import com.thapsus.cargo.android.ui.rider.RiderScaffold
import com.thapsus.cargo.android.ui.primitives.BrandWordmark
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.primitives.WordmarkSize
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.repository.AuthSession
import com.thapsus.cargo.domain.model.UserRole

/**
 * Role-aware top-level scaffold. Customer role goes to the full Phase C
 * `CustomerScaffold`. Other roles still show the Phase B placeholder tabs
 * until their respective phases land (D = Operator, E = Admin/Agent/Rider).
 */
@Composable
fun RoleTabsScreen(session: AuthSession.Authenticated, onSignOut: () -> Unit) {
    when (session.role) {
        UserRole.CUSTOMER -> CustomerScaffold(session = session, onSignOut = onSignOut)
        UserRole.OPERATOR -> OperatorScaffold(session = session, onSignOut = onSignOut)
        UserRole.ADMIN -> AdminScaffold(session = session, onSignOut = onSignOut)
        UserRole.CLEARING_AGENT -> AgentScaffold(session = session, onSignOut = onSignOut)
        UserRole.RIDER -> RiderScaffold(session = session, onSignOut = onSignOut)
    }
}

@Composable
private fun PlaceholderTabsScreen(role: UserRole, onSignOut: () -> Unit) {
    val tabs = remember(role) { tabsForRole(role) }
    var selected by rememberSaveable(role) { mutableStateOf(0) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Brand.cream) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            BrandWordmark(size = WordmarkSize.Small)
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tabs[selected].label, color = Brand.ink)
                    Text(
                        "Phase B placeholder — the real ${tabs[selected].label} screen lands in a later phase.",
                        color = Brand.ink.copy(alpha = 0.7f)
                    )
                }
            }
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Signed in as ${roleLabel(role)}", color = Brand.ink)
                    InkButton(text = "Sign out", onClick = onSignOut)
                }
            }
        }
    }
}

private data class TabSpec(val label: String, val icon: ImageVector)

private fun tabsForRole(role: UserRole): List<TabSpec> = when (role) {
    UserRole.CUSTOMER -> emptyList() // Customer goes through CustomerScaffold
    UserRole.OPERATOR -> listOf(
        TabSpec("Today", Icons.Filled.CalendarToday),
        TabSpec("Receive", Icons.Filled.Print),
        TabSpec("Consols", Icons.Filled.Inventory2),
        TabSpec("Dispatch", Icons.Filled.DeliveryDining),
        TabSpec("Account", Icons.Filled.AccountCircle)
    )
    UserRole.CLEARING_AGENT -> listOf(
        TabSpec("Customs", Icons.Filled.AssignmentTurnedIn),
        TabSpec("Invoices", Icons.Filled.Receipt),
        TabSpec("Account", Icons.Filled.AccountCircle)
    )
    UserRole.RIDER -> listOf(
        TabSpec("Today", Icons.Filled.Map),
        TabSpec("Outbox", Icons.Filled.Outbox),
        TabSpec("Account", Icons.Filled.AccountCircle)
    )
    UserRole.ADMIN -> listOf(
        TabSpec("Console", Icons.Filled.SpaceDashboard),
        TabSpec("KPI", Icons.Filled.BarChart),
        TabSpec("Consols", Icons.Filled.Inventory2),
        TabSpec("Settings", Icons.Filled.Settings),
        TabSpec("Account", Icons.Filled.AccountCircle)
    )
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.CUSTOMER -> "Customer"
    UserRole.OPERATOR -> "Operator"
    UserRole.CLEARING_AGENT -> "Clearing agent"
    UserRole.RIDER -> "Rider"
    UserRole.ADMIN -> "Admin"
}
