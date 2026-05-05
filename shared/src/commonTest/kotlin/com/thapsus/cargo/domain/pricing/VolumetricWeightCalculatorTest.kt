package com.thapsus.cargo.domain.pricing

import com.thapsus.cargo.domain.model.ParcelDimensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VolumetricWeightCalculatorTest {

    @Test
    fun volumetric_uses_iata_divisor_6000() {
        val dims = ParcelDimensions(lengthCm = 60.0, widthCm = 40.0, heightCm = 30.0, actualKg = 5.0)
        val vol = VolumetricWeightCalculator.volumetricKg(dims)
        assertEquals(12.0, vol, absoluteTolerance = 1e-9)
    }

    @Test
    fun chargeable_picks_actual_when_actual_is_heavier() {
        val dims = ParcelDimensions(lengthCm = 20.0, widthCm = 20.0, heightCm = 20.0, actualKg = 10.0)
        // vol = (20*20*20)/6000 = 1.333 — actual 10kg dominates.
        val charge = VolumetricWeightCalculator.chargeableKg(dims)
        assertEquals(10.0, charge, absoluteTolerance = 1e-9)
    }

    @Test
    fun chargeable_picks_volumetric_when_box_is_light_and_bulky() {
        val dims = ParcelDimensions(lengthCm = 80.0, widthCm = 50.0, heightCm = 60.0, actualKg = 6.0)
        // vol = (80*50*60)/6000 = 40
        val charge = VolumetricWeightCalculator.chargeableKg(dims)
        assertEquals(40.0, charge, absoluteTolerance = 1e-9)
    }

    @Test
    fun breakdown_flags_when_volumetric_rules() {
        val light = ParcelDimensions(lengthCm = 80.0, widthCm = 50.0, heightCm = 60.0, actualKg = 6.0)
        val b1 = VolumetricWeightCalculator.breakdown(light)
        assertTrue(b1.volumetricRules)
        assertEquals(40.0, b1.chargeableKg, absoluteTolerance = 1e-9)

        val heavy = ParcelDimensions(lengthCm = 20.0, widthCm = 20.0, heightCm = 20.0, actualKg = 10.0)
        val b2 = VolumetricWeightCalculator.breakdown(heavy)
        assertFalse(b2.volumetricRules)
        assertEquals(10.0, b2.chargeableKg, absoluteTolerance = 1e-9)
    }

    @Test
    fun rejects_zero_or_negative_dimensions() {
        assertFailsWith<IllegalArgumentException> {
            ParcelDimensions(0.0, 10.0, 10.0, 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            ParcelDimensions(10.0, 10.0, 10.0, -1.0)
        }
    }

    @Test
    fun custom_divisor_is_supported() {
        val dims = ParcelDimensions(lengthCm = 60.0, widthCm = 40.0, heightCm = 30.0, actualKg = 5.0)
        val vol = VolumetricWeightCalculator.volumetricKg(dims, divisor = 5000.0)
        assertEquals(14.4, vol, absoluteTolerance = 1e-9)
    }
}
