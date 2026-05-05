package com.thapsus.cargo.android.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.ConsolidationDto
import com.thapsus.cargo.data.dto.CustomsEntryDto
import com.thapsus.cargo.data.dto.CustomsStatus
import com.thapsus.cargo.data.repository.AuthSession
import com.thapsus.cargo.presentation.AgentInvoicesViewModel

private object AgentRoutes {
    const val CUSTOMS = "agent/customs"
    const val INVOICES = "agent/invoices"
    const val ACCOUNT = "agent/account"
}

private data class TabSpec(val label: String, val route: String, val icon: ImageVector)
private val agentTabs = listOf(
    TabSpec("Customs", AgentRoutes.CUSTOMS, Icons.Filled.AssignmentTurnedIn),
    TabSpec("Invoices", AgentRoutes.INVOICES, Icons.Filled.Receipt),
    TabSpec("Account", AgentRoutes.ACCOUNT, Icons.Filled.AccountCircle)
)

@Composable
fun AgentScaffold(session: AuthSession.Authenticated, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Brand.cream) {
                agentTabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            val popped = nav.popBackStack(tab.route, inclusive = false)
                            if (!popped) {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = AgentRoutes.CUSTOMS,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(AgentRoutes.CUSTOMS) { CustomsScreen(agentId = session.userId) }
            composable(AgentRoutes.INVOICES) { AgentInvoicesScreen() }
            composable(AgentRoutes.ACCOUNT) { AgentAccountScreen(session, onSignOut) }
        }
    }
}

@Composable
private fun CustomsScreen(agentId: String) {
    val vm = remember(agentId) { ThapsusSdk.customsAgentViewModel(agentId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }

    val consolidations by vm.assignedConsolidations.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EyebrowPill(label = "Customs agent")
        EditorialHeader(
            title = "Customs",
            subtitle = "Consolidations awaiting clearance."
        )

        error?.let { CalloutBanner(title = "Error", message = it) }

        if (consolidations.isEmpty()) {
            SoftCard {
                Text("No consolidations assigned yet.", color = Brand.ink.copy(alpha = 0.7f))
            }
        } else {
            consolidations.forEach { c ->
                ConsolidationRow(c, isSelected = c.id == selectedId, onTap = { vm.select(c.id) })
            }
        }

        if (selectedId != null) {
            Spacer(Modifier.height(8.dp))
            Text("Customs entries", color = Brand.ink, fontWeight = FontWeight.SemiBold)
            if (entries.isEmpty()) {
                SoftCard { Text("No entries filed yet.", color = Brand.ink.copy(alpha = 0.7f)) }
            } else {
                entries.forEach { e -> CustomsEntryRow(e) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConsolidationRow(c: ConsolidationDto, isSelected: Boolean, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) Brand.Orange.copy(alpha = 0.18f) else Brand.cream.copy(alpha = 0.78f),
                RoundedCornerShape(22.dp)
            )
            .padding(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Week of ${c.weekStart}", color = Brand.ink, fontWeight = FontWeight.SemiBold)
            c.masterAwbNo?.let {
                Text("AWB $it", color = Brand.Orange, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            Text(
                "${c.totalParcels} parcels · %.1f kg".format(c.totalKg),
                color = Brand.ink.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        Button(
            onClick = onTap,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSelected) Brand.ink else Brand.Orange,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) { Text(if (isSelected) "Selected" else "Select", fontSize = 12.sp) }
    }
}

@Composable
private fun CustomsEntryRow(e: CustomsEntryDto) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.idfNo ?: "IDF pending", color = Brand.ink, fontWeight = FontWeight.SemiBold)
            Text(
                "Status: ${friendly(e.status)}",
                color = Brand.ink.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Text(
                "CIF KES %.2f · Duty KES %.2f · VAT KES %.2f".format(
                    e.cifKes ?: 0.0,
                    e.dutyKes ?: 0.0,
                    e.vatKes ?: 0.0
                ),
                color = Brand.ink.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun friendly(s: CustomsStatus) = when (s) {
    CustomsStatus.PRE_ALERT -> "Pre-alert"
    CustomsStatus.IDF_SUBMITTED -> "IDF submitted"
    CustomsStatus.ENTRY_FILED -> "Entry filed"
    CustomsStatus.DUTY_ASSESSED -> "Duty assessed"
    CustomsStatus.DUTY_PAID -> "Duty paid"
    CustomsStatus.RELEASED -> "Released"
    CustomsStatus.REJECTED -> "Rejected"
}

@Composable
private fun AgentInvoicesScreen() {
    val vm = remember { ThapsusSdk.agentInvoicesViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.load() }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EyebrowPill(label = "Customs agent")
        EditorialHeader(title = "Invoices", subtitle = "Submitted invoices for clearance work.")

        when (val s = state) {
            is AgentInvoicesViewModel.UiState.Loading -> CircularProgressIndicator(color = Brand.ink)
            is AgentInvoicesViewModel.UiState.Error -> CalloutBanner(title = "Couldn't load", message = s.message)
            is AgentInvoicesViewModel.UiState.Loaded -> {
                if (s.invoices.isEmpty()) {
                    SoftCard { Text("No invoices yet.", color = Brand.ink.copy(alpha = 0.7f)) }
                } else {
                    s.invoices.forEach { inv ->
                        SoftCard {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    inv.invoiceNo ?: "Invoice (no number)",
                                    color = Brand.ink,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "KES %.2f".format(inv.amountKes),
                                    color = Brand.Orange,
                                    fontWeight = FontWeight.SemiBold
                                )
                                inv.consolidationId?.let {
                                    Text(
                                        "Consol ${it.take(8)}",
                                        color = Brand.ink.copy(alpha = 0.55f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AgentAccountScreen(session: AuthSession.Authenticated, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        EyebrowPill(label = "Account")
        EditorialHeader(title = "Account", subtitle = "Customs agent profile.")
        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "CLEARING AGENT",
                    color = Brand.Orange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp
                )
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
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E), contentColor = Color.White)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sign out", fontWeight = FontWeight.SemiBold)
        }
    }
}
