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
    fun `morning bucket 05_00 to 11_59 yields Good morning`() {
        assertEquals("Good morning", TimeOfDayGreeter.prefix(localInstant(5, 0), tz))
        assertEquals("Good morning", TimeOfDayGreeter.prefix(localInstant(8, 30), tz))
        assertEquals("Good morning", TimeOfDayGreeter.prefix(localInstant(11, 59), tz))
    }

    @Test
    fun `afternoon bucket 12_00 to 16_59 yields Good afternoon`() {
        assertEquals("Good afternoon", TimeOfDayGreeter.prefix(localInstant(12, 0), tz))
        assertEquals("Good afternoon", TimeOfDayGreeter.prefix(localInstant(15, 45), tz))
        assertEquals("Good afternoon", TimeOfDayGreeter.prefix(localInstant(16, 59), tz))
    }

    @Test
    fun `evening bucket 17_00 to 21_59 yields Good evening`() {
        assertEquals("Good evening", TimeOfDayGreeter.prefix(localInstant(17, 0), tz))
        assertEquals("Good evening", TimeOfDayGreeter.prefix(localInstant(19, 30), tz))
        assertEquals("Good evening", TimeOfDayGreeter.prefix(localInstant(21, 59), tz))
    }

    @Test
    fun `late night bucket 22_00 to 04_59 yields Hi (not Good evening)`() {
        assertEquals("Hi", TimeOfDayGreeter.prefix(localInstant(22, 0), tz))
        assertEquals("Hi", TimeOfDayGreeter.prefix(localInstant(0, 15), tz))
        assertEquals("Hi", TimeOfDayGreeter.prefix(localInstant(3, 30), tz))
        assertEquals("Hi", TimeOfDayGreeter.prefix(localInstant(4, 59), tz))
    }

    @Test
    fun `greetingLine title-cases the name and adds the comma`() {
        assertEquals(
            "Good morning, Brian.",
            TimeOfDayGreeter.greetingLine(localInstant(9), tz, "brian")
        )
        assertEquals(
            "Good evening, Brian.",
            TimeOfDayGreeter.greetingLine(localInstant(18), tz, "BRIAN")
        )
        assertEquals(
            "Hi, Brian.",
            TimeOfDayGreeter.greetingLine(localInstant(2), tz, "Brian")
        )
    }

    @Test
    fun `greetingLine with blank name drops the trailing comma`() {
        assertEquals(
            "Good morning.",
            TimeOfDayGreeter.greetingLine(localInstant(9), tz, "")
        )
        assertEquals(
            "Hi.",
            TimeOfDayGreeter.greetingLine(localInstant(2), tz, "   ")
        )
    }

    @Test
    fun `timezone matters — same instant gives different prefix in different zones`() {
        // 09:00 in Nairobi (UTC+3) is 06:00 in London (UTC+0) — both morning,
        // but at 13:00 Nairobi it's 10:00 London — afternoon vs morning.
        val nairobiAfternoon = localInstant(13)
        assertEquals("Good afternoon", TimeOfDayGreeter.prefix(nairobiAfternoon, tz))
        assertEquals("Good morning", TimeOfDayGreeter.prefix(nairobiAfternoon, TimeZone.of("Europe/London")))
    }
}
