package com.thapsus.cargo.android.ui

import android.app.Activity
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.auth.SignInScreen
import com.thapsus.cargo.android.ui.customer.AddressCaptureSheet
import com.thapsus.cargo.android.ui.customer.rememberAddressPromptStore
import com.thapsus.cargo.android.ui.nav.RoleTabsScreen
import com.thapsus.cargo.android.ui.onboarding.OnboardingScreen
import com.thapsus.cargo.android.ui.onboarding.rememberOnboardingStore
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
    val onboardingStore = rememberOnboardingStore()

    // PR Q — Universal Link handler for the activation email. MainActivity
    // is single-task, so a tap on https://thapsus.uk/verify-email?token=…
    // re-resolves into this same composition with the URI on the activity
    // intent. The launched effect consumes the token exactly once per
    // intent (guarded by a setData(null) so a recomposition doesn't
    // re-submit) and dispatches to AuthViewModel.verifyEmail — on success
    // the session flips to Authenticated and the gate above transitions
    // into RoleTabsScreen automatically.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val intent = activity.intent ?: return@LaunchedEffect
        if (intent.action != Intent.ACTION_VIEW) return@LaunchedEffect
        val data = intent.data ?: return@LaunchedEffect
        val pathOk = data.path == "/verify-email" || data.path?.startsWith("/verify-email") == true
        if (!pathOk) return@LaunchedEffect
        val token = data.getQueryParameter("token") ?: return@LaunchedEffect
        // Same hex-shape guard the iOS handler uses — 64 lowercase hex
        // chars from crypto.randomBytes(32) on the server.
        if (!Regex("^[a-f0-9]{64}$").matches(token)) return@LaunchedEffect
        intent.data = null
        authVm.verifyEmail(token)
    }

    // First-launch gate. Reads the persisted flag on every recomposition
    // (cheap SharedPreferences-backed mutableState) and surfaces the
    // walkthrough until the user taps "Get started". Reinstalling clears
    // SharedPreferences and re-shows it. Mirrors iOS RootView gate.
    if (!onboardingStore.isCompleted) {
        OnboardingScreen(onComplete = { onboardingStore.complete() })
        return
    }

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

    // Post-auth address prompt — surfaces once when the authenticated
    // user has no delivery_address on file and hasn't previously
    // dismissed the prompt. Saving the address (here or via
    // ProfileEditScreen) updates the session profile, so this branch
    // stops short on the next recomposition. Mirrors the iOS RootView
    // gate semantics.
    val addressPromptStore = rememberAddressPromptStore()
    val authenticatedProfile = (session as? AuthSession.Authenticated)?.profile
    val needsAddress = authenticatedProfile != null &&
        authenticatedProfile.deliveryAddress.orEmpty().trim().isEmpty() &&
        !addressPromptStore.isDismissed
    if (needsAddress) {
        AddressCaptureSheet(onResolved = { addressPromptStore.markDismissed() })
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
