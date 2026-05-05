package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileEditViewModel(
    private val auth: AuthRepository
) : SharedViewModel() {

    sealed interface FormState {
        data object Idle : FormState
        data object Submitting : FormState
        data class Saved(val message: String) : FormState
        data class Error(val message: String) : FormState
    }

    private val _form = MutableStateFlow<FormState>(FormState.Idle)
    val form: StateFlow<FormState> = _form.asStateFlow()

    fun save(name: String?, phone: String?, languagePref: String?, deliveryAddress: String? = null) {
        scope.launch {
            _form.value = FormState.Submitting
            auth.updateProfile(
                name = name,
                phone = phone,
                languagePref = languagePref,
                deliveryAddress = deliveryAddress
            )
                .onSuccess { _form.value = FormState.Saved("Profile updated") }
                .onFailure { _form.value = FormState.Error(it.message ?: "Update failed") }
        }
    }

    fun changePassword(current: String, new: String) {
        scope.launch {
            _form.value = FormState.Submitting
            auth.changePassword(current, new)
                .onSuccess { _form.value = FormState.Saved("Password changed") }
                .onFailure { _form.value = FormState.Error(it.message ?: "Password change failed") }
        }
    }

    fun forgotPassword(email: String) {
        scope.launch {
            _form.value = FormState.Submitting
            auth.forgotPassword(email)
                .onSuccess { _form.value = FormState.Saved("Check your inbox for reset instructions.") }
                .onFailure { _form.value = FormState.Error(it.message ?: "Failed to send reset email") }
        }
    }

    fun reset() { _form.value = FormState.Idle }
}
