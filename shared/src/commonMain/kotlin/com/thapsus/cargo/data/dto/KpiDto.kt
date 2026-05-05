package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /api/kpi` — founder KPI snapshot. Every field is computed live from
 * SUM/COUNT/AVG/COUNT FILTER aggregates so all numerics are loose-decoded.
 * Several fields can come back `null` on empty tables (kg_trend_pct,
 * on_time_pct, nps_avg) — keep them nullable.
 */
@Serializable
data class KpiResponse(
    val success: Boolean = true,
    val kpi: KpiBlock = KpiBlock()
)

@Serializable
data class KpiBlock(
    @SerialName("kg_this_week")
    @Serializable(with = LooseDoubleSerializer::class) val kgThisWeek: Double = 0.0,
    @SerialName("kg_last_week")
    @Serializable(with = LooseDoubleSerializer::class) val kgLastWeek: Double = 0.0,
    @SerialName("kg_trend_pct") val kgTrendPct: Double? = null,
    @SerialName("parcels_this_week")
    @Serializable(with = LooseIntSerializer::class) val parcelsThisWeek: Int = 0,
    @SerialName("on_time_pct") val onTimePct: Double? = null,
    @SerialName("complaints_per_100")
    @Serializable(with = LooseDoubleSerializer::class) val complaintsPer100: Double = 0.0,
    @SerialName("nps_avg") val npsAvg: Double? = null,
    @SerialName("nps_responses")
    @Serializable(with = LooseIntSerializer::class) val npsResponses: Int = 0,
    @SerialName("wallet_kes")
    @Serializable(with = LooseDoubleSerializer::class) val walletKes: Double = 0.0,
    @SerialName("pending_inbound")
    @Serializable(with = LooseDoubleSerializer::class) val pendingInbound: Double = 0.0,
    @SerialName("insurance_claims_gbp")
    @Serializable(with = LooseDoubleSerializer::class) val insuranceClaimsGbp: Double = 0.0,
    @SerialName("insurance_premiums_gbp")
    @Serializable(with = LooseDoubleSerializer::class) val insurancePremiumsGbp: Double = 0.0
)

/** `GET /api/kpi/marketing` — UTM rollup + 90d retention. */
@Serializable
data class KpiMarketingResponse(
    val success: Boolean = true,
    val utm: List<UtmRowDto> = emptyList(),
    @SerialName("retention_90d") val retention90d: Retention90dDto = Retention90dDto()
)

@Serializable
data class UtmRowDto(
    val source: String = "(direct)",
    val medium: String = "(none)",
    val campaign: String = "(none)",
    @Serializable(with = LooseIntSerializer::class) val signups: Int = 0
)

@Serializable
data class Retention90dDto(
    @SerialName("cohort_size")
    @Serializable(with = LooseIntSerializer::class) val cohortSize: Int = 0,
    @SerialName("repeat_in_90d")
    @Serializable(with = LooseIntSerializer::class) val repeatIn90d: Int = 0,
    @SerialName("pct") val pct: Double? = null
)

/** `GET /api/nps/summary`. nps_avg can be null on empty tables. */
@Serializable
data class NpsSummaryResponse(
    val success: Boolean = true,
    val summary: NpsSummaryBlock = NpsSummaryBlock()
)

@Serializable
data class NpsSummaryBlock(
    @Serializable(with = LooseIntSerializer::class) val total: Int = 0,
    @SerialName("avg_score") val avgScore: Double? = null,
    @Serializable(with = LooseIntSerializer::class) val promoters: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val passives: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val detractors: Int = 0,
    val nps: Double? = null
)

/** `GET /api/ops/today`. */
@Serializable
data class OpsTodayResponse(
    val success: Boolean = true,
    val today: OpsTodayBlock = OpsTodayBlock()
)

@Serializable
data class OpsTodayBlock(
    @Serializable(with = LooseIntSerializer::class) val expected: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val received: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val consolidating: Int = 0,
    @SerialName("in_transit")
    @Serializable(with = LooseIntSerializer::class) val inTransit: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val held: Int = 0
)
