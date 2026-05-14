package com.thapsus.cargo.presentation.home

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Time-of-day prefix to pair with a [HomeGreeting.body].
 *
 * Buckets:
 *   05:00 – 11:59 → "Good morning"
 *   12:00 – 16:59 → "Good afternoon"
 *   17:00 – 21:59 → "Good evening"
 *   22:00 – 04:59 → "Hi"               (saying "good evening" at 3am feels off)
 *
 * The platform renders the full headline as a single sentence-cased line:
 *   "${greetingLine(now, tz, firstName)} ${body}"
 * e.g. "Good morning, Brian. Your shipment is on its way to Kenya."
 */
object TimeOfDayGreeter {

    /** Returns the sentence-cased prefix, e.g. "Good morning" or "Hi". */
    fun prefix(now: Instant, tz: TimeZone): String {
        val hour = now.toLocalDateTime(tz).hour
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hi" // 22:00-04:59
        }
    }

    /**
     * Renders the leading "Good morning, Brian." line. [firstName] gets
     * its first letter capitalised (so callers can pass "brian" or "BRIAN"
     * and still see "Brian"). An empty/blank name produces "Good morning."
     * with no comma.
     */
    fun greetingLine(now: Instant, tz: TimeZone, firstName: String): String {
        val p = prefix(now, tz)
        val cased = firstName.trim().titleCaseFirst()
        return if (cased.isEmpty()) "$p." else "$p, $cased."
    }

    /**
     * Primitive-only overload that lets Swift/Java callers compose the
     * prefix without depending on kotlinx-datetime's Foundation bridges.
     * Pass `Date().timeIntervalSince1970 * 1000` and `TimeZone.current.
     * identifier` from Swift, or `System.currentTimeMillis()` and
     * `ZoneId.systemDefault().id` from Java — both reach the same code
     * path as [greetingLine].
     */
    fun greetingLineFor(epochMs: Long, tzId: String, firstName: String): String =
        greetingLine(
            now = Instant.fromEpochMilliseconds(epochMs),
            tz = TimeZone.of(tzId),
            firstName = firstName
        )

    private fun String.titleCaseFirst(): String {
        if (isEmpty()) return this
        val head = this[0].uppercaseChar()
        val tail = substring(1).lowercase()
        return "$head$tail"
    }
}
