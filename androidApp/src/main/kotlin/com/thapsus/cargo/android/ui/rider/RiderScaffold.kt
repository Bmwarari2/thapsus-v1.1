package com.thapsus.cargo.android.ui.rider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.shared.OutboxScreen
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.repository.AuthSession

private object RiderRoutes {
    const val TODAY = "rider/today"
    const val OUTBOX = "rider/outbox"
    const val ACCOUNT = "rider/account"

    const val RUN_STOPS = "rider/run/{runId}?zone={zone}"
    fun runStops(runId: String, zone: String): String {
        val encoded = java.net.URLEncoder.encode(zone, "UTF-8")
        return "rider/run/$runId?zone=$encoded"
    }

    // POD capture — parcel ids are joined with `,` so a multi-parcel
    // bundle can travel through a single path arg. Zone + recipient
    // are URL-encoded so spaces / non-ascii survive the route lookup.
    const val POD_CAPTURE =
        "rider/run/{runId}/pod?parcelIds={parcelIds}&zone={zone}&recipient={recipient}"
    fun podCapture(runId: String, zone: String, parcelIds: List<String>, recipient: String): String {
        val ids = parcelIds.joinToString(",")
        val z = java.net.URLEncoder.encode(zone, "UTF-8")
        val r = java.net.URLEncoder.encode(recipient, "UTF-8")
        return "rider/run/$runId/pod?parcelIds=$ids&zone=$z&recipient=$r"
    }
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
            composable(RiderRoutes.TODAY) {
                RiderRunsScreen(
                    riderId = session.userId,
                    onOpenRun = { run -> nav.navigate(RiderRoutes.runStops(run.id, run.zone)) }
                )
            }
            composable(
                route = RiderRoutes.RUN_STOPS,
                arguments = listOf(
                    navArgument("runId") { type = NavType.StringType },
                    navArgument("zone") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val runId = entry.arguments?.getString("runId") ?: ""
                val rawZone = entry.arguments?.getString("zone") ?: ""
                val zone = runCatching { java.net.URLDecoder.decode(rawZone, "UTF-8") }
                    .getOrDefault(rawZone)
                RunStopListScreen(
                    runId = runId,
                    zone = zone,
                    onClose = { nav.popBackStack() },
                    onOpenPod = { parcelIds, recipient ->
                        nav.navigate(RiderRoutes.podCapture(runId, zone, parcelIds, recipient))
                    }
                )
            }
            composable(
                route = RiderRoutes.POD_CAPTURE,
                arguments = listOf(
                    navArgument("runId") { type = NavType.StringType },
                    navArgument("parcelIds") { type = NavType.StringType; defaultValue = "" },
                    navArgument("zone") { type = NavType.StringType; defaultValue = "" },
                    navArgument("recipient") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                val runId = entry.arguments?.getString("runId") ?: ""
                val rawZone = entry.arguments?.getString("zone") ?: ""
                val rawRecipient = entry.arguments?.getString("recipient") ?: ""
                val parcelIdsCsv = entry.arguments?.getString("parcelIds") ?: ""
                val zone = runCatching { java.net.URLDecoder.decode(rawZone, "UTF-8") }
                    .getOrDefault(rawZone)
                val recipient = runCatching { java.net.URLDecoder.decode(rawRecipient, "UTF-8") }
                    .getOrDefault(rawRecipient)
                val ids = parcelIdsCsv.split(',').filter { it.isNotBlank() }
                PodCaptureScreen(
                    runId = runId,
                    runZone = zone,
                    riderId = session.userId,
                    parcelIds = ids,
                    defaultRecipient = recipient,
                    onClose = { nav.popBackStack() }
                )
            }
            composable(RiderRoutes.OUTBOX) { OutboxScreen() }
            composable(RiderRoutes.ACCOUNT) { RiderAccountScreen(session, onSignOut) }
        }
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
