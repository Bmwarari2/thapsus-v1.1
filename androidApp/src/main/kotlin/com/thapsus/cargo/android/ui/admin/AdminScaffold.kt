package com.thapsus.cargo.android.ui.admin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
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
import com.thapsus.cargo.android.ui.operator.ConsolidationDetailScreen
import com.thapsus.cargo.android.ui.operator.ConsolidationListScreen
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.repository.AuthSession

private data class TabSpec(val label: String, val route: String, val icon: ImageVector)

private val adminTabs = listOf(
    TabSpec("Console", AdminRoutes.CONSOLE, Icons.Filled.Dashboard),
    TabSpec("KPI", AdminRoutes.KPI, Icons.Filled.BarChart),
    TabSpec("Consols", AdminRoutes.CONSOLS, Icons.Filled.Inventory2),
    TabSpec("Settings", AdminRoutes.SETTINGS, Icons.Filled.Settings),
    TabSpec("Account", AdminRoutes.ACCOUNT, Icons.Filled.AccountCircle)
)

@Composable
fun AdminScaffold(session: AuthSession.Authenticated, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Brand.cream) {
                adminTabs.forEach { tab ->
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
            startDestination = AdminRoutes.CONSOLE,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(AdminRoutes.CONSOLE) {
                AdminConsoleScreen(
                    onOpenUsers = { nav.navigate(AdminRoutes.USERS) },
                    onOpenPayments = { nav.navigate(AdminRoutes.PAYMENTS) },
                    onOpenCreateBfm = { nav.navigate(AdminRoutes.CREATE_BFM) },
                    onOpenIssueInvoice = { nav.navigate(AdminRoutes.ISSUE_INVOICE) },
                    onOpenCustomerConsolidations = { nav.navigate(AdminRoutes.CUSTOMER_CONSOLS) },
                    onOpenOrders = { nav.navigate(AdminRoutes.ORDERS) },
                    onOpenRevenue = { nav.navigate(AdminRoutes.REVENUE) },
                    onOpenAuditLogs = { nav.navigate(AdminRoutes.AUDIT_LOGS) },
                    onOpenErrorLogs = { nav.navigate(AdminRoutes.ERROR_LOGS) },
                    onOpenDsarQueue = { nav.navigate(AdminRoutes.DSAR_QUEUE) }
                )
            }
            composable(AdminRoutes.KPI) {
                KPIDashboardScreen()
            }
            composable(AdminRoutes.CONSOLS) {
                ConsolidationListScreen(
                    onOpenDetail = { nav.navigate(AdminRoutes.consolDetail(it)) }
                )
            }
            composable(
                route = AdminRoutes.CONSOL_DETAIL,
                arguments = listOf(navArgument("consolId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("consolId") ?: ""
                ConsolidationDetailScreen(
                    consolidationId = id,
                    operatorName = session.profile?.fullName ?: session.email ?: "Admin",
                    onBack = { nav.popBackStack() }
                )
            }
            composable(AdminRoutes.SETTINGS) {
                OpsSettingsScreen()
            }
            composable(AdminRoutes.ACCOUNT) {
                AdminAccountScreen(session = session, onSignOut = onSignOut)
            }
            composable(AdminRoutes.USERS) {
                AdminUsersScreen(
                    onBack = { nav.popBackStack() },
                    onOpenUser = { id -> nav.navigate(AdminRoutes.userDetail(id)) }
                )
            }
            composable(
                route = AdminRoutes.USER_DETAIL,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("userId") ?: ""
                AdminUserDetailScreen(userId = id, onBack = { nav.popBackStack() })
            }
            composable(AdminRoutes.PAYMENTS) {
                AdminPaymentsScreen(onBack = { nav.popBackStack() })
            }
            composable(AdminRoutes.CREATE_BFM) {
                AdminCreateBuyForMeScreen(onClose = { nav.popBackStack() })
            }
            composable(AdminRoutes.ISSUE_INVOICE) {
                AdminIssueInvoiceScreen(onClose = { nav.popBackStack() })
            }
            composable(AdminRoutes.CUSTOMER_CONSOLS) {
                AdminCustomerConsolidationsScreen(onBack = { nav.popBackStack() })
            }
            composable(AdminRoutes.ORDERS) {
                AdminOrdersScreen(
                    onBack = { nav.popBackStack() },
                    onOpenOrder = { id -> nav.navigate(AdminRoutes.orderDetail(id)) }
                )
            }
            composable(
                route = AdminRoutes.ORDER_DETAIL,
                arguments = listOf(navArgument("orderId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("orderId") ?: ""
                AdminOrderDetailScreen(orderId = id, onBack = { nav.popBackStack() })
            }
            composable(AdminRoutes.REVENUE) {
                AdminRevenueScreen(onBack = { nav.popBackStack() })
            }
            composable(AdminRoutes.AUDIT_LOGS) {
                AdminAuditLogsScreen(onBack = { nav.popBackStack() })
            }
            composable(AdminRoutes.ERROR_LOGS) {
                AdminErrorLogsScreen(onBack = { nav.popBackStack() })
            }
            composable(AdminRoutes.DSAR_QUEUE) {
                AdminDsarQueueScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
