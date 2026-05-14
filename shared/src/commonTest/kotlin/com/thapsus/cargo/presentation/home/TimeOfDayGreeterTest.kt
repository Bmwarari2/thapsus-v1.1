package com.thapsus.cargo.presentation.home

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeOfDayGreeterTest {

    private val tz = TimeZone.of("Africa/Nairobi")

    private fun localInstant(hour: Int, minute: Int = 0): Instant =
        LocalDateTime(2026, 5, 14, hour, minute, 0).toInstant(tz)

    @Test
    fun `morning bucket 05_00 to 11_59 yields good morning`() {
        assertEquals("good morning", TimeOfDayGreeter.prefix(localInstant(5, 0), tz))
        assertEquals("good morning", TimeOfDayGreeter.prefix(localInstant(8, 30), tz))
        assertEquals("good morning", TimeOfDayGreeter.prefix(localInstant(11, 59), tz))
    }

    @Test
    fun `afternoon bucket 12_00 to 16_59 yields good afternoon`() {
        assertEquals("good afternoon", TimeOfDayGreeter.prefix(localInstant(12, 0), tz))
        assertEquals("good afternoon", TimeOfDayGreeter.prefix(localInstant(15, 45), tz))
        assertEquals("good afternoon", TimeOfDayGreeter.prefix(localInstant(16, 59), tz))
    }

    @Test
    fun `evening bucket 17_00 to 21_59 yields good evening`() {
        assertEquals("good evening", TimeOfDayGreeter.prefix(localInstant(17, 0), tz))
        assertEquals("good evening", TimeOfDayGreeter.prefix(localInstant(19, 30), tz))
        assertEquals("good evening", TimeOfDayGreeter.prefix(localInstant(21, 59), tz))
    }

    @Test
    fun `late night bucket 22_00 to 04_59 yields hi (not good evening)`() {
        assertEquals("hi", TimeOfDayGreeter.prefix(localInstant(22, 0), tz))
        assertEquals("hi", TimeOfDayGreeter.prefix(localInstant(0, 15), tz))
        assertEquals("hi", TimeOfDayGreeter.prefix(localInstant(3, 30), tz))
        assertEquals("hi", TimeOfDayGreeter.prefix(localInstant(4, 59), tz))
    }

    @Test
    fun `greetingLine lowercases the name and adds the comma`() {
        assertEquals(
            "good morning, brian.",
            TimeOfDayGreeter.greetingLine(localInstant(9), tz, "Brian")
        )
        assertEquals(
            "good evening, brian.",
            TimeOfDayGreeter.greetingLine(localInstant(18), tz, "BRIAN")
        )
    }

    @Test
    fun `greetingLine with blank name drops the trailing comma`() {
        assertEquals(
            "good morning.",
            TimeOfDayGreeter.greetingLine(localInstant(9), tz, "")
        )
        assertEquals(
            "hi.",
            TimeOfDayGreeter.greetingLine(localInstant(2), tz, "   ")
        )
    }

    @Test
    fun `timezone matters — same instant gives different prefix in different zones`() {
        // 09:00 in Nairobi (UTC+3) is 06:00 in London (UTC+0) — both morning,
        // but at 13:00 Nairobi it's 10:00 London — afternoon vs morning.
        val nairobiAfternoon = localInstant(13)
        assertEquals("good afternoon", TimeOfDayGreeter.prefix(nairobiAfternoon, tz))
        assertEquals("good morning", TimeOfDayGreeter.prefix(nairobiAfternoon, TimeZone.of("Europe/London")))
    }
}
