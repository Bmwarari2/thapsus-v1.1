package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Scale
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

// BFM-primary tab order — mirrors iOS RootTabView for `.customer`.
// Old Tracking + Wallet tabs collapsed into the Activity hub.
private val customerTabs = listOf(
    TabSpec("Home", CustomerRoutes.HOME, Icons.Filled.Home),
    TabSpec("Shop", CustomerRoutes.SHOP, Icons.Filled.AutoAwesome),
    TabSpec("Activity", CustomerRoutes.ACTIVITY, Icons.Filled.Inbox),
    TabSpec("Quote", CustomerRoutes.QUOTE, Icons.Filled.Scale),
    TabSpec("Account", CustomerRoutes.ACCOUNT, Icons.Filled.AccountCircle)
)

/**
 * Scaffold for the customer role. Bottom nav swaps between the five top-level
 * customer routes (BFM-primary pivot: Home · Shop · Activity · Quote · Account),
 * and sub-screens (NewOrder, ParcelDetail, Notifications, ProfileEdit, Wallet,
 * Tracking) push onto the same back stack so the bottom nav stays visible while
 * they're showing.
 */
@Composable
fun CustomerScaffold(
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
                customerTabs.forEach { tab ->
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
            startDestination = CustomerRoutes.HOME,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(CustomerRoutes.HOME) {
                HomeScreen(
                    session = session,
                    onOpenBuyForMe = { nav.navigate(CustomerRoutes.SHOP) },
                    onOpenPreRegister = { nav.navigate(CustomerRoutes.NEW_ORDER) },
                    onOpenParcel = { nav.navigate(CustomerRoutes.parcelDetail(it)) },
                    onOpenNotifications = { nav.navigate(CustomerRoutes.NOTIFICATIONS) }
                )
            }
            composable(CustomerRoutes.SHOP) {
                BuyForMeScreen()
            }
            composable(CustomerRoutes.ACTIVITY) {
                ActivityHubScreen(
                    onOpenTracking = { nav.navigate(CustomerRoutes.TRACKING) },
                    onOpenPreRegister = { nav.navigate(CustomerRoutes.NEW_ORDER) },
                    onOpenInvoices = { nav.navigate(CustomerRoutes.INVOICES) },
                    onOpenTransactions = { nav.navigate(CustomerRoutes.TRANSACTIONS) }
                )
            }
            composable(CustomerRoutes.QUOTE) {
                QuoteScreen()
            }
            composable(CustomerRoutes.ACCOUNT) {
                AccountScreen(
                    session = session,
                    onSignOut = onSignOut,
                    onOpenNewOrder = { nav.navigate(CustomerRoutes.NEW_ORDER) },
                    onOpenNotifications = { nav.navigate(CustomerRoutes.NOTIFICATIONS) },
                    onOpenProfileEdit = { nav.navigate(CustomerRoutes.PROFILE_EDIT) },
                    onOpenCredit = { nav.navigate(CustomerRoutes.CREDIT) },
                    onOpenTransactions = { nav.navigate(CustomerRoutes.TRANSACTIONS) },
                    onOpenConsolidations = { nav.navigate(CustomerRoutes.CONSOLIDATIONS) },
                    onOpenInvoices = { nav.navigate(CustomerRoutes.INVOICES) },
                    onOpenWarehouseAddress = { nav.navigate(CustomerRoutes.WAREHOUSE) }
                )
            }
            composable(CustomerRoutes.TRACKING) {
                TrackingScreen(
                    userId = session.userId,
                    onOpenParcel = { nav.navigate(CustomerRoutes.parcelDetail(it)) }
                )
            }
            composable(CustomerRoutes.WALLET) {
                // Legacy wallet route now renders CreditCenterScreen — the
                // wallet was decomposed into Credit + Transactions in mig-028.
                CreditCenterScreen()
            }
            composable(CustomerRoutes.CREDIT) {
                CreditCenterScreen()
            }
            composable(CustomerRoutes.CONSOLIDATIONS) {
                CustomerConsolidationsScreen(userId = session.userId)
            }
            composable(CustomerRoutes.WAREHOUSE) {
                WarehouseAddressScreen(session = session)
            }
            composable(CustomerRoutes.INVOICES) {
                CustomerInvoicesScreen(
                    userId = session.userId,
                    onPayInvoice = { c ->
                        val amount = c.invoiceAmount?.toLong() ?: 0L
                        val title = c.description ?: if (c.isStandalone) "Standalone invoice" else "Shipping invoice"
                        nav.navigate(CustomerRoutes.payInvoice("consolidation", c.id, amount, title))
                    }
                )
            }
            composable(
                route = CustomerRoutes.PAY_INVOICE,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType },
                    navArgument("amount") { type = NavType.LongType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                val kind = entry.arguments?.getString("kind") ?: "consolidation"
                val id = entry.arguments?.getString("id") ?: ""
                val amount = entry.arguments?.getLong("amount") ?: 0L
                val rawTitle = entry.arguments?.getString("title") ?: ""
                val title = runCatching { java.net.URLDecoder.decode(rawTitle, "UTF-8") }.getOrDefault(rawTitle)
                PayInvoiceScreen(
                    targetKind = kind,
                    targetId = id,
                    targetTitle = title,
                    amountKesGross = amount,
                    onClose = { nav.popBackStack() }
                )
            }
            composable(CustomerRoutes.TRANSACTIONS) {
                TransactionsScreen()
            }
            composable(CustomerRoutes.NEW_ORDER) {
                NewOrderScreen(
                    userId = session.userId,
                    onClose = { nav.popBackStack() }
                )
            }
            composable(
                route = CustomerRoutes.PARCEL_DETAIL,
                arguments = listOf(navArgument("parcelId") { type = NavType.StringType })
            ) { entry ->
                val parcelId = entry.arguments?.getString("parcelId") ?: ""
                ParcelDetailScreen(
                    parcelId = parcelId,
                    userId = session.userId,
                    onClose = { nav.popBackStack() }
                )
            }
            composable(CustomerRoutes.NOTIFICATIONS) {
                NotificationInboxScreen(
                    userId = session.userId,
                    onClose = { nav.popBackStack() }
                )
            }
            composable(CustomerRoutes.PROFILE_EDIT) {
                ProfileEditScreen(onClose = { nav.popBackStack() })
            }
        }
    }
}
