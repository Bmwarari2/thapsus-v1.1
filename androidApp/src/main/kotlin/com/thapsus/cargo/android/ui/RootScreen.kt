package com.thapsus.cargo.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.auth.SignInScreen
import com.thapsus.cargo.android.ui.nav.RoleTabsScreen
import com.thapsus.cargo.android.ui.primitives.AppBackground
import com.thapsus.cargo.android.ui.primitives.BrandWordmark
import com.thapsus.cargo.android.ui.primitives.WordmarkSize
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.repository.AuthSession

/**
 * Top-level gate. Mirrors `RootView.swift`: splash while `AuthSession.Initializing`,
 * `SignInScreen` when signed out, role-aware tabs once authenticated.
 */
@Composable
fun RootScreen() {
    val authVm = remember { ThapsusSdk.authViewModel() }
    DisposableEffect(authVm) {
        onDispose { authVm.clear() }
    }

    val session by authVm.session.collectAsStateWithLifecycle()

    AppBackground {
        AnimatedContent(
            targetState = session::class,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "auth-gate"
        ) {
            when (val s = session) {
                is AuthSession.Initializing -> Splash()
                is AuthSession.SignedOut -> SignInScreen(authVm)
                is AuthSession.Authenticated -> RoleTabsScreen(session = s, onSignOut = { authVm.signOut() })
            }
        }
    }
}

@Composable
private fun Splash() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandWordmark(size = WordmarkSize.Large)
            CircularProgressIndicator(color = Brand.ink)
        }
    }
}
