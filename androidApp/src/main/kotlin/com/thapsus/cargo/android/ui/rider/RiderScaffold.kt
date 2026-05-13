package com.thapsus.cargo.android.ui.rider

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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.thapsus.cargo.android.ui.primitives.BigStatTile
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.LastMileRunDto
import com.thapsus.cargo.data.dto.RunStatus
import com.thapsus.cargo.data.repository.AuthSession

private object RiderRoutes {
    const val TODAY = "rider/today"
    const val OUTBOX = "rider/outbox"
    const val ACCOUNT = "rider/account"
}

private data class TabSpec(val label: String, val route: String, val icon: ImageVector)
private val riderTabs = listOf(
    TabSpec("Today", RiderRoutes.TODAY, Icons.Filled.Map),
    TabSpec("Outbox", RiderRoutes.OUTBOX, Icons.Filled.Outbox),
    TabSpec("Account", RiderRoutes.ACCOUNT, Icons.Filled.AccountCircle)
)

@Composable
fun RiderScaffold(session: AuthSession.Authenticated, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Brand.cream) {
                riderTabs.forEach { tab ->
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
            startDestination = RiderRoutes.TODAY,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(RiderRoutes.TODAY) { RiderTodayScreen(riderId = session.userId) }
            composable(RiderRoutes.OUTBOX) { RiderOutboxScreen() }
            composable(RiderRoutes.ACCOUNT) { RiderAccountScreen(session, onSignOut) }
        }
    }
}

@Composable
private fun RiderTodayScreen(riderId: String) {
    val vm = remember(riderId) { ThapsusSdk.riderRunViewModel(riderId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.refresh() }
    val runs by vm.runs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EyebrowPill(label = "Last mile")
        EditorialHeader(
            title = "Today",
            subtitle = "Active rider runs assigned to you."
        )
        if (runs.isEmpty()) {
            SoftCard {
                Text("No runs assigned. Check back when dispatch creates one.", color = Brand.ink.copy(alpha = 0.7f))
            }
        } else {
            runs.forEach { run -> RunRow(run) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RunRow(run: LastMileRunDto) {
    SoftCard {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(run.zone, color = Brand.ink, fontWeight = FontWeight.SemiBold)
                Text(
                    "Run ${run.runDate}",
                    color = Brand.ink.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                friendly(run.status),
                color = Brand.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(Brand.Orange.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

// RunStatus enum was simplified to 4 cases (PLANNED / IN_PROGRESS /
// COMPLETED / CANCELLED) — "Scheduled", "En route", and "Partial" no
// longer exist in shared/.../LastMileRunDto.kt. Labels match the iOS
// RiderRunView/DispatchView convention so the two clients read the
// same on screen.
private fun friendly(s: RunStatus) = when (s) {
    RunStatus.PLANNED -> "Planned"
    RunStatus.IN_PROGRESS -> "In progress"
    RunStatus.COMPLETED -> "Completed"
    RunStatus.CANCELLED -> "Cancelled"
}

@Composable
private fun RiderOutboxScreen() {
    val vm = remember { ThapsusSdk.outboxViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.refresh() }

    val pending by vm.pending.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val lastFlushed by vm.lastFlushed.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EyebrowPill(label = "Last mile")
        EditorialHeader(
            title = "Outbox",
            subtitle = "Offline POD events waiting to sync."
        )
        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pending events", color = Brand.cream.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(
                    pending.toString(),
                    color = Brand.cream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 56.sp
                )
                lastFlushed?.let {
                    Text(
                        "Last flush sent $it events.",
                        color = Brand.cream.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
        InkButton(
            text = if (busy) "Syncing…" else "Flush now",
            enabled = !busy && pending > 0,
            onClick = { vm.flushNow() }
        )
    }
}

@Composable
private fun RiderAccountScreen(session: AuthSession.Authenticated, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        EyebrowPill(label = "Account")
        EditorialHeader(title = "Account", subtitle = "Rider profile.")
        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RIDER", color = Brand.Orange, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
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
