package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.AdminStatsFullResponse
import com.thapsus.cargo.data.dto.ErrorLogStatsResponse
import com.thapsus.cargo.data.dto.KpiBlock
import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.dto.PackageStatus
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.local.ThapsusLocalCache.Companion.toDto
import com.thapsus.cargo.data.repository.AdminRepository
import com.thapsus.cargo.data.repository.KpiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Spec §4.12 founder KPI dashboard. Cache-backed [snapshot] keeps the screen
 * live offline; [serverStats] / [errorStats] mirror the webapp KPI tab so the
 * iOS view shows the same revenue/status/error figures the founder sees on the
 * web. The two paths complement each other — the cache snapshot is per-parcel
 * (chargeable kg, on-time %), the server snapshot is per-order (revenue,
 * status breakdown) and tracks rows iOS hasn't seen via realtime yet.
 */
class KPIDashboardViewModel(
    private val cache: ThapsusLocalCache,
    private val admin: AdminRepository,
    private val kpi: KpiRepository
) : SharedViewModel() {

    val snapshot: StateFlow<KPISnapshot> = cache.observeAllPackages()
        .map { rows -> KPISnapshot.from(rows.map { it.toDto() }) }
        .stateIn(scope, SharingStarted.Eagerly, KPISnapshot.empty())

    private val _serverStats = MutableStateFlow<AdminStatsFullResponse?>(null)
    val serverStats: StateFlow<AdminStatsFullResponse?> = _serverStats.asStateFlow()

    private val _errorStats = MutableStateFlow<ErrorLogStatsResponse?>(null)
    val errorStats: StateFlow<ErrorLogStatsResponse?> = _errorStats.asStateFlow()

    /**
     * Founder-tile snapshot from `GET /api/kpi`. Distinct from [serverStats]
     * (which mirrors the webapp admin overview) — these are the founder-only
     * metrics: this/last week kg, on-time %, NPS avg, wallet KES, complaints
     * per 100, pending inbound. All numerics arrive loose-decoded so empty
     * tables (`null` / stringified BIGINT) don't crash the parser.
     */
    private val _founder = MutableStateFlow<KpiBlock?>(null)
    val founder: StateFlow<KpiBlock?> = _founder.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refresh() {
        scope.launch {
            _loading.value = true
            admin.statsFull().onSuccess { _serverStats.value = it }
            admin.errorLogStats().onSuccess { _errorStats.value = it }
            kpi.summary().onSuccess { _founder.value = it }
            _loading.value = false
        }
    }
}

data class KPISnapshot(
    val chargeableKgThisWeek: Double,
    val totalParcels: Int,
    val deliveredCount: Int,
    val inTransitCount: Int,
    val heldCount: Int,
    val onTimePercent: Double,
    val averageMarginPerKgPence: Long
) {
    companion object {
        fun empty() = KPISnapshot(0.0, 0, 0, 0, 0, 0.0, 0)

        fun from(parcels: List<PackageDto>): KPISnapshot {
            val totalKg = parcels.sumOf { it.chargeableKg ?: 0.0 }
            val delivered = parcels.count { it.status == PackageStatus.DELIVERED }
            val inTransit = parcels.count {
                it.status in setOf(
                    PackageStatus.IN_TRANSIT,
                    PackageStatus.MANIFESTED,
                    PackageStatus.JKIA_ARRIVED,
                    PackageStatus.AWAITING_DUTY_PAYMENT
                )
            }
            val held = parcels.count {
                it.status in setOf(PackageStatus.HELD, PackageStatus.HELD_AT_NAIROBI_HUB)
            }
            val resolvable = (delivered + held).coerceAtLeast(1)
            val onTime = delivered.toDouble() / resolvable.toDouble() * 100.0
            return KPISnapshot(
                chargeableKgThisWeek = totalKg,
                totalParcels = parcels.size,
                deliveredCount = delivered,
                inTransitCount = inTransit,
                heldCount = held,
                onTimePercent = onTime,
                averageMarginPerKgPence = if (totalKg > 0) (parcels.sumOf { it.declaredValueGbpPence } / totalKg).toLong() else 0L
            )
        }
    }
}
