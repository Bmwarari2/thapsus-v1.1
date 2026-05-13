package com.thapsus.cargo.domain.auth

/**
 * Single source of truth for the password rules surfaced on every
 * sign-up entry point (iOS SignInView, Android SignInScreen, future
 * web). Kept deliberately small — modern guidance (NIST SP 800-63B)
 * favours length over composition complexity, but we still require a
 * letter + a digit so a password like "12345678" doesn't sail through.
 *
 * Both platforms render the same `Rule.list` as a live checklist next
 * to the password field, and `AuthViewModel.signUp` calls
 * `firstFailure` so a submit attempt with a weak password produces a
 * specific human-readable error.
 *
 * No upper-case / symbol requirement on purpose: those rules push
 * users towards predictable substitutions ("Password1!") that don't
 * actually add entropy, and complicate paste-from-password-manager
 * flows on Android keyboards.
 */
object PasswordPolicy {

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128  // server-side ceiling — bcrypt truncates at 72 anyway

    enum class Rule(val label: String) {
        MIN_LENGTH("At least 8 characters"),
        HAS_LETTER("Includes a letter"),
        HAS_DIGIT("Includes a number")
    }

    /** Stable list iOS / Android render in order. */
    val rules: List<Rule> = Rule.entries.toList()

    /**
     * Which rules the given password satisfies. UI uses this to draw
     * a tick (passed) or a circle (pending) per rule as the user types.
     */
    fun check(password: String): Set<Rule> {
        val passed = mutableSetOf<Rule>()
        if (password.length in MIN_LENGTH..MAX_LENGTH) passed += Rule.MIN_LENGTH
        if (password.any { it.isLetter() }) passed += Rule.HAS_LETTER
        if (password.any { it.isDigit() }) passed += Rule.HAS_DIGIT
        return passed
    }

    fun isValid(password: String): Boolean = check(password).size == rules.size

    /**
     * Returns the first unmet rule's `label`, or null if everything
     * passes. AuthViewModel turns this into the FormState.Error
     * message on a submit attempt with a weak password.
     */
    fun firstFailure(password: String): String? {
        val passed = check(password)
        return rules.firstOrNull { it !in passed }?.label
    }
}
