package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.remote.ApiException
import com.thapsus.cargo.data.repository.AuthRepository
import com.thapsus.cargo.data.repository.AuthSession
import com.thapsus.cargo.domain.auth.PasswordPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(
    private val auth: AuthRepository
) : SharedViewModel() {

    val session: StateFlow<AuthSession> = auth.state
        .stateIn(scope, SharingStarted.Eagerly, AuthSession.Initializing)

    private val _form = MutableStateFlow<FormState>(FormState.Idle)
    val form: StateFlow<FormState> = _form.asStateFlow()

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _form.value = FormState.Error("Email and password are required")
            return
        }
        scope.launch {
            _form.value = FormState.Submitting
            auth.signInWithEmail(email.trim(), password)
                .onSuccess { _form.value = FormState.Idle }
                .onFailure { t -> _form.value = FormState.Error(friendly(t), codeOf(t)) }
        }
    }

    fun signUp(
        email: String,
        password: String,
        name: String? = null,
        phone: String? = null,
        countryOfResidence: String? = null
    ) {
        if (email.isBlank()) {
            _form.value = FormState.Error("Email is required")
            return
        }
        PasswordPolicy.firstFailure(password)?.let { unmet ->
            _form.value = FormState.Error("Password: $unmet")
            return
        }
        scope.launch {
            _form.value = FormState.Submitting
            auth.signUpWithEmail(
                email = email.trim(),
                password = password,
                name = name?.trim()?.takeIf { it.isNotBlank() },
                phone = phone?.trim()?.takeIf { it.isNotBlank() },
                countryOfResidence = countryOfResidence?.trim()?.takeIf { it.isNotBlank() }
            )
                .onSuccess { result ->
                    _form.value = when (result) {
                        is AuthRepository.SignUpResult.VerificationRequired ->
                            FormState.VerificationSent(result.email)
                        AuthRepository.SignUpResult.Authenticated ->
                            FormState.Idle
                    }
                }
                .onFailure { _form.value = FormState.Error(friendly(it)) }
        }
    }

    /**
     * POST /auth/verify-email — driven by the iOS / Android Universal Link
     * handler. On success the AuthSession flips to Authenticated and
     * RootView's gate transitions automatically into the role-tabs.
     */
    fun verifyEmail(token: String) {
        scope.launch {
            _form.value = FormState.Submitting
            auth.verifyEmail(token)
                .onSuccess { _form.value = FormState.Idle }
                .onFailure { _form.value = FormState.Error(friendly(it)) }
        }
    }

    /**
     * POST /auth/resend-verification — used by the "I didn't get the
     * email" affordance on the post-sign-up check-inbox screen and by
     * the sign-in screen when the server returns `email_unverified`.
     */
    fun resendVerification(email: String) {
        scope.launch {
            _form.value = FormState.Submitting
            auth.resendVerification(email.trim())
                .onSuccess { _form.value = FormState.Sent("If your account is awaiting verification, a fresh activation email has been sent.") }
                .onFailure { _form.value = FormState.Error(friendly(it)) }
        }
    }

    fun requestPhoneOtp(phone: String) {
        scope.launch {
            _form.value = FormState.Submitting
            auth.signInWithPhoneOtp(phone)
                .onSuccess { _form.value = FormState.Sent("OTP sent.") }
                .onFailure { _form.value = FormState.Error(friendly(it)) }
        }
    }

    fun verifyPhoneOtp(phone: String, token: String) {
        scope.launch {
            _form.value = FormState.Submitting
            auth.verifyPhoneOtp(phone, token)
                .onSuccess { _form.value = FormState.Idle }
                .onFailure { _form.value = FormState.Error(friendly(it)) }
        }
    }

    fun signOut() {
        scope.launch { auth.signOut() }
    }

    /**
     * Audit W6.1 follow-up — fired by the Swift side on
     * UIApplication.willEnterForegroundNotification so a
     * long-backgrounded session that has crossed the server's 24h
     * refresh threshold rotates its sc_token before the user's next
     * tap. Errors are swallowed by AuthRepository.refreshSession
     * (Result.runCatching) — a transient network blip on resume must
     * never break the cached AuthSession.
     */
    fun refresh() {
        scope.launch { auth.refreshSession() }
    }

    fun dismissError() { _form.value = FormState.Idle }

    private fun friendly(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: "Sign-in failed. Please try again."

    /**
     * Extracts the server's machine-readable failure code if the throwable
     * is an ApiException. Currently the only code the client branches on
     * is `"email_unverified"` from /auth/login (PR N) — surfaced to the
     * sign-in UI so the generic invalid-credentials banner can be swapped
     * for a "resend activation email" affordance.
     */
    private fun codeOf(t: Throwable): String? = (t as? ApiException)?.code

    sealed interface FormState {
        data object Idle : FormState
        data object Submitting : FormState
        data class Sent(val message: String) : FormState
        /**
         * Generic auth error. [code] mirrors the server's
         * machine-readable failure code (e.g. `"email_unverified"`)
         * when set; the UI may branch on it to render specific
         * recovery affordances and falls back to [message] otherwise.
         */
        data class Error(val message: String, val code: String? = null) : FormState
        /**
         * Sign-up completed but the account is awaiting email
         * verification (server PR N). The screen layer routes this
         * to a "check your inbox" view that surfaces the email +
         * a "resend" affordance backed by [resendVerification].
         */
        data class VerificationSent(val email: String) : FormState
    }
}
