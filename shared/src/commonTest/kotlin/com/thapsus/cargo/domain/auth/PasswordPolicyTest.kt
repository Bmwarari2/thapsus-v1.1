package com.thapsus.cargo.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordPolicyTest {

    @Test
    fun empty_password_fails_first_on_min_length() {
        val rules = PasswordPolicy.check("")
        assertTrue(rules.isEmpty())
        assertFalse(PasswordPolicy.isValid(""))
        assertEquals(PasswordPolicy.Rule.MIN_LENGTH.label, PasswordPolicy.firstFailure(""))
    }

    @Test
    fun digits_only_meets_length_and_digit_but_not_letter() {
        // "12345678" — common weak password the old "8+ chars" rule
        // would have accepted; HAS_LETTER catches it now.
        val rules = PasswordPolicy.check("12345678")
        assertTrue(PasswordPolicy.Rule.MIN_LENGTH in rules)
        assertTrue(PasswordPolicy.Rule.HAS_DIGIT in rules)
        assertFalse(PasswordPolicy.Rule.HAS_LETTER in rules)
        assertEquals(PasswordPolicy.Rule.HAS_LETTER.label, PasswordPolicy.firstFailure("12345678"))
    }

    @Test
    fun letters_only_meets_length_and_letter_but_not_digit() {
        val rules = PasswordPolicy.check("alexmwangi")
        assertTrue(PasswordPolicy.Rule.MIN_LENGTH in rules)
        assertTrue(PasswordPolicy.Rule.HAS_LETTER in rules)
        assertFalse(PasswordPolicy.Rule.HAS_DIGIT in rules)
        assertEquals(PasswordPolicy.Rule.HAS_DIGIT.label, PasswordPolicy.firstFailure("alexmwangi"))
    }

    @Test
    fun short_password_fails_min_length_even_with_letter_and_digit() {
        // "ab12" passes letter + digit but not the 8-char min.
        assertEquals(PasswordPolicy.Rule.MIN_LENGTH.label, PasswordPolicy.firstFailure("ab12"))
    }

    @Test
    fun strong_password_passes_every_rule() {
        val pw = "thapsus2026"
        val rules = PasswordPolicy.check(pw)
        assertEquals(PasswordPolicy.rules.size, rules.size)
        assertTrue(PasswordPolicy.isValid(pw))
        assertNull(PasswordPolicy.firstFailure(pw))
    }

    @Test
    fun symbols_are_accepted_but_not_required() {
        // Symbols satisfy nothing on their own, but a password with
        // letter + digit + symbol still passes — we don't penalise.
        assertTrue(PasswordPolicy.isValid("alex2026!"))
    }

    @Test
    fun very_long_password_passes_until_max_length() {
        val pw = "a1" + "x".repeat(PasswordPolicy.MAX_LENGTH - 2)
        assertTrue(PasswordPolicy.isValid(pw))
        val tooLong = pw + "y"
        assertFalse(PasswordPolicy.Rule.MIN_LENGTH in PasswordPolicy.check(tooLong))
    }
}
