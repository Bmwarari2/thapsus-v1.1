package com.thapsus.cargo.domain.pricing

import com.thapsus.cargo.domain.model.ParcelDimensions
import kotlin.math.max

/**
 * vol_kg = (L × W × H) / dim_divisor;  chargeable_kg = max(actual, vol).
 *
 * The divisor is admin-editable from `pricing_settings.dim_divisor`
 * (migration 029). Default 5000 matches swiftcargo-main so cross-repo
 * quotes converge. The previous hardcoded 6000 (IATA) is still accepted
 * — admin can flip back via the OpsConsole.
 */
object VolumetricWeightCalculator {

    /** Default divisor — matches the seed in migration 029. */
    const val DEFAULT_DIVISOR = 5000.0

    /** Historical IATA divisor — pre-029 thapsus used this everywhere. */
    const val IATA_AIR_DIVISOR = 6000.0

    fun volumetricKg(dims: ParcelDimensions, divisor: Double = DEFAULT_DIVISOR): Double {
        require(divisor > 0.0) { "divisor must be positive" }
        return (dims.lengthCm * dims.widthCm * dims.heightCm) / divisor
    }

    /** Returns max(actualKg, volumetricKg) — the chargeable mass that flows into pricing. */
    fun chargeableKg(dims: ParcelDimensions, divisor: Double = DEFAULT_DIVISOR): Double {
        return max(dims.actualKg, volumetricKg(dims, divisor))
    }

    fun breakdown(dims: ParcelDimensions, divisor: Double = DEFAULT_DIVISOR): Breakdown {
        val vol = volumetricKg(dims, divisor)
        val charge = max(dims.actualKg, vol)
        return Breakdown(
            actualKg = dims.actualKg,
            volumetricKg = vol,
            chargeableKg = charge,
            dimDivisor = divisor,
            volumetricRules = charge == vol && vol > dims.actualKg
        )
    }

    data class Breakdown(
        val actualKg: Double,
        val volumetricKg: Double,
        val chargeableKg: Double,
        val dimDivisor: Double,
        /** True if volumetric weight is the chargeable mass — surface this on the customer parcel page. */
        val volumetricRules: Boolean
    )
}
