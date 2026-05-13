package com.thapsus.cargo.android.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import kotlinx.coroutines.launch

/**
 * Forgot-password kickoff. Mirrors iOS PasswordResetView. Sends a
 * POST /auth/forgot-password — server emails a reset link. Token-
 * consumption (POST /auth/reset-password) happens on the web page
 * the email points at, so the app only needs the kickoff flow.
 */
@Composable
fun PasswordResetScreen(onClose: () -> Unit) {
    val auth = remember { ThapsusSdk.auth() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Sign in",
            title = "Forgot password",
            subtitle = "We'll email you a link to set a new one."
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    placeholder = { Text("you@email.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        capitalization = KeyboardCapitalization.None
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OrangeButton(
                    text = if (sending) "Sending…" else "Send reset link",
                    enabled = email.trim().contains("@") && !sending,
                    onClick = {
                        sending = true
                        error = null
                        result = null
                        scope.launch {
                            auth.forgotPassword(email.trim().lowercase())
                                .onSuccess {
                                    result = "Check your inbox at ${email.trim()} for the reset link."
                                }
                                .onFailure {
                                    error = it.message ?: "Couldn't reach the server"
                                }
                            sending = false
                        }
                    }
                )
            }
        }

        result?.let { CalloutBanner(title = "Email sent", message = it) }
        error?.let { CalloutBanner(title = "Couldn't send", message = it) }

        TextButton(onClick = onClose) { Text("Back to sign in") }
        Spacer(Modifier.height(40.dp))
    }
}
