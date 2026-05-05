package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.dto.PackageStatus
import com.thapsus.cargo.data.repository.PackageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives /app dashboard (spec §3.2): open parcels, this week's cut-off countdown,
 * recent activity. Backed by the offline cache so the dashboard renders instantly
 * on cold-launch even with no network.
 */
class CustomerDashboardViewModel(
    private val userId: String,
    private val packages: PackageRepository
) : SharedViewModel() {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val state: StateFlow<DashboardState> = packages.observeForUser(userId)
        .map { all -> DashboardState.from(all) }
        .stateIn(scope, SharingStarted.Eagerly, DashboardState.empty())

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _refreshing.value = true
            packages.refreshForUser(userId).onFailure { _error.value = it.message }
            _refreshing.value = false
        }
    }

    fun dismissError() { _error.value = null }
}

data class DashboardState(
    val totalParcels: Int,
    val inFlightParcels: Int,
    val awaitingDuty: Int,
    val outForDelivery: Int,
    val recentParcels: List<PackageDto>
) {
    companion object {
        fun empty() = DashboardState(0, 0, 0, 0, emptyList())

        fun from(all: List<PackageDto>): DashboardState {
            val inFlight = all.count {
                it.status in setOf(
                    PackageStatus.IN_TRANSIT,
                    PackageStatus.JKIA_ARRIVED,
                    PackageStatus.MANIFESTED
                )
            }
            val awaitDuty = all.count { it.status == PackageStatus.AWAITING_DUTY_PAYMENT }
            val outForDelivery = all.count { it.status == PackageStatus.OUT_FOR_DELIVERY }
            return DashboardState(
                totalParcels = all.size,
                inFlightParcels = inFlight,
                awaitingDuty = awaitDuty,
                outForDelivery = outForDelivery,
                recentParcels = all.take(10)
            )
        }
    }
}
