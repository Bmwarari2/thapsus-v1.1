package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.repository.AuthRepository
import com.thapsus.cargo.data.repository.AuthSession
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
                .onFailure { _form.value = FormState.Error(friendly(it)) }
        }
    }

    fun signUp(
        email: String,
        password: String,
        name: String? = null,
        phone: String? = null,
        countryOfResidence: String? = null
    ) {
        if (email.isBlank() || password.length < 8) {
            _form.value = FormState.Error("Password must be 8+ characters")
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
                .onSuccess { _form.value = FormState.Sent("Check your inbox to verify.") }
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

    fun dismissError() { _form.value = FormState.Idle }

    private fun friendly(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: "Sign-in failed. Please try again."

    sealed interface FormState {
        data object Idle : FormState
        data object Submitting : FormState
        data class Sent(val message: String) : FormState
        data class Error(val message: String) : FormState
    }
}
