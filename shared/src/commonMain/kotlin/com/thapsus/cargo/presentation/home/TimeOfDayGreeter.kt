package com.thapsus.cargo.presentation.home

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Friendly time-of-day prefix to pair with a [HomeGreeting.body].
 *
 * Buckets:
 *   05:00 – 11:59 → "good morning"
 *   12:00 – 16:59 → "good afternoon"
 *   17:00 – 21:59 → "good evening"
 *   22:00 – 04:59 → "hi"              (saying "good evening" at 3am feels off)
 *
 * The full rendered greeting is composed as
 *   "${prefix(now, tz)}, ${firstName.lowercase()}. ${body}"
 * which the UI assembles with whatever capitalisation/typography it wants.
 */
object TimeOfDayGreeter {

    /** Returns the lowercased prefix, e.g. "good morning" or "hi". */
    fun prefix(now: Instant, tz: TimeZone): String {
        val hour = now.toLocalDateTime(tz).hour
        return when (hour) {
            in 5..11 -> "good morning"
            in 12..16 -> "good afternoon"
            in 17..21 -> "good evening"
            else -> "hi" // 22:00-04:59
        }
    }

    /**
     * Renders the leading "good morning, brian." line. [firstName] is
     * lowercased to match the friendly tone; pass an empty string to
     * produce "good morning."
     */
    fun greetingLine(now: Instant, tz: TimeZone, firstName: String): String {
        val p = prefix(now, tz)
        val trimmed = firstName.trim()
        return if (trimmed.isEmpty()) "$p." else "$p, ${trimmed.lowercase()}."
    }
}
