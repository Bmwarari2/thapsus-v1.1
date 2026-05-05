package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.dto.PackageStatus
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.local.ThapsusLocalCache.Companion.toDto
import com.thapsus.cargo.data.repository.PackageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import com.thapsus.cargo.data.remote.Tables
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/**
 * Drives the operator's `/ops/today` view (spec §3.3): parcels expected today,
 * late, ready to consolidate, in transit. Pulls every package once, then relies
 * on RealtimeSync + the local cache for live updates.
 */
class OperatorTodayViewModel(
    private val supabase: SupabaseClient,
    private val cache: ThapsusLocalCache,
    private val packages: PackageRepository
) : SharedViewModel() {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val state: StateFlow<TodayState> = cache.observeAllPackages()
        .map { rows -> TodayState.from(rows.map { it.toDto() }) }
        .stateIn(scope, SharingStarted.Eagerly, TodayState.empty())

    init { refresh() }

    fun refresh() {
        scope.launch {
            _refreshing.value = true
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                val rows = supabase.from(Tables.PACKAGES).select().decodeList<PackageDto>()
                rows.forEach { cache.upsertPackage(it, now) }
            }.onFailure { _error.value = it.message }
            _refreshing.value = false
        }
    }

    fun dismissError() { _error.value = null }
}

data class TodayState(
    val expectedToday: List<PackageDto>,
    val late: List<PackageDto>,
    val readyToConsolidate: List<PackageDto>,
    val inTransit: List<PackageDto>,
    val held: List<PackageDto>
) {
    val totalCount: Int get() =
        expectedToday.size + late.size + readyToConsolidate.size + inTransit.size + held.size

    companion object {
        /**
         * Pre-registered packages older than this threshold count as "late".
         * Customers told us they were shipping a parcel `LATE_THRESHOLD_DAYS`
         * ago and it still hasn't arrived at the warehouse — operators want
         * a queue so they can chase the customer. Threshold tuned to a week
         * because most UK retailers ship within 3-5 working days; anything
         * past 7 calendar days is genuinely overdue.
         */
        const val LATE_THRESHOLD_DAYS = 7

        fun empty() = TodayState(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        fun from(all: List<PackageDto>): TodayState {
            val now = Clock.System.now()
            val lateCutoff = now.minus(LATE_THRESHOLD_DAYS, DateTimeUnit.DAY, TimeZone.UTC)

            val preRegistered = all.filter { it.status == PackageStatus.PRE_REGISTERED }
            val (late, expected) = preRegistered.partition { p ->
                val createdInstant = p.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                createdInstant != null && createdInstant < lateCutoff
            }

            val ready = all.filter {
                it.status in setOf(
                    PackageStatus.RECEIVED_AT_WAREHOUSE,
                    PackageStatus.PHOTOGRAPHED,
                    PackageStatus.WEIGHED,
                    PackageStatus.SCREENED
                )
            }
            val inTransit = all.filter {
                it.status in setOf(
                    PackageStatus.MANIFESTED,
                    PackageStatus.IN_TRANSIT,
                    PackageStatus.JKIA_ARRIVED
                )
            }
            val held = all.filter {
                it.status in setOf(PackageStatus.HELD, PackageStatus.HELD_AT_NAIROBI_HUB)
            }
            return TodayState(
                expectedToday = expected,
                late = late,
                readyToConsolidate = ready,
                inTransit = inTransit,
                held = held
            )
        }
    }
}
