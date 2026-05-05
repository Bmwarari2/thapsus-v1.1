package com.thapsus.cargo.domain.model

import kotlinx.serialization.Serializable

/**
 * Operator intake captures L/W/H in cm and actual mass in kg.
 * Volumetric kg = (L × W × H) / 6000 per spec §4.3.
 */
@Serializable
data class ParcelDimensions(
    val lengthCm: Double,
    val widthCm: Double,
    val heightCm: Double,
    val actualKg: Double
) {
    init {
        require(lengthCm > 0.0) { "lengthCm must be positive" }
        require(widthCm > 0.0) { "widthCm must be positive" }
        require(heightCm > 0.0) { "heightCm must be positive" }
        require(actualKg > 0.0) { "actualKg must be positive" }
    }
}
