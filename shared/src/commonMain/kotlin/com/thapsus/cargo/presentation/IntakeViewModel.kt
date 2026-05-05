package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.OrderDimensionsDto
import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.dto.ScreeningResult
import com.thapsus.cargo.data.repository.PackageRepository
import com.thapsus.cargo.domain.model.ParcelDimensions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Operator intake at Stockport hub (spec §4.3). Routes through the Express
 * `POST /api/ops/parcels/:id/receive` endpoint so the server recomputes
 * chargeable weight, flips order status, and stamps `photographed_at`.
 * The DTO `existing` is the matching `packages` row whose `order_id` we
 * post against — `packages.id` is the iOS-side cache id, but the receive
 * endpoint keys on the order id (`packages.order_id`).
 */
class IntakeViewModel(
    private val operatorId: String,
    private val packages: PackageRepository
) : SharedViewModel() {

    private val _state = MutableStateFlow<IntakeState>(IntakeState.Idle)
    val state: StateFlow<IntakeState> = _state.asStateFlow()

    fun onBarcodeScanned(barcode: String) {
        scope.launch {
            _state.value = IntakeState.Looking(barcode)
            val match = packages.fetchByBarcode(barcode)
            _state.value = if (match != null) {
                IntakeState.Matched(match)
            } else {
                IntakeState.Unmatched(barcode)
            }
        }
    }

    /**
     * Phase C — operator picks a pre-registered parcel from the list rather
     * than scanning. Skips the barcode lookup and jumps straight to Matched
     * so the measurements/print sheet can present immediately.
     */
    fun selectExisting(pkg: PackageDto) {
        _state.value = IntakeState.Matched(pkg)
    }

    fun submitMeasurements(
        existing: PackageDto,
        dims: ParcelDimensions,
        photoUrl: String?,
        @Suppress("UNUSED_PARAMETER") screening: ScreeningResult,
        barcode: String? = null,
        customsDutyKes: Double? = null,
        hsTier: String? = null
    ) {
        // Receive endpoint keys on order_id. PackageDto.orderId is the linked
        // order uuid; fall back to id for legacy/orphan rows where the two
        // were aligned by the 2026-04-29 backfill.
        val orderId = existing.orderId ?: existing.id
        val outboundBarcode = barcode ?: existing.barcode
        scope.launch {
            _state.value = IntakeState.Submitting
            packages.receive(
                orderId = orderId,
                weightKg = dims.actualKg,
                dimensions = OrderDimensionsDto(
                    lengthCm = dims.lengthCm,
                    widthCm = dims.widthCm,
                    heightCm = dims.heightCm
                ),
                photoUrl = photoUrl,
                barcode = outboundBarcode,
                customsDuty = customsDutyKes,
                hsTier = hsTier
            )
                .onSuccess { resp ->
                    val updated = existing.copy(
                        lengthCm = dims.lengthCm,
                        widthCm = dims.widthCm,
                        heightCm = dims.heightCm,
                        actualKg = resp.weightKg ?: dims.actualKg,
                        volumetricKg = resp.volumetricKg,
                        chargeableKg = resp.chargeableKg,
                        photoUrl = photoUrl ?: existing.photoUrl,
                        barcode = outboundBarcode ?: existing.barcode
                    )
                    _state.value = IntakeState.Done(updated)
                }
                .onFailure { _state.value = IntakeState.Failed(it.message ?: "Couldn't record receipt") }
        }
    }

    fun reset() { _state.value = IntakeState.Idle }
}

sealed interface IntakeState {
    data object Idle : IntakeState
    data class Looking(val barcode: String) : IntakeState
    data class Matched(val pkg: PackageDto) : IntakeState
    data class Unmatched(val barcode: String) : IntakeState
    data object Submitting : IntakeState
    data class Done(val pkg: PackageDto) : IntakeState
    data class Failed(val message: String) : IntakeState
}
