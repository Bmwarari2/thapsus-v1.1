package com.thapsus.cargo.domain.pricing

import com.thapsus.cargo.domain.model.ParcelDimensions
import kotlin.math.max

/**
 * Spec §4.3: Vol kg = (L × W × H) / 6000; chargeable = max(actual, vol).
 *
 * IATA standard divisor of 6000 cm³/kg for general air freight.
 * This is the floor — admin can override the divisor per pricing channel later;
 * for v1 it is fixed at 6000.
 */
object VolumetricWeightCalculator {

    const val IATA_AIR_DIVISOR = 6000.0

    fun volumetricKg(dims: ParcelDimensions, divisor: Double = IATA_AIR_DIVISOR): Double {
        require(divisor > 0.0) { "divisor must be positive" }
        return (dims.lengthCm * dims.widthCm * dims.heightCm) / divisor
    }

    /**
     * Returns max(actualKg, volumetricKg) — the chargeable mass that flows into pricing.
     */
    fun chargeableKg(dims: ParcelDimensions, divisor: Double = IATA_AIR_DIVISOR): Double {
        return max(dims.actualKg, volumetricKg(dims, divisor))
    }

    fun breakdown(dims: ParcelDimensions, divisor: Double = IATA_AIR_DIVISOR): Breakdown {
        val vol = volumetricKg(dims, divisor)
        val charge = max(dims.actualKg, vol)
        return Breakdown(
            actualKg = dims.actualKg,
            volumetricKg = vol,
            chargeableKg = charge,
            volumetricRules = charge == vol && vol > dims.actualKg
        )
    }

    data class Breakdown(
        val actualKg: Double,
        val volumetricKg: Double,
        val chargeableKg: Double,
        /** True if volumetric weight is the chargeable mass — surface this on the customer parcel page. */
        val volumetricRules: Boolean
    )
}
