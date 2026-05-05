package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.DsarRequestDto
import com.thapsus.cargo.data.repository.DsarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DsarViewModel(
    private val dsar: DsarRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val requests: List<DsarRequestDto>) : UiState
    }

    sealed interface FormState {
        data object Idle : FormState
        data object Submitting : FormState
        data class Submitted(val message: String) : FormState
        data class Error(val message: String) : FormState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _form = MutableStateFlow<FormState>(FormState.Idle)
    val form: StateFlow<FormState> = _form.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = UiState.Loading
            dsar.listMine()
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Failed to load DSAR requests") }
        }
    }

    fun submit(type: String, notes: String?) {
        scope.launch {
            _form.value = FormState.Submitting
            dsar.create(type, notes)
                .onSuccess {
                    _form.value = FormState.Submitted("Request received. We'll be in touch.")
                    load()
                }
                .onFailure { _form.value = FormState.Error(it.message ?: "Submission failed") }
        }
    }

    fun resetForm() { _form.value = FormState.Idle }
}
