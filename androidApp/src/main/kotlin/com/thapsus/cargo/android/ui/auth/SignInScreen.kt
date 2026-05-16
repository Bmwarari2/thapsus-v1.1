package com.thapsus.cargo.android.ui.auth

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.android.ui.primitives.BrandWordmark
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.primitives.WordmarkSize
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.domain.auth.PasswordPolicy
import com.thapsus.cargo.presentation.AuthViewModel

private data class Country(val code: String, val label: String)

private val COUNTRIES = listOf(
    Country("KE", "Kenya"),
    Country("UG", "Uganda"),
    Country("TZ", "Tanzania"),
    Country("RW", "Rwanda"),
    Country("GB", "United Kingdom"),
    Country("US", "United States"),
    Country("OTHER", "Other")
)

private const val TERMS_URL = "https://thapsus.uk/terms"
private const val PRIVACY_URL = "https://thapsus.uk/privacy"

/**
 * Email/password sign-in + sign-up. Mirrors iOS `SignInView.swift`:
 * sign-up captures full name + phone + country of residence in addition
 * to email/password, and requires explicit T&Cs / Privacy agreement
 * before submit unlocks. Sign-in mode keeps a Forgot-password link.
 *
 * Shared `AuthViewModel.signUp` already accepts the extra fields — this
 * screen just plumbs them through. Terms / Privacy links open in the
 * system browser via `Intent.ACTION_VIEW` (no Chrome Custom Tabs dep,
 * matching the BFM retailer chip pattern).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(vm: AuthViewModel) {
    val form by vm.form.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<Country?>(null) }
    var isSignUp by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var presentingForgot by remember { mutableStateOf(false) }
    var presentingTracking by remember { mutableStateOf(false) }
    val forgotSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val trackingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BrandWordmark(size = WordmarkSize.Medium)
            EyebrowPill(label = "Welcome", icon = Icons.Filled.FlightTakeoff)
            EditorialHeader(
                title = if (isSignUp) "Create account" else "UK → Kenya,\nin one weekly flight.",
                subtitle = if (isSignUp)
                    "Sign up to start shipping with Thapsus."
                else
                    "Sign in to track your shipments and manage your wallet."
            )
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isSignUp) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full name") },
                        placeholder = { Text("Alex Mwangi") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                if (isSignUp) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone") },
                        placeholder = { Text("+254…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            capitalization = KeyboardCapitalization.None
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    CountryPicker(selected = country, onSelect = { country = it })
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("••••••••") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isSignUp) {
                    PasswordRequirements(password = password)
                }

                if (!isSignUp) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { presentingForgot = true }) {
                            Text("Forgot password?", color = Brand.Orange, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (isSignUp) {
                    TermsAgreementRow(
                        agreed = agreedToTerms,
                        onToggle = { agreedToTerms = !agreedToTerms },
                        onOpenTerms = { openUrl(TERMS_URL) },
                        onOpenPrivacy = { openUrl(PRIVACY_URL) }
                    )
                }

                if (form is AuthViewModel.FormState.Submitting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Brand.ink)
                    }
                } else {
                    InkButton(
                        text = if (isSignUp) "Create account" else "Sign in",
                        enabled = !isSignUp || (agreedToTerms && PasswordPolicy.isValid(password)),
                        onClick = {
                            if (isSignUp) {
                                vm.signUp(
                                    email = email,
                                    password = password,
                                    name = fullName,
                                    phone = phone,
                                    countryOfResidence = country?.code
                                )
                            } else {
                                vm.signIn(email, password)
                            }
                        }
                    )
                }
                TextButton(
                    onClick = {
                        isSignUp = !isSignUp
                        // Always require a fresh agreement when entering sign-up.
                        agreedToTerms = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSignUp) "Already have an account? Sign in" else "New here? Create an account",
                        color = Brand.Orange
                    )
                }
            }
        }

        when (val s = form) {
            is AuthViewModel.FormState.Error -> CalloutBanner(
                title = "Couldn't sign you in",
                message = s.message
            )
            is AuthViewModel.FormState.Sent -> CalloutBanner(
                title = "Check your inbox",
                message = s.message
            )
            else -> Unit
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = { presentingTracking = true }) {
                Text(
                    "Track a shipment without signing in",
                    color = Brand.Orange,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (presentingTracking) {
        ModalBottomSheet(
            onDismissRequest = { presentingTracking = false },
            sheetState = trackingSheetState
        ) {
            PublicTrackingScreen(onClose = { presentingTracking = false })
        }
    }

    if (presentingForgot) {
        ModalBottomSheet(
            onDismissRequest = { presentingForgot = false },
            sheetState = forgotSheetState
        ) {
            PasswordResetScreen(onClose = { presentingForgot = false })
        }
    }
}

@Composable
private fun CountryPicker(selected: Country?, onSelect: (Country) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "COUNTRY",
            color = Brand.ink.copy(alpha = 0.6f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brand.cream.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .border(1.dp, Brand.ink.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selected?.label ?: "Select country",
                    color = if (selected == null) Brand.ink.copy(alpha = 0.5f) else Brand.ink,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = Brand.ink.copy(alpha = 0.6f)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                COUNTRIES.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.label) },
                        onClick = {
                            onSelect(c)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TermsAgreementRow(
    agreed: Boolean,
    onToggle: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = Brand.Orange, fontWeight = FontWeight.SemiBold)
    )
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = Brand.ink.copy(alpha = 0.85f))) {
            append("I agree to Thapsus Cargo's ")
        }
        withLink(LinkAnnotation.Clickable(tag = "TERMS", styles = linkStyle) { onOpenTerms() }) {
            append("Terms of Service")
        }
        withStyle(SpanStyle(color = Brand.ink.copy(alpha = 0.85f))) {
            append(" and ")
        }
        withLink(LinkAnnotation.Clickable(tag = "PRIVACY", styles = linkStyle) { onOpenPrivacy() }) {
            append("Privacy Policy")
        }
        withStyle(SpanStyle(color = Brand.ink.copy(alpha = 0.85f))) {
            append(".")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = agreed,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Brand.Orange,
                checkmarkColor = androidx.compose.ui.graphics.Color.White,
                uncheckedColor = Brand.ink.copy(alpha = 0.5f)
            )
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = annotated,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = 14.dp)
                .clickable(onClick = onToggle)
        )
    }
}

/**
 * Live password checklist driven by the shared `PasswordPolicy`. Each
 * rule renders as a check (passed, orange) or a hollow circle (pending,
 * muted ink). Same source-of-truth + same order as iOS, so a customer
 * who learns the rules on one platform sees the identical wording on
 * the other.
 */
@Composable
private fun PasswordRequirements(password: String) {
    val passed = remember(password) { PasswordPolicy.check(password) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PasswordPolicy.rules.forEach { rule ->
            val isPassed = rule in passed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPassed) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isPassed) Brand.Orange else Brand.ink.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    rule.label,
                    color = if (isPassed) Brand.ink else Brand.ink.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = if (isPassed) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
