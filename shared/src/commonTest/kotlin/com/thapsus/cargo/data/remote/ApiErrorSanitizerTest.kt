package com.thapsus.cargo.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ApiErrorSanitizerTest {

    @Test
    fun deprecation_hint_with_post_path_is_collapsed() {
        // Real-world server message that leaked into the BFM Accept
        // banner before this fix landed.
        val raw = "Wallet accept is removed. Use POST /api/payments with target_kind=buy_for_me."
        val out = sanitize(raw, status = 410)
        assertFalse("removed" in out, "leaked technical hint: $out")
        assertFalse("POST" in out, "leaked HTTP verb: $out")
        assertFalse("/api/" in out, "leaked path: $out")
    }

    @Test
    fun customer_safe_message_passes_through_unchanged() {
        assertEquals("Email already taken", sanitize("Email already taken", 409))
        assertEquals("Insufficient balance", sanitize("Insufficient balance", 402))
        assertEquals("Order not found", sanitize("Order not found", 404))
    }

    @Test
    fun raw_sql_fragment_is_collapsed() {
        val raw = "syntax error at or near \"SELECT id FROM payments WHERE user_id = \$1\""
        val out = sanitize(raw, status = 500)
        assertFalse("SELECT" in out, "leaked SQL: $out")
        assertFalse("FROM" in out, "leaked SQL: $out")
    }

    @Test
    fun stack_trace_fragment_is_collapsed() {
        val raw = "Cannot read properties of undefined (reading 'foo') at PaymentRouter.handle(server.js:42)"
        val out = sanitize(raw, status = 500)
        assertFalse("undefined" in out, "leaked stack: $out")
        assertFalse("PaymentRouter" in out, "leaked stack: $out")
    }

    @Test
    fun null_pointer_is_collapsed() {
        val out = sanitize("NullPointerException: cannot be null", 500)
        assertFalse("Null" in out, "leaked NPE: $out")
        assertFalse("cannot be null" in out, "leaked NPE: $out")
    }

    @Test
    fun friendly_fallback_matches_status_class() {
        // Each status class gets a tone-appropriate message — sanity
        // check we don't return the same generic string for everything.
        val tech = "POST /api/payments with target_kind=buy_for_me"
        val s400 = sanitize(tech, 400)
        val s401 = sanitize(tech, 401)
        val s404 = sanitize(tech, 404)
        val s500 = sanitize(tech, 500)
        assertEquals(4, setOf(s400, s401, s404, s500).size)
    }
}
