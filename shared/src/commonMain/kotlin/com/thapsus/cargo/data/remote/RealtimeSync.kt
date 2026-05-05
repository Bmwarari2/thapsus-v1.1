package com.thapsus.cargo.data.remote

import com.thapsus.cargo.data.dto.ConsolidationDto
import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.local.ThapsusLocalCache
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Spec §4.3: "customer sees photo and weight on their portal within seconds
 * (Supabase realtime channel)". Subscribes to Postgres change events on
 * `packages` and `consolidations` and writes them through to the local cache,
 * which the UI is already observing.
 *
 * Channel-lifecycle note: supabase-kt keeps a per-topic channel registry —
 * `supabase.channel("foo")` returns the cached channel for `"foo"` if one
 * already exists. After a channel has been joined once, calling
 * `postgresChangeFlow` on it again throws "You cannot call postgresChangeFlow
 * after joining the channel". We avoid that by:
 *   1. Using a unique session-id-suffixed topic per startForX() invocation.
 *   2. Calling supabase.realtime.removeChannel(channel) on stop() so the
 *      registry doesn't grow forever and previous topics get GC'd.
 *
 * Phase 3 follow-up — additional realtime tables we should add once they have
 * a local-cache layer (currently VMs use pull-to-refresh on appear, which is
 * fast enough for v1):
 *   • notifications     → push-style banners for status changes
 *   • tickets / ticket_messages → live admin replies in TicketDetailView
 *   • buy_for_me_orders → status flips visible without reopen
 *   • aml_flags         → admin queue refresh
 * Each requires a SQLDelight table + ThapsusLocalCache observer to mirror
 * what `packages` and `consolidations` already have.
 */
class RealtimeSync(
    private val supabase: SupabaseClient,
    private val cache: ThapsusLocalCache,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var packagesChannel: RealtimeChannel? = null
    private var consolidationsChannel: RealtimeChannel? = null
    private val jobs = mutableListOf<Job>()

    private fun newSessionId(): String =
        Clock.System.now().toEpochMilliseconds().toString()

    /** Open channels for the signed-in customer's parcels. */
    suspend fun startForCustomer(userId: String) {
        stop()
        val sid = newSessionId()
        val channel = supabase.channel("rt-packages-customer-$userId-$sid")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.PACKAGES
            filter("user_id", FilterOperator.EQ, userId)
        }
        jobs += scope.launch {
            flow.collect { action ->
                handlePackageAction(action)
            }
        }
        channel.subscribe()
        packagesChannel = channel
    }

    /** Operator/admin/agent-wide channel for the full packages + consolidations stream. */
    suspend fun startForStaff() {
        stop()
        val sid = newSessionId()

        val pkgChannel = supabase.channel("rt-packages-staff-$sid")
        val pkgFlow = pkgChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.PACKAGES
        }
        jobs += scope.launch {
            pkgFlow.collect { action -> handlePackageAction(action) }
        }
        pkgChannel.subscribe()
        packagesChannel = pkgChannel

        val conChannel = supabase.channel("rt-consolidations-$sid")
        val conFlow = conChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.CONSOLIDATIONS
        }
        jobs += scope.launch {
            conFlow.collect { action -> handleConsolidationAction(action) }
        }
        conChannel.subscribe()
        consolidationsChannel = conChannel
    }

    suspend fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        packagesChannel?.let { ch ->
            runCatching { ch.unsubscribe() }
            runCatching { supabase.realtime.removeChannel(ch) }
        }
        packagesChannel = null
        consolidationsChannel?.let { ch ->
            runCatching { ch.unsubscribe() }
            runCatching { supabase.realtime.removeChannel(ch) }
        }
        consolidationsChannel = null
    }

    private fun handlePackageAction(action: PostgresAction) {
        val now = Clock.System.now().toEpochMilliseconds()
        when (action) {
            is PostgresAction.Insert, is PostgresAction.Update -> {
                val record = (action as? PostgresAction.Insert)?.record
                    ?: (action as PostgresAction.Update).record
                runCatching {
                    val pkg = json.decodeFromJsonElement(PackageDto.serializer(), record)
                    cache.upsertPackage(pkg, now)
                }
            }
            is PostgresAction.Delete -> {
                // No-op for v1 — soft deletes only.
            }
            is PostgresAction.Select -> { /* unused */ }
        }
    }

    private fun handleConsolidationAction(action: PostgresAction) {
        val now = Clock.System.now().toEpochMilliseconds()
        when (action) {
            is PostgresAction.Insert, is PostgresAction.Update -> {
                val record = (action as? PostgresAction.Insert)?.record
                    ?: (action as PostgresAction.Update).record
                runCatching {
                    val c = json.decodeFromJsonElement(ConsolidationDto.serializer(), record)
                    cache.upsertConsolidation(c, now)
                }
            }
            is PostgresAction.Delete, is PostgresAction.Select -> { /* no-op */ }
        }
    }
}
