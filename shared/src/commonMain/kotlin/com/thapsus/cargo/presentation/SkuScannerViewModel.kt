package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.OpsScannedParcelDto
import com.thapsus.cargo.data.repository.PackageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SkuScannerViewModel(
    private val packages: PackageRepository
) : SharedViewModel() {

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun onScanned(rawBarcode: String) {
        val cleaned = rawBarcode.trim().uppercase()
        if (cleaned.isEmpty()) return
        if (_state.value is State.Looking) return
        scope.launch {
            _state.value = State.Looking(cleaned)
            packages.lookupByScannedBarcode(cleaned)
                .onSuccess { parcel ->
                    _state.value = if (parcel != null) State.Found(parcel) else State.NotFound(cleaned)
                }
                .onFailure { err ->
                    _state.value = State.Failed(cleaned, err.message ?: "Lookup failed")
                }
        }
    }

    fun reset() { _state.value = State.Idle }

    sealed interface State {
        data object Idle : State
        data class Looking(val barcode: String) : State
        data class Found(val parcel: OpsScannedParcelDto) : State
        data class NotFound(val barcode: String) : State
        data class Failed(val barcode: String, val message: String) : State
    }
}
