package com.thapsus.cargo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun pence_addition_does_not_lose_precision() {
        val a = Money.gbp(199)
        val b = Money.gbp(801)
        assertEquals(1000L, (a + b).minor)
    }

    @Test
    fun cross_currency_arithmetic_throws() {
        val gbp = Money.gbp(100)
        val kes = Money.kes(100)
        assertFailsWith<IllegalArgumentException> { gbp + kes }
    }

    @Test
    fun apply_rate_uses_half_to_even_rounding_to_minor() {
        val pounds = Money.gbpFromMajor(10.00) // 1000p
        // 1000p * 0.0199 = 19.9 → rounded to 20p
        assertEquals(20L, pounds.applyRate(0.0199).minor)
    }

    @Test
    fun major_returns_pounds_or_shillings() {
        assertEquals(12.34, Money.gbp(1234).major, absoluteTolerance = 1e-9)
        assertEquals(99.50, Money.kes(9950).major, absoluteTolerance = 1e-9)
    }
}
