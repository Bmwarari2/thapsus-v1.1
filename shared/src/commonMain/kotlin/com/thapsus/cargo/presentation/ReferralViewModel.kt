package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.ReferralEntryDto
import com.thapsus.cargo.data.dto.ReferralSummaryResponse
import com.thapsus.cargo.data.repository.ReferralsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReferralViewModel(
    private val referrals: ReferralsRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(
            val summary: ReferralSummaryResponse,
            val history: List<ReferralEntryDto>
        ) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = UiState.Loading
            val s = referrals.summary().getOrElse {
                _state.value = UiState.Error(it.message ?: "Failed to load referrals")
                return@launch
            }
            val h = referrals.history().getOrNull()?.referrals.orEmpty()
            _state.value = UiState.Loaded(s, h)
        }
    }
}
