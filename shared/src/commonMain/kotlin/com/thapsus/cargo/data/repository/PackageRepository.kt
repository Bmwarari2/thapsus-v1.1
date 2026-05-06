package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.OpsBarcodeLookupResponse
import com.thapsus.cargo.data.dto.OpsCustomerLookupResponse
import com.thapsus.cargo.data.dto.OpsReceiveRequest
import com.thapsus.cargo.data.dto.OpsReceiveResponse
import com.thapsus.cargo.data.dto.OpsScannedParcelDto
import com.thapsus.cargo.data.dto.OrderDimensionsDto
import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.local.ThapsusLocalCache.Companion.toDto
import com.thapsus.cargo.data.remote.Tables
import com.thapsus.cargo.data.remote.ThapsusApiClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import com.thapsus.cargo.util.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Reads stay PostgREST + local cache. Writes are server-owned:
 *   • Customer order create  → `OrdersRepository.create` (POST /api/orders).
 *   • Operator intake        → [receive] (POST /api/ops/parcels/:id/receive),
 *     which the server uses to update weight/dimensions/chargeable_kg + the
 *     attached `packages` row in one transaction.
 *
 * The previous direct-PostgREST `upsert` and `updateStatus` paths were
 * removed (Phase 1 audit F2): they bypassed the server's side-effects
 * (admin push events, status transitions, screening flags) and silently
 * worked because RLS was off.
 */
data class OpsCustomer(
    val fullName: String?,
    val warehouseId: String?
)

class PackageRepository(
    private val supabase: SupabaseClient,
    private val cache: ThapsusLocalCache,
    private val api: ThapsusApiClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler)
    private val json = Json { ignoreUnknownKeys = true }

    fun observeForUser(userId: String): Flow<List<PackageDto>> =
        cache.observePackagesForUser(userId).map { rows -> rows.map { it.toDto() } }

    fun observeOne(id: String): Flow<PackageDto?> =
        cache.observePackage(id).map { it?.toDto() }

    /**
     * Live `packages` channel for the customer's iOS tracking views.
     *
     * Migration 024 added `packages` to the `supabase_realtime` publication;
     * before that the table was outside the publication and any subscription
     * silently received nothing. Each Insert/Update upserts the row into the
     * SQLDelight cache, which fans out through every existing
     * `cache.observePackagesForUser` / `observePackage` subscriber — so the
     * customer's TrackingView and ParcelDetailView pick up status flips
     * (received_at_warehouse → consolidating → out_for_delivery → delivered)
     * without a pull-to-refresh.
     *
     * Hot Flow: launches a per-userId Supabase channel and stays subscribed
     * for the Flow's lifetime. Caller is expected to consume from a
     * `viewModelScope.launch { ... }` (or SwiftUI `.task(id:)`) so cancellation
     * cleans the channel up.
     */
    fun observeRealtimeForUser(userId: String): Flow<PackageDto> = callbackFlow {
        val channel = supabase.channel("rt-packages-$userId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.PACKAGES
            filter("user_id", FilterOperator.EQ, userId)
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val job = scope.launch {
            // Wrap the whole collect in runCatching so a malformed websocket
            // frame, supabase-kt internal exception, or any other Throwable
            // raised inside `collect` doesn't escape into the parent
            // SupervisorJob's handler and crash the app via
            // Kotlin_processUnhandledException (Orders tab freeze trace).
            // The flow ends silently; the cache-backed observe…ForUser still
            // serves whatever rows it has.
            runCatching {
                changeFlow.collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> runCatching {
                            val pkg = json.decodeFromJsonElement(PackageDto.serializer(), action.record)
                            cache.upsertPackage(pkg, now)
                            trySend(pkg)
                        }
                        is PostgresAction.Update -> runCatching {
                            val pkg = json.decodeFromJsonElement(PackageDto.serializer(), action.record)
                            cache.upsertPackage(pkg, now)
                            trySend(pkg)
                        }
                        is PostgresAction.Delete -> Unit
                        is PostgresAction.Select -> Unit
                    }
                }
            }.onFailure { t ->
                println("[PackageRepository.observeRealtimeForUser] collect failed: ${t::class.simpleName}: ${t.message}")
            }
        }
        runCatching { channel.subscribe() }
            .onFailure { t ->
                println("[PackageRepository.observeRealtimeForUser] subscribe failed: ${t.message}")
            }
        awaitClose {
            job.cancel()
            scope.launch { runCatching { channel.unsubscribe() } }
        }
    }

    suspend fun refreshForUser(userId: String): Result<List<PackageDto>> = runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        val rows = supabase.from(Tables.PACKAGES)
            .select { filter { eq("user_id", userId) } }
            .decodeList<PackageDto>()
        rows.forEach { cache.upsertPackage(it, now) }
        rows
    }

    /**
     * Operator/admin-scoped pull of every package row. Used by the consolidation
     * detail flow after assigning parcels so the local cache reflects the new
     * `consolidation_id` immediately, without waiting for the next Realtime tick.
     */
    suspend fun refreshAll(): Result<List<PackageDto>> = runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        val rows = supabase.from(Tables.PACKAGES).select().decodeList<PackageDto>()
        rows.forEach { cache.upsertPackage(it, now) }
        rows
    }

    suspend fun fetchByBarcode(barcode: String): PackageDto? = runCatching {
        supabase.from(Tables.PACKAGES)
            .select {
                filter { eq("barcode", barcode) }
                limit(1)
            }
            .decodeSingleOrNull<PackageDto>()
    }.getOrNull()

    /**
     * Operator camera-scanner lookup. Routes through `/api/ops/parcels/by-barcode/:barcode`
     * (server uses pg pool — bypasses RLS) and returns the full operator-scope
     * projection: customer block, status, weights, consolidation linkage, hold reason.
     * `null` on miss so the iOS scanner sheet can show a "Not found" banner without
     * having to inspect a Result envelope.
     */
    suspend fun lookupByScannedBarcode(barcode: String): Result<OpsScannedParcelDto?> = runCatching {
        val resp = api.get<OpsBarcodeLookupResponse>("/ops/parcels/by-barcode/$barcode")
        resp.parcel
    }

    /**
     * Operator intake. Posts to `POST /api/ops/parcels/:orderId/receive`
     * with the captured weight, dimensions, photo, and barcode. The server
     * recomputes volumetric/chargeable kg, flips the parcel into
     * `received_at_warehouse`, and stamps `photographed_at` if a photo URL
     * was provided. Returns the recomputed weights so iOS can reflect the
     * server's authoritative value.
     */
    /**
     * Phase C — operator label needs the customer's full name and personal
     * `TC-XXXX` warehouse code, neither of which is on PackageDto. Routes
     * through `/api/ops/parcels/:orderId/customer` (operator-only). Returns
     * a Triple-style nullable so the iOS sheet can fall back to the parcel's
     * retailer if the lookup 404s or fails.
     */
    suspend fun fetchCustomer(orderId: String): OpsCustomer? = try {
        val resp = api.get<OpsCustomerLookupResponse>("/ops/parcels/$orderId/customer")
        resp.customer?.let {
            OpsCustomer(fullName = it.fullName, warehouseId = it.warehouseId)
        }
    } catch (_: Throwable) {
        null
    }

    suspend fun receive(
        orderId: String,
        weightKg: Double? = null,
        dimensions: OrderDimensionsDto? = null,
        photoUrl: String? = null,
        barcode: String? = null,
        customsDuty: Double? = null,
        hsTier: String? = null
    ): Result<OpsReceiveResponse> = runCatching {
        api.post<OpsReceiveResponse, OpsReceiveRequest>(
            "/ops/parcels/$orderId/receive",
            OpsReceiveRequest(
                weightKg = weightKg,
                dimensions = dimensions,
                photoUrl = photoUrl,
                barcode = barcode,
                customsDuty = customsDuty,
                hsTier = hsTier
            )
        )
    }
}
