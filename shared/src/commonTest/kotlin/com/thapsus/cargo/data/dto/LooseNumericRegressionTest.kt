package com.thapsus.cargo.data.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks in the Phase 2 hardening: every DTO that touches a server-side
 * aggregate must accept JSON-string-encoded numbers (pg-node BIGINT/NUMERIC
 * stringification) AND JSON null (empty-table aggregates). These tests reject
 * a regression where someone removes the Loose serializer.
 */
class LooseNumericRegressionTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── ReferralSummaryResponse ────────────────────────────────────────────
    @Test
    fun referral_summary_decodes_string_aggregates() {
        val payload = """
            {
              "success": true,
              "referral": {
                "referral_code": "ABCD1234",
                "current_balance": "150.5",
                "statistics": {
                  "total_referrals": "7",
                  "completed_referrals": "3",
                  "pending_referrals": "4",
                  "total_earned": "150.5"
                }
              },
              "referred_users": []
            }
        """.trimIndent()
        val out = json.decodeFromString(ReferralSummaryResponse.serializer(), payload)
        assertEquals(7, out.referral.statistics.totalReferrals)
        assertEquals(3, out.referral.statistics.completedReferrals)
        assertEquals(150.5, out.referral.statistics.totalEarned)
        assertEquals(150.5, out.referral.currentBalance)
    }

    @Test
    fun referral_summary_decodes_null_aggregates() {
        val payload = """
            {
              "success": true,
              "referral": {
                "referral_code": "ABCD1234",
                "current_balance": null,
                "statistics": {
                  "total_referrals": null,
                  "completed_referrals": null,
                  "pending_referrals": null,
                  "total_earned": null
                }
              },
              "referred_users": []
            }
        """.trimIndent()
        val out = json.decodeFromString(ReferralSummaryResponse.serializer(), payload)
        assertEquals(0, out.referral.statistics.totalReferrals)
        assertEquals(0.0, out.referral.statistics.totalEarned)
        assertEquals(0.0, out.referral.currentBalance)
    }

    @Test
    fun referred_user_orders_count_string_or_null() {
        val asString = """{"id":"r1","orders_count":"4"}"""
        val asNull   = """{"id":"r2","orders_count":null}"""
        val a = json.decodeFromString(ReferredUserDto.serializer(), asString)
        val b = json.decodeFromString(ReferredUserDto.serializer(), asNull)
        assertEquals(4, a.ordersCount)
        assertEquals(0, b.ordersCount)
    }

    // ── PaginationDto ──────────────────────────────────────────────────────
    @Test
    fun pagination_total_string_or_null() {
        val a = json.decodeFromString(
            PaginationDto.serializer(),
            """{"page":"1","limit":"10","total":"42","totalPages":"5"}"""
        )
        assertEquals(42, a.total)
        val b = json.decodeFromString(
            PaginationDto.serializer(),
            """{"page":1,"limit":10,"total":null,"totalPages":null}"""
        )
        assertEquals(0, b.total)
        assertEquals(0, b.totalPages)
    }

    // ── NotificationListResponse ──────────────────────────────────────────
    @Test
    fun notification_unread_string_or_null() {
        val a = json.decodeFromString(
            NotificationListResponse.serializer(),
            """{"success":true,"notifications":[],"unread":"3"}"""
        )
        assertEquals(3, a.unread)
        val b = json.decodeFromString(
            NotificationListResponse.serializer(),
            """{"success":true,"notifications":[],"unread":null}"""
        )
        assertEquals(0, b.unread)
    }

    // ── WalletDto + TransactionDto (removed) ────────────────────────────────
    // Tests removed as part of the pricing-model PR — WalletDto and
    // TransactionDto were dropped when migration 028 retired the wallet model
    // in favour of user_credits + credit_ledger. The LooseNumericSerializer
    // these tests guarded is still exercised by the other DTOs in this file.

    // ── ConsolidationDto ──────────────────────────────────────────────────
    @Test
    fun consolidation_totals_string_or_null() {
        val payload = """
            {
              "id":"c1","week_start":"2026-04-28","cutoff_at":"2026-04-30T18:00:00Z",
              "total_kg":"250.75","total_parcels":"42"
            }
        """.trimIndent()
        val c = json.decodeFromString(ConsolidationDto.serializer(), payload)
        assertEquals(250.75, c.totalKg)
        assertEquals(42, c.totalParcels)
    }

    // ── KpiBlock ──────────────────────────────────────────────────────────
    @Test
    fun kpi_handles_null_optionals_and_string_totals() {
        val payload = """
            {
              "kg_this_week":"125.4",
              "kg_last_week":"100",
              "kg_trend_pct":null,
              "parcels_this_week":"7",
              "on_time_pct":null,
              "complaints_per_100":"0.50",
              "nps_avg":null,
              "nps_responses":"0",
              "wallet_kes":"123456",
              "pending_inbound":"5000",
              "insurance_claims_gbp":"0",
              "insurance_premiums_gbp":"0"
            }
        """.trimIndent()
        val k = json.decodeFromString(KpiBlock.serializer(), payload)
        assertEquals(125.4, k.kgThisWeek)
        assertEquals(7, k.parcelsThisWeek)
        assertNull(k.kgTrendPct)
        assertNull(k.onTimePct)
        assertNull(k.npsAvg)
    }

    // ── NpsSummaryBlock ───────────────────────────────────────────────────
    @Test
    fun nps_summary_decodes_filter_aggregates() {
        val payload = """
            {"total":"0","avg_score":null,"promoters":"0","passives":"0","detractors":"0","nps":null}
        """.trimIndent()
        val s = json.decodeFromString(NpsSummaryBlock.serializer(), payload)
        assertEquals(0, s.total)
        assertNull(s.avgScore)
        assertNull(s.nps)
    }

    // ── OpsTodayBlock ─────────────────────────────────────────────────────
    @Test
    fun ops_today_decodes_zero_filled_counts() {
        val payload = """
            {"expected":"0","received":"0","consolidating":null,"in_transit":null,"held":"3"}
        """.trimIndent()
        val t = json.decodeFromString(OpsTodayBlock.serializer(), payload)
        assertEquals(0, t.expected)
        assertEquals(0, t.consolidating)
        assertEquals(3, t.held)
    }

    // ── PackageDto.declared_value_gbp_pence ───────────────────────────────
    @Test
    fun package_declared_value_pence_accepts_string() {
        val raw = """
            {"id":"p1","user_id":"u1","declared_value_gbp_pence":"1500"}
        """.trimIndent()
        val p = json.decodeFromString(PackageDto.serializer(), raw)
        assertEquals(1500L, p.declaredValueGbpPence)
    }

    // ── TrackingDto nullable doubles (slimmed) ───────────────────────────
    // Original test referenced declared_value / estimated_cost / actual_cost /
    // customs_duty on TrackingDto — those fields were removed when the DTO
    // was reduced to the public tracking surface. Keep the weight-only check
    // so the LooseDoubleSerializer's null path is still locked in.
    @Test
    fun tracking_dto_weight_kg_distinguishes_null_from_zero() {
        val rawNull = """
            {"id":"o1","tracking_number":"THP-1","weight_kg":null}
        """.trimIndent()
        val t = json.decodeFromString(TrackingDto.serializer(), rawNull)
        assertNull(t.weightKg)
    }

    // ── AdminPagination (PendingTransactionRow removed) ──────────────────
    // PendingTransactionRow and PendingTransactionsResponse went away with the
    // wallet retirement (migration 028). AdminPagination's loose serializer
    // path is still worth a smoke test on its own.
    @Test
    fun admin_pagination_string_form() {
        val pag = json.decodeFromString(
            AdminPagination.serializer(),
            """{"page":"1","limit":"10","total":"103","totalPages":"11"}"""
        )
        assertEquals(103, pag.total)
    }

    // ── AdminStatsResponse loose hardening ───────────────────────────────
    @Test
    fun admin_stats_compact_response_accepts_string_aggregates() {
        val payload = """
            {"success":true,"total_users":"5","total_orders":"12","active_orders":"3",
             "delivered_orders":"9","revenue_kes":"123456.78","pending_payments":"0"}
        """.trimIndent()
        val s = json.decodeFromString(AdminStatsResponse.serializer(), payload)
        assertEquals(5, s.totalUsers)
        assertEquals(12, s.totalOrders)
        assertEquals(123456.78, s.revenueKes)
    }
}
