package com.thapsus.cargo.android.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thapsus.cargo.android.ui.primitives.AppBackground
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.theme.Brand
import kotlinx.coroutines.launch

// First-launch walkthrough for Android — mirrors the iOS OnboardingView.
// Four unskippable pages, native Compose animations only (no Lottie / no
// new deps). Completion persisted via OnboardingStore.
//
// Content order matches the BFM-primary product narrative:
//   1. Shop & ship                            — primary
//   2. Pre-register parcels you already bought — secondary
//   3. Tracking + customs
//   4. Payment options
//
// Insurance is intentionally not mentioned (removed across all clients).
// Selection haptic on every page change via Compose's HapticFeedback
// (HapticFeedbackType.LongPress is the closest semantic equivalent to
// iOS .selection; SEGMENT_TICK requires API 30+ via View.performHapticFeedback
// which would need a wrapper — keeping it Compose-only for portability).

private data class OnboardingPage(
    val illustration: Illustration,
    val headline: String,
    val body: String
) {
    enum class Illustration { ShipArc, WarehouseDrop, StatusPills, PayMethods }
}

private val pages = listOf(
    OnboardingPage(
        OnboardingPage.Illustration.ShipArc,
        "Shop the UK, delivered to Kenya.",
        "Send us a link from any UK retailer and we'll buy on your behalf, ship it home, and handle every step in between."
    ),
    OnboardingPage(
        OnboardingPage.Illustration.WarehouseDrop,
        "Already bought something? We'll ship it for you.",
        "Send your parcels to our Preston warehouse. We consolidate, customs-clear, and deliver to your door."
    ),
    OnboardingPage(
        OnboardingPage.Illustration.StatusPills,
        "Track every step.",
        "From purchase to your door — including KRA customs clearance — visible in one place. Realtime updates, no waiting."
    ),
    OnboardingPage(
        OnboardingPage.Illustration.PayMethods,
        "Pay how you like.",
        "M-Pesa, Lipana STK push, or any card. Earn wallet credits when you refer a friend."
    ),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Fire a soft tick whenever the visible page settles on a new index.
    LaunchedEffect(pagerState.currentPage) {
        // Skip the initial settle on page 0 so we don't haptic at app launch.
        if (pagerState.currentPage > 0 || pagerState.targetPage > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) { index ->
                OnboardingPageView(page = pages[index])
            }

            // Pager indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                content = {
                    Spacer(modifier = Modifier.weight(1f))
                    repeat(pages.size) { idx ->
                        val isActive = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .width(if (isActive) 26.dp else 8.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (isActive) Brand.ink else Brand.ink.copy(alpha = 0.22f))
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            )

            // Bottom CTA — Next for pages 1-3, Get started on the final page
            val isFinal = pagerState.currentPage == pages.size - 1
            InkButton(
                text = if (isFinal) "Get started" else "Next",
                onClick = {
                    if (isFinal) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun OnboardingPageView(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.height(260.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (page.illustration) {
                OnboardingPage.Illustration.ShipArc       -> ShipArcIllustration()
                OnboardingPage.Illustration.WarehouseDrop -> WarehouseDropIllustration()
                OnboardingPage.Illustration.StatusPills   -> StatusPillsIllustration()
                OnboardingPage.Illustration.PayMethods    -> PayMethodsIllustration()
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = page.headline,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Brand.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = page.body,
            fontSize = 15.sp,
            color = Brand.ink.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// ---------- Page 1 — parcel + GB→KE arc ----------

@Composable
private fun ShipArcIllustration() {
    val transition = rememberInfiniteTransition(label = "ship-arc")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2200)),
        label = "ship-arc-progress"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val start = Offset(w * 0.18f, h * 0.72f)
            val end = Offset(w * 0.82f, h * 0.72f)
            val control = Offset(w * 0.5f, h * 0.18f)

            val path = Path().apply {
                moveTo(start.x, start.y)
                quadraticBezierTo(control.x, control.y, end.x, end.y)
            }

            drawPath(
                path = path,
                color = Brand.Orange,
                style = Stroke(
                    width = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f), phase = 0f)
                )
            )

            // Parcel travelling along the arc — quadratic Bezier interpolation.
            val t = progress
            val px = (1 - t) * (1 - t) * start.x + 2 * (1 - t) * t * control.x + t * t * end.x
            val py = (1 - t) * (1 - t) * start.y + 2 * (1 - t) * t * control.y + t * t * end.y
            drawCircle(color = Brand.ink, radius = 24f, center = Offset(px, py))
        }

        // GB / KE flag dots at the endpoints
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(top = 156.dp)
        ) {
            FlagDot("UK")
            FlagDot("KE")
        }
    }
}

@Composable
private fun FlagDot(label: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brand.ink)
    ) {
        Text(label, color = Brand.cream, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ---------- Page 2 — package landing into warehouse ----------

@Composable
private fun WarehouseDropIllustration() {
    val transition = rememberInfiniteTransition(label = "warehouse-drop")
    val drop by transition.animateFloat(
        initialValue = -160f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1400)),
        label = "warehouse-drop-y"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // Warehouse silhouette via Canvas (simple shed)
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 48.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0f, h * 0.35f)
                lineTo(w / 2f, h * 0.18f)
                lineTo(w, h * 0.35f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path, color = Brand.ink)
        }

        // Falling parcel — animates from above the silhouette into its mouth.
        Icon(
            imageVector = Icons.Filled.Inventory2,
            contentDescription = null,
            tint = Brand.Orange,
            modifier = Modifier
                .padding(bottom = 80.dp)
                .offset(y = drop.dp)
                .size(48.dp)
        )
    }
}

// ---------- Page 3 — status timeline cascade ----------

@Composable
private fun StatusPillsIllustration() {
    var visibleCount by remember { mutableIntStateOf(0) }
    val steps = listOf(
        Icons.Filled.Check to "Purchased",
        Icons.Filled.AirplanemodeActive to "In flight",
        Icons.Filled.Description to "Customs",
        Icons.Filled.LocalShipping to "Out for delivery",
        Icons.Filled.Home to "Delivered"
    )

    LaunchedEffect(Unit) {
        steps.indices.forEach { idx ->
            kotlinx.coroutines.delay(120L)
            visibleCount = idx + 1
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        steps.forEachIndexed { idx, (icon, label) ->
            val alpha by animateFloatAsState(
                targetValue = if (idx < visibleCount) 1f else 0f,
                animationSpec = tween(durationMillis = 280),
                label = "step-alpha-$idx"
            )
            val offsetX by animateFloatAsState(
                targetValue = if (idx < visibleCount) 0f else -20f,
                animationSpec = tween(durationMillis = 280),
                label = "step-offset-$idx"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = offsetX.dp.coerceAtLeast(0.dp))
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brand.ink.copy(alpha = alpha))
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = Brand.cream.copy(alpha = alpha))
                }
                Text(label, color = Brand.ink.copy(alpha = alpha), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------- Page 4 — payment methods ----------

@Composable
private fun PayMethodsIllustration() {
    val transition = rememberInfiniteTransition(label = "pay-pulse")
    val mpesaScale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1200), repeatMode = RepeatMode.Reverse),
        label = "mpesa-scale"
    )
    val cardScale by transition.animateFloat(
        initialValue = 1.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1200), repeatMode = RepeatMode.Reverse),
        label = "card-scale"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PayMethodBadge(icon = Icons.Filled.Phone, label = "M-Pesa", scale = mpesaScale)
        PayMethodBadge(icon = Icons.Filled.CreditCard, label = "Card", scale = cardScale)
    }
}

@Composable
private fun PayMethodBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    scale: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brand.ink)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Brand.cream, modifier = Modifier.size((36 * scale).dp))
        }
        Text(label, color = Brand.ink, fontWeight = FontWeight.SemiBold)
    }
}
