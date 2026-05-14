package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.BuyForMeOrderDto
import com.thapsus.cargo.data.dto.CustomerConsolidationDto
import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.dto.PackageStatus
import com.thapsus.cargo.data.dto.PaymentDto
import com.thapsus.cargo.data.dto.UserDto
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.repository.AuthRepository
import com.thapsus.cargo.data.repository.AuthSession
import com.thapsus.cargo.data.repository.BuyForMeRepository
import com.thapsus.cargo.data.repository.CustomerConsolidationsRepository
import com.thapsus.cargo.data.repository.PackageRepository
import com.thapsus.cargo.data.repository.PaymentsRepository
import com.thapsus.cargo.presentation.home.HomeGreeting
import com.thapsus.cargo.presentation.home.HomeGreetingBuilder
import com.thapsus.cargo.presentation.home.HomeGreetingSnapshot
import com.thapsus.cargo.presentation.home.TimeOfDayGreeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Drives /app dashboard (spec §3.2): open parcels, this week's cut-off countdown,
 * recent activity, and the **rotating welcome carousel** ([headlinePrefix] +
 * [greetings]).
 *
 * Backed by the offline cache so the dashboard renders instantly on cold-launch
 * even with no network.
 *
 * The greeting carousel pulls from several sources — parcels (Flow), customer
 * consolidations (suspend), buy-for-me (suspend), payments (suspend), auth
 * state (Flow), and the local "seen" markers. The non-Flow inputs collapse
 * into [MutableStateFlow]s refreshed by [refresh], and the lot combines through
 * [HomeGreetingBuilder].
 */
class CustomerDashboardViewModel(
    private val userId: String,
    private val packages: PackageRepository,
    private val consolidations: CustomerConsolidationsRepository,
    private val buyForMe: BuyForMeRepository,
    private val payments: PaymentsRepository,
    private val auth: AuthRepository,
    private val cache: ThapsusLocalCache,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : SharedViewModel() {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val state: StateFlow<DashboardState> = packages.observeForUser(userId)
        .map { all -> DashboardState.from(all) }
        .stateIn(scope, SharingStarted.Eagerly, DashboardState.empty())

    // ----- Greeting sources -----

    private val consolidationsFlow = MutableStateFlow<List<CustomerConsolidationDto>>(emptyList())
    private val bfmFlow = MutableStateFlow<List<BuyForMeOrderDto>>(emptyList())
    private val paymentsFlow = MutableStateFlow<List<PaymentDto>>(emptyList())
    private val seenFlow = MutableStateFlow<Map<String, Instant>>(emptyMap())

    /** Friendly time-of-day prefix, e.g. "good morning, brian.". Recomputed every refresh. */
    private val _headlinePrefix = MutableStateFlow(
        TimeOfDayGreeter.greetingLine(clock.now(), timeZone, "")
    )
    val headlinePrefix: StateFlow<String> = _headlinePrefix.asStateFlow()

    /**
     * Priority-ordered, freshness-filtered list of greetings to show in the
     * home carousel. Always at least one entry — the fallback "ready to place
     * an order with us?" line if nothing else applies.
     */
    val greetings: StateFlow<List<HomeGreeting>> = combine(
        packages.observeForUser(userId),
        consolidationsFlow,
        bfmFlow,
        paymentsFlow,
        combine(auth.state, seenFlow) { authState, seen -> authState to seen }
    ) { parcels, cons, bfm, pays, (authState, seen) ->
        val profile = (authState as? AuthSession.Authenticated)?.profile
        val firstName = firstNameOf(profile)
        val now = clock.now()
        val snapshot = SnapshotAssembler.assemble(
            parcels = parcels,
            consolidations = cons,
            buyForMe = bfm,
            payments = pays,
            authProfile = profile,
            now = now
        )
        _headlinePrefix.value = TimeOfDayGreeter.greetingLine(now, timeZone, firstName)
        HomeGreetingBuilder.build(snapshot, seen, now)
    }.stateIn(scope, SharingStarted.Eagerly, listOf(HomeGreeting.Default))

    init {
        refresh()
        loadSeenMarkers()
    }

    fun refresh() {
        scope.launch {
            _refreshing.value = true
            packages.refreshForUser(userId).onFailure { _error.value = it.message }

            // Greeting feeds degrade gracefully — a failed pull just means
            // the corresponding greeting category stays quiet, not that the
            // home screen breaks. Errors here intentionally don't surface.
            runCatching { consolidationsFlow.value = consolidations.fetchForUser(userId) }
            buyForMe.list().onSuccess { list ->
                bfmFlow.value = list.filter { it.userId == userId }
            }
            payments.list().onSuccess { paymentsFlow.value = it }

            _refreshing.value = false
        }
    }

    /**
     * Called by the UI after the user opens the destination behind a greeting.
     * Status greetings get filtered out on the next emission; urgent greetings
     * ignore the marker and keep firing until the underlying state clears.
     */
    fun markGreetingSeen(greetingId: String) {
        scope.launch {
            val now = clock.now()
            cache.markHomeGreetingSeen(userId, greetingId, now.toEpochMilliseconds())
            seenFlow.value = seenFlow.value + (greetingId to now)
        }
    }

    fun dismissError() { _error.value = null }

    private fun loadSeenMarkers() {
        val raw = cache.homeGreetingSeenForUser(userId)
        seenFlow.value = raw.mapValues { Instant.fromEpochMilliseconds(it.value) }
    }

    private fun firstNameOf(profile: UserDto?): String {
        val full = profile?.fullName?.trim().orEmpty()
        if (full.isEmpty()) return ""
        return full.split(' ').firstOrNull().orEmpty()
    }
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

/**
 * Pulls the four input lists apart and folds them into a flat
 * [HomeGreetingSnapshot]. Kept in its own object so the heavy mapping logic
 * stays out of the view-model's combine block.
 */
private object SnapshotAssembler {

    fun assemble(
        parcels: List<PackageDto>,
        consolidations: List<CustomerConsolidationDto>,
        buyForMe: List<BuyForMeOrderDto>,
        payments: List<PaymentDto>,
        authProfile: UserDto?,
        now: Instant
    ): HomeGreetingSnapshot {
        val atHub = parcels.filter { it.status == PackageStatus.RECEIVED_AT_WAREHOUSE }
        val inTransit = parcels.filter {
            it.status in setOf(
                PackageStatus.IN_TRANSIT,
                PackageStatus.MANIFESTED,
                PackageStatus.JKIA_ARRIVED
            )
        }
        val outForDelivery = parcels.filter { it.status == PackageStatus.OUT_FOR_DELIVERY }
        val recentlyDelivered = parcels
            .filter { it.status == PackageStatus.DELIVERED }
            .mapNotNull { it.updatedAt?.let(::parseInstant) }
            .maxOrNull()

        // Invoice signals — payments rows represent owed amounts.
        //   status='pending'                    → unpaid invoice
        //   status='failed'                     → recent failure
        //   status='processing' + method='mpesa'→ STK push pending
        val urgentInvoicePayment = payments.firstOrNull { it.status == "pending" }
        val failedPayment = payments.firstOrNull { it.status == "failed" }
        val mpesaPending = payments.firstOrNull { it.status == "processing" && it.method == "mpesa" }

        val quoted = buyForMe.firstOrNull { it.status == "quoted" }
        val expiringSoon = quoted?.takeIf { isExpiringWithin24h(it, now) }
        val purchased = buyForMe.firstOrNull { it.status == "purchased" }
        val shippedToHub = buyForMe.firstOrNull { it.status == "shipped_to_uk_hub" }

        val activeConsolidation = consolidations.firstOrNull { c ->
            c.status in setOf("ready_to_ship", "in_transit", "cleared_customs", "shipped")
        }
        val (consStatus, consAt) = when (activeConsolidation?.status) {
            "ready_to_ship" ->
                HomeGreetingSnapshot.ConsolidationStatus.Ready to
                    activeConsolidation.updatedAt?.let(::parseInstant)
            "in_transit", "shipped" ->
                HomeGreetingSnapshot.ConsolidationStatus.InTransit to
                    activeConsolidation.updatedAt?.let(::parseInstant)
            "cleared_customs" ->
                HomeGreetingSnapshot.ConsolidationStatus.Cleared to
                    activeConsolidation.updatedAt?.let(::parseInstant)
            else -> null to null
        }

        val lastActivity = listOfNotNull(
            parcels.mapNotNull { it.updatedAt?.let(::parseInstant) }.maxOrNull(),
            buyForMe.mapNotNull { it.updatedAt?.let(::parseInstant) }.maxOrNull(),
            consolidations.mapNotNull { it.updatedAt?.let(::parseInstant) }.maxOrNull()
        ).maxOrNull()

        return HomeGreetingSnapshot(
            accountCreatedAt = authProfile?.createdAt?.let(::parseInstant),
            lastActivityAt = lastActivity,

            urgentInvoice = urgentInvoicePayment?.let {
                HomeGreetingSnapshot.UrgentInvoice(id = it.id, overdue = false)
            },
            failedPaymentInvoiceId = failedPayment?.id,
            mpesaPendingInvoiceId = mpesaPending?.id,
            quoteExpiringSoon = expiringSoon?.let {
                HomeGreetingSnapshot.PendingQuote(it.id, it.estimateGbp)
            },
            quoteReady = quoted?.let {
                HomeGreetingSnapshot.PendingQuote(it.id, it.estimateGbp)
            },
            ticketWithUnreadReply = null,
            dsarReady = false,

            parcelsAtHubCount = atHub.size,
            parcelsAtHubLatestAt = atHub.mapNotNull { it.updatedAt?.let(::parseInstant) }.maxOrNull(),
            outForDeliveryCount = outForDelivery.size,
            outForDeliveryLatestAt = outForDelivery.mapNotNull { it.updatedAt?.let(::parseInstant) }.maxOrNull(),
            consolidationStatus = consStatus,
            consolidationStatusAt = consAt,
            recentlyDeliveredAt = recentlyDelivered,
            bfmPurchased = purchased?.let {
                HomeGreetingSnapshot.BfmOrderRef(it.id, hostOf(it.retailerUrl))
            },
            bfmShippedToHub = shippedToHub?.let {
                HomeGreetingSnapshot.BfmOrderRef(it.id, hostOf(it.retailerUrl))
            },
            parcelsInTransitCount = inTransit.size,
            parcelsInTransitLatestAt = inTransit.mapNotNull { it.updatedAt?.let(::parseInstant) }.maxOrNull(),
            preRegisterProcessing = false,
            preRegisterAt = null,

            creditBalanceGbp = 0.0,
            referralMilestoneAt = null,
            npsPromptDue = false
        )
    }

    private fun isExpiringWithin24h(order: BuyForMeOrderDto, now: Instant): Boolean {
        // Quotes are valid for 7 days from quotedAt. If quotedAt is within 6
        // days of now, the remaining window is < 24h.
        val quotedAt = order.quotedAt?.let(::parseInstant) ?: return false
        return (now - quotedAt) > 6.days
    }

    private fun hostOf(url: String): String? {
        if (url.isBlank()) return null
        val s = url.removePrefix("http://").removePrefix("https://")
        val end = s.indexOfAny(charArrayOf('/', '?', '#'))
        val host = (if (end >= 0) s.substring(0, end) else s).removePrefix("www.")
        return host.takeIf { it.isNotBlank() }
    }

    private fun parseInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()
}
