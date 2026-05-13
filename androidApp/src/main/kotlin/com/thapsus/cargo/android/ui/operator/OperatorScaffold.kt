package com.thapsus.cargo.android.ui.operator

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.repository.AuthSession

private data class TabSpec(val label: String, val route: String, val icon: ImageVector)

// BFM-primary tab order — mirrors iOS RootTabView for `.operator`.
// Today summary is reachable behind Account → Today.
private val operatorTabs = listOf(
    TabSpec("BFM", OperatorRoutes.BFM, Icons.Filled.AutoAwesome),
    TabSpec("Receive", OperatorRoutes.RECEIVE, Icons.Filled.Print),
    TabSpec("Consols", OperatorRoutes.CONSOLS, Icons.Filled.Inventory2),
    TabSpec("Dispatch", OperatorRoutes.DISPATCH, Icons.Filled.DeliveryDining),
    TabSpec("Account", OperatorRoutes.ACCOUNT, Icons.Filled.AccountCircle)
)

@Composable
fun OperatorScaffold(
    session: AuthSession.Authenticated,
    onSignOut: () -> Unit
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Brand.cream) {
                operatorTabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            val popped = nav.popBackStack(tab.route, inclusive = false)
                            if (!popped) {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) {
                                        inclusive = false
                                        saveState = false
                                    }
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
            startDestination = OperatorRoutes.BFM,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(OperatorRoutes.BFM) {
                OpsBuyForMeQueueScreen()
            }
            composable(OperatorRoutes.RECEIVE) {
                OperatorReceiveScreen(
                    operatorId = session.userId,
                    onOpenScanner = { nav.navigate(OperatorRoutes.SCANNER) }
                )
            }
            composable(OperatorRoutes.CONSOLS) {
                ConsolidationListScreen(
                    onOpenDetail = { nav.navigate(OperatorRoutes.consolDetail(it)) }
                )
            }
            composable(
                route = OperatorRoutes.CONSOL_DETAIL,
                arguments = listOf(navArgument("consolId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("consolId") ?: ""
                ConsolidationDetailScreen(
                    consolidationId = id,
                    operatorName = session.profile?.fullName ?: session.email ?: "Operator",
                    onBack = { nav.popBackStack() }
                )
            }
            composable(OperatorRoutes.DISPATCH) {
                DispatchScreen()
            }
            composable(OperatorRoutes.ACCOUNT) {
                OperatorAccountScreen(
                    session = session,
                    onSignOut = onSignOut,
                    onOpenTodaySummary = { nav.navigate(OperatorRoutes.TODAY) },
                    onOpenOutbox = { nav.navigate(OperatorRoutes.OUTBOX) },
                    onOpenClientTerminal = { nav.navigate(OperatorRoutes.CLIENT_TERMINAL) }
                )
            }
            composable(OperatorRoutes.TODAY) {
                OperatorTodayScreen(onOpenBfmQueue = {
                    val popped = nav.popBackStack(OperatorRoutes.BFM, inclusive = false)
                    if (!popped) nav.navigate(OperatorRoutes.BFM)
                })
            }
            composable(OperatorRoutes.OUTBOX) { OutboxScreen() }
            composable(OperatorRoutes.CLIENT_TERMINAL) {
                ClientTerminalScreen(session = session)
            }
            composable(OperatorRoutes.SCANNER) {
                SkuScannerScreen(onClose = { nav.popBackStack() })
            }
        }
    }
}
