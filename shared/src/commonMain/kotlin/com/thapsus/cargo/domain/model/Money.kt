package com.thapsus.cargo.domain.model

import kotlinx.serialization.Serializable

/**
 * Per spec §11 hard rule 8: never store money as float.
 * GBP held as pence; KES held as cents. Both as Long.
 */
@Serializable
enum class Currency { GBP, KES }

@Serializable
data class Money(
    val minor: Long,
    val currency: Currency
) {
    operator fun plus(other: Money): Money {
        require(other.currency == currency) { "Currency mismatch: $currency vs ${other.currency}" }
        return copy(minor = minor + other.minor)
    }

    operator fun minus(other: Money): Money {
        require(other.currency == currency) { "Currency mismatch: $currency vs ${other.currency}" }
        return copy(minor = minor - other.minor)
    }

    operator fun times(factor: Int): Money = copy(minor = minor * factor)

    /** Multiply by a real-valued factor (rate, percent). Banker's rounding to nearest minor unit. */
    fun applyRate(rate: Double): Money {
        val raw = minor.toDouble() * rate
        return copy(minor = kotlin.math.round(raw).toLong())
    }

    val major: Double get() = minor / 100.0

    companion object {
        fun gbp(pence: Long) = Money(pence, Currency.GBP)
        fun kes(cents: Long) = Money(cents, Currency.KES)
        fun gbpFromMajor(pounds: Double) = Money(kotlin.math.round(pounds * 100.0).toLong(), Currency.GBP)
        fun kesFromMajor(shillings: Double) = Money(kotlin.math.round(shillings * 100.0).toLong(), Currency.KES)
    }
}
