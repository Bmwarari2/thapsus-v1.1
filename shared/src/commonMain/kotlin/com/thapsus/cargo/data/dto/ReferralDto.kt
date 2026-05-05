package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /api/referral` returns nested `{ referral: { statistics: {...} }, referred_users: [...] }`.
 * Numeric fields are pg-aggregates (`COUNT(*)`, `SUM(CASE)`) → string-encoded by
 * pg-node, null on empty tables. All wrapped with Loose serializers.
 */
@Serializable
data class ReferralSummaryResponse(
    val success: Boolean = true,
    @SerialName("referral") val referral: ReferralBlock = ReferralBlock(),
    @SerialName("referred_users") val referredUsers: List<ReferredUserDto> = emptyList()
)

@Serializable
data class ReferralBlock(
    @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("current_balance")
    @Serializable(with = LooseDoubleSerializer::class)
    val currentBalance: Double = 0.0,
    @SerialName("statistics") val statistics: ReferralStatistics = ReferralStatistics()
)

@Serializable
data class ReferralStatistics(
    @SerialName("total_referrals")
    @Serializable(with = LooseIntSerializer::class)
    val totalReferrals: Int = 0,
    @SerialName("completed_referrals")
    @Serializable(with = LooseIntSerializer::class)
    val completedReferrals: Int = 0,
    @SerialName("pending_referrals")
    @Serializable(with = LooseIntSerializer::class)
    val pendingReferrals: Int = 0,
    @SerialName("total_earned")
    @Serializable(with = LooseDoubleSerializer::class)
    val totalEarned: Double = 0.0
)

@Serializable
data class ReferredUserDto(
    @SerialName("id") val id: String,
    @SerialName("referee_id") val refereeId: String? = null,
    @SerialName("referee_name") val refereeName: String? = null,
    @SerialName("referee_email") val refereeEmail: String? = null,
    @SerialName("referred_at") val referredAt: String? = null,
    @SerialName("referee_joined_at") val refereeJoinedAt: String? = null,
    @SerialName("signed_up") val signedUp: Boolean = true,
    @SerialName("first_order_placed") val firstOrderPlaced: Boolean = false,
    @SerialName("orders_count")
    @Serializable(with = LooseIntSerializer::class)
    val ordersCount: Int = 0,
    @SerialName("reward_status") val rewardStatus: String = "pending",
    @SerialName("reward_amount")
    @Serializable(with = LooseDoubleSerializer::class)
    val rewardAmount: Double = 0.0,
    @SerialName("completed_at") val completedAt: String? = null
)

/** Convenience accessors so older SwiftUI sites that read flat fields keep compiling. */
val ReferralSummaryResponse.referralCode: String? get() = referral.referralCode
val ReferralSummaryResponse.totalReferrals: Int get() = referral.statistics.totalReferrals
val ReferralSummaryResponse.totalEarningsKes: Double get() = referral.statistics.totalEarned
val ReferralSummaryResponse.pendingCount: Int get() = referral.statistics.pendingReferrals
val ReferralSummaryResponse.completedCount: Int get() = referral.statistics.completedReferrals

/** Same compatibility shims for the history rows. */
val ReferredUserDto.status: String get() = rewardStatus
val ReferredUserDto.createdAt: String? get() = referredAt

/** `GET /api/referral/history` — paginated. */
@Serializable
data class ReferralHistoryResponse(
    val success: Boolean = true,
    @SerialName("referrals") val referrals: List<ReferredUserDto> = emptyList(),
    @SerialName("pagination") val pagination: PaginationDto = PaginationDto()
)

/** Shared pagination shape used by every paginated Express list response. */
@Serializable
data class PaginationDto(
    @Serializable(with = LooseIntSerializer::class) val page: Int = 1,
    @Serializable(with = LooseIntSerializer::class) val limit: Int = 10,
    @Serializable(with = LooseIntSerializer::class) val total: Int = 0,
    @SerialName("totalPages")
    @Serializable(with = LooseIntSerializer::class) val totalPages: Int = 0
)

/**
 * Old flat-shape `ReferralEntryDto` used by `ReferralView.swift`'s history list.
 * Aliased to the new `ReferredUserDto` so existing call sites compile while the
 * UI catches up to the wire shape.
 */
typealias ReferralEntryDto = ReferredUserDto
