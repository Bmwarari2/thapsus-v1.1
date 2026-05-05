package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.CreateOrderRequest
import com.thapsus.cargo.data.dto.InsuranceTier
import com.thapsus.cargo.data.dto.OrderDimensionsDto
import com.thapsus.cargo.data.dto.OrderDto
import com.thapsus.cargo.data.repository.OrdersRepository
import com.thapsus.cargo.data.repository.PackageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Spec §4.2: customer pre-registers a parcel before sending it to Stockport.
 *
 * Routes through `POST /api/orders` (the same endpoint the React webapp uses)
 * so the server-side side-effects fire: the matching `packages` row is created,
 * referral auto-rewards run, push events go out, and `sendOrderCreatedEmail`
 * triggers. After the order lands, we ask `PackageRepository` to refresh the
 * local SQLDelight cache from PostgREST so the dashboard shows the new row.
 */
class ParcelPreRegViewModel(
    private val userId: String,
    private val orders: OrdersRepository,
    private val packages: PackageRepository
) : SharedViewModel() {

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun submit(input: PreRegInput) {
        scope.launch {
            _state.value = State.Submitting
            // dimensions only sent when the customer filled all three sides;
            // server's volumetric calc divides by 5000 so a 0×0×0 box would
            // surface a misleading "0kg volumetric" estimate.
            val dimensions = if (input.lengthCm != null && input.widthCm != null && input.heightCm != null) {
                OrderDimensionsDto(
                    lengthCm = input.lengthCm,
                    widthCm = input.widthCm,
                    heightCm = input.heightCm
                )
            } else null
            val req = CreateOrderRequest(
                retailer = input.retailer,
                description = input.description,
                market = input.market,
                shippingSpeed = input.shippingSpeed,
                insurance = input.insuranceTier != InsuranceTier.STANDARD,
                declaredValue = input.declaredValueGbpPence / 100.0,
                weightKg = input.weightKg,
                dimensions = dimensions,
                hsTier = input.hsTier
            )
            orders.create(req)
                .onSuccess { order ->
                    // Best-effort: refresh the local cache so the dashboard
                    // picks up the auto-created `packages` row. If this fails
                    // we still consider the order saved — the cache will catch
                    // up next time the customer pulls to refresh.
                    runCatching { packages.refreshForUser(userId) }
                    _state.value = State.Saved(order)
                }
                .onFailure { _state.value = State.Failed(it.message ?: "Couldn't create order") }
        }
    }

    fun reset() { _state.value = State.Idle }

    data class PreRegInput(
        val retailer: String,
        val description: String,
        val declaredValueGbpPence: Long,
        val insuranceTier: InsuranceTier,
        val market: String = "UK",
        val shippingSpeed: String = "economy",
        val hsTier: String = "general",
        /**
         * Approximate weight + dimensions the customer can fill in if they
         * already know them. All four are optional — server can derive
         * volumetric weight from dimensions alone, and falls back to a
         * weight-only estimate when neither set is present. The intake
         * operator always re-measures at the warehouse, so these are
         * never billed values.
         */
        val weightKg: Double? = null,
        val lengthCm: Double? = null,
        val widthCm: Double? = null,
        val heightCm: Double? = null
    )

    sealed interface State {
        data object Idle : State
        data object Submitting : State
        data class Saved(val order: OrderDto) : State
        data class Failed(val message: String) : State
    }
}
