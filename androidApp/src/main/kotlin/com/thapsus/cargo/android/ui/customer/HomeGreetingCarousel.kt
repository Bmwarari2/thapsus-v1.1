package com.thapsus.cargo.android.ui.customer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.CustomerDashboardViewModel
import com.thapsus.cargo.presentation.home.HomeGreeting
import com.thapsus.cargo.presentation.home.HomeGreetingDestination
import kotlinx.coroutines.delay

/**
 * Rotating welcome carousel on the Customer Home tab — replaces the static
 * "Welcome, {firstName}" header. Sourced from `headlinePrefix` + `greetings`
 * StateFlows on [CustomerDashboardViewModel] (built in the KMP shared module
 * so iOS and Android render identical copy).
 *
 * Behaviour:
 * - Auto-advances every 5 seconds with a fade cross-transition.
 * - Resets to index 0 whenever the source list changes — keeps the most
 *   urgent greeting visible after a refresh.
 * - Single greeting renders static (no rotation, no page indicator).
 * - Tap fires [onTap] with the current greeting's destination and calls
 *   [CustomerDashboardViewModel.markGreetingSeen] so status variants drop
 *   on the next emission.
 */
@Composable
fun HomeGreetingCarousel(
    dashVm: CustomerDashboardViewModel,
    onTap: (HomeGreetingDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefix by dashVm.headlinePrefix.collectAsStateWithLifecycle()
    val greetings by dashVm.greetings.collectAsStateWithLifecycle()

    var index by remember { mutableStateOf(0) }

    // Reset to top of the freshly emitted list — important when the urgent
    // ladder shifts (e.g. user just paid an invoice; the carousel should
    // start from whatever's now most urgent rather than mid-rotation).
    LaunchedEffect(greetings) {
        if (index >= greetings.size) index = 0
    }

    // 5-second rotation. Only runs when there's more than one greeting,
    // and uses `greetings.size` in the loop guard so adding/removing
    // entries reactively pauses or starts the loop.
    LaunchedEffect(greetings.size) {
        if (greetings.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5_000)
            if (greetings.isEmpty()) break
            index = (index + 1) % greetings.size
        }
    }

    val current = greetings.getOrNull(index)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = prefix.ifBlank { "hi." },
            color = Brand.ink.copy(alpha = 0.6f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )

        AnimatedContent(
            targetState = current,
            label = "home-greeting-carousel",
            transitionSpec = { fadeIn() togetherWith fadeOut() }
        ) { greeting ->
            if (greeting != null) {
                Text(
                    text = greeting.body,
                    color = Brand.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            dashVm.markGreetingSeen(greeting.id)
                            onTap(greeting.destination)
                        }
                )
            } else {
                // Pre-bootstrap fallback — the layout shouldn't pop in.
                Text(
                    text = "ready when you are.",
                    color = Brand.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    lineHeight = 32.sp
                )
            }
        }

        if (greetings.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                repeat(greetings.size) { i ->
                    val active = i == index
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (active) 18.dp else 6.dp)
                            .background(
                                color = if (active) Brand.ink.copy(alpha = 0.85f)
                                else Brand.ink.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * Translates a [HomeGreetingDestination] from the KMP shared layer to the
 * appropriate [CustomerRoutes] string. The carousel hands the destination
 * to the scaffold which dispatches via this helper.
 *
 * Per-record deep-links (specific invoice / BFM order / ticket) fall back
 * to the corresponding list screen for v1 — the customer reaches the
 * record with one more tap from there. A follow-up can route through to
 * detail screens once those are wired with their own deep-link routes.
 */
fun HomeGreetingDestination.toCustomerRoute(): String = when (this) {
    is HomeGreetingDestination.Shop -> CustomerRoutes.SHOP
    is HomeGreetingDestination.ActivityHub -> CustomerRoutes.ACTIVITY
    is HomeGreetingDestination.Parcels -> CustomerRoutes.TRACKING
    is HomeGreetingDestination.Invoices -> CustomerRoutes.INVOICES
    is HomeGreetingDestination.Transactions -> CustomerRoutes.TRANSACTIONS
    is HomeGreetingDestination.Consolidations -> CustomerRoutes.CONSOLIDATIONS
    is HomeGreetingDestination.CreditCenter -> CustomerRoutes.CREDIT
    is HomeGreetingDestination.BuyForMeOrder -> CustomerRoutes.SHOP
    is HomeGreetingDestination.PayInvoice -> CustomerRoutes.INVOICES
    is HomeGreetingDestination.TicketDetail -> CustomerRoutes.TICKETS
    is HomeGreetingDestination.Dsar -> CustomerRoutes.DSAR
    is HomeGreetingDestination.Referral -> CustomerRoutes.REFERRAL
    is HomeGreetingDestination.NpsSurvey -> CustomerRoutes.ACTIVITY
}
