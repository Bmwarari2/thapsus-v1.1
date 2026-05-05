package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.InsuranceQuoteDto
import com.thapsus.cargo.data.dto.PolicyRowDto
import com.thapsus.cargo.data.repository.InsuranceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InsuranceViewModel(
    private val insurance: InsuranceRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val policies: List<PolicyRowDto>) : UiState
    }

    sealed interface QuoteState {
        data object Idle : QuoteState
        data object Loading : QuoteState
        data class Loaded(val quote: InsuranceQuoteDto) : QuoteState
        data class Error(val message: String) : QuoteState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _quote = MutableStateFlow<QuoteState>(QuoteState.Idle)
    val quote: StateFlow<QuoteState> = _quote.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = UiState.Loading
            insurance.listPolicies()
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Failed to load policies") }
        }
    }

    fun fetchQuote(tier: String, declaredValueGbp: Double) {
        scope.launch {
            _quote.value = QuoteState.Loading
            insurance.quote(tier, declaredValueGbp)
                .onSuccess { _quote.value = QuoteState.Loaded(it) }
                .onFailure { _quote.value = QuoteState.Error(it.message ?: "Quote failed") }
        }
    }

    fun issue(parcelId: String, tier: String, declaredValueGbp: Double, onComplete: (Boolean, String?) -> Unit) {
        scope.launch {
            insurance.issue(parcelId, tier, declaredValueGbp)
                .onSuccess { onComplete(true, null); load() }
                .onFailure { onComplete(false, it.message) }
        }
    }
}
