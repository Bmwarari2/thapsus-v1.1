package com.thapsus.cargo.android.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thapsus.cargo.android.ui.theme.Brand
import kotlinx.coroutines.delay

/**
 * In-app notification banner overlay. Pinned to the top of the
 * customer scaffold so a new push / Realtime update is visible
 * without yanking the user out of context. Mirrors iOS
 * NotificationBannerView.
 *
 * P2.5 ships the visual primitive + dismiss/auto-hide logic. The
 * notifications-feed → banner wire-up (Realtime push payloads
 * trigger show()) lands when we port the notifications repository
 * subscription.
 */
@Composable
fun NotificationBanner(
    title: String?,
    message: String?,
    onClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val visible = title != null && message != null
    LaunchedEffect(title, message) {
        if (visible) {
            delay(5_000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brand.ink, RoundedCornerShape(18.dp))
                    .clickable(enabled = onClick != null) { onClick?.invoke(); onDismiss() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Brand.Orange, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title ?: "", color = Brand.cream, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(message ?: "", color = Brand.cream.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 2)
                }
            }
        }
    }
}

/**
 * Helper to drive the banner from any composable parent. Stores the
 * latest notification in local state + auto-hides after 5s.
 */
class NotificationBannerHost {
    var current by mutableStateOf<Pair<String, String>?>(null)
        private set

    fun show(title: String, message: String) {
        current = title to message
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun rememberNotificationBannerHost(): NotificationBannerHost = remember { NotificationBannerHost() }
