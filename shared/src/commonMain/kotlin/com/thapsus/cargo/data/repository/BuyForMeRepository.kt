package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.AcceptBuyForMeRequest
import com.thapsus.cargo.data.dto.AdminCreateBuyForMeRequest
import com.thapsus.cargo.data.dto.AdminCreateBuyForMeResponse
import com.thapsus.cargo.data.dto.BuyForMeAckResponse
import com.thapsus.cargo.data.dto.BuyForMeDetailResponse
import com.thapsus.cargo.data.dto.BuyForMeListResponse
import com.thapsus.cargo.data.dto.BuyForMeOrderDto
import com.thapsus.cargo.data.dto.BuyForMePayResponse
import com.thapsus.cargo.data.dto.CreateBuyForMeRequest
import com.thapsus.cargo.data.dto.CreateBuyForMeResponse
import com.thapsus.cargo.data.dto.QuoteBuyForMeRequest
import com.thapsus.cargo.data.dto.RejectBuyForMeRequest
import com.thapsus.cargo.data.remote.Tables
import com.thapsus.cargo.data.remote.ThapsusApiClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class BuyForMeRepository(
    private val api: ThapsusApiClient,
    private val supabase: SupabaseClient
) {
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val realtimeJson = Json { ignoreUnknownKeys = true }

    suspend fun list(): Result<List<BuyForMeOrderDto>> = runCatching {
        api.get<BuyForMeListResponse>("/buy-for-me").orders
    }

    /** Operator queue — `pending_quote / quoted / paid` orders. */
    suspend fun operatorQueue(): Result<List<BuyForMeOrderDto>> = runCatching {
        api.get<BuyForMeListResponse>("/buy-for-me/queue").orders
    }

    /**
     * Live operator queue. Bootstraps from `/buy-for-me/queue`, then merges
     * Supabase Realtime INSERT/UPDATE events from `buy_for_me_orders`.
     * In-memory only — operator-online surface, no offline cache needed.
     */
    fun observeOperatorQueue(): Flow<List<BuyForMeOrderDto>> = callbackFlow {
        var snapshot: List<BuyForMeOrderDto> = emptyList()
        suspend fun emit() { trySend(snapshot.sortedByDescending { it.createdAt ?: "" }) }

        realtimeScope.launch {
            operatorQueue().onSuccess {
                snapshot = it
                emit()
            }
        }

        val channel = supabase.channel("rt-bfm-queue-${Clock.System.now().toEpochMilliseconds()}")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.BUY_FOR_ME_ORDERS
        }
        val rtJob = realtimeScope.launch {
            flow.collect { action ->
                val record = when (action) {
                    is PostgresAction.Insert -> action.record
                    is PostgresAction.Update -> action.record
                    else -> null
                } ?: return@collect
                runCatching {
                    val order = realtimeJson.decodeFromJsonElement(BuyForMeOrderDto.serializer(), record)
                    val keep = order.status in listOf("pending_quote", "quoted", "paid")
                    snapshot = snapshot.filter { it.id != order.id } + (if (keep) listOf(order) else emptyList())
                    emit()
                }
            }
        }
        channel.subscribe()

        awaitClose {
            rtJob.cancel()
            realtimeScope.launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    suspend fun create(
        retailerUrl: String? = null,
        itemName: String,
        size: String? = null,
        qty: Int = 1,
        notes: String? = null,
        retailerId: String? = null
    ): Result<String> = runCatching {
        val resp = api.post<CreateBuyForMeResponse, CreateBuyForMeRequest>(
            "/buy-for-me",
            CreateBuyForMeRequest(
                retailerId = retailerId,
                retailerUrl = retailerUrl,
                itemName = itemName,
                size = size,
                qty = qty,
                notes = notes
            )
        )
        resp.orderId ?: error("BuyForMe create: missing order_id")
    }

    /**
     * Admin: create a BFM on behalf of a customer (off-platform order).
     * Optionally pre-quotes in the same call so the customer can pay
     * immediately without an operator round-trip. Returns the new order
     * id + whether the quote-ready email has already fired.
     */
    suspend fun adminCreate(
        userId: String,
        itemName: String,
        retailerId: String? = null,
        retailerUrl: String? = null,
        size: String? = null,
        qty: Int = 1,
        notes: String? = null,
        estimateGbp: Double? = null,
        markupPct: Double? = null
    ): Result<AdminCreateBuyForMeResponse> = runCatching {
        api.post<AdminCreateBuyForMeResponse, AdminCreateBuyForMeRequest>(
            "/buy-for-me/admin-create",
            AdminCreateBuyForMeRequest(
                userId = userId,
                retailerId = retailerId,
                retailerUrl = retailerUrl,
                itemName = itemName,
                size = size,
                qty = qty,
                notes = notes,
                estimateGbp = estimateGbp,
                markupPct = markupPct,
            )
        )
    }

    suspend fun detail(id: String): Result<BuyForMeOrderDto> = runCatching {
        api.get<BuyForMeDetailResponse>("/buy-for-me/$id").order
            ?: error("BuyForMe detail: missing order")
    }

    suspend fun pay(id: String): Result<BuyForMePayResponse> = runCatching {
        api.post<BuyForMePayResponse, String>("/buy-for-me/$id/pay", null)
    }

    /**
     * Customer accepts a quote. Server runs the same wallet debit as `/pay`
     * but also records the optional acceptance note. Mirror of the webapp's
     * Accept button.
     */
    suspend fun accept(id: String, reason: String? = null): Result<BuyForMePayResponse> = runCatching {
        api.post<BuyForMePayResponse, AcceptBuyForMeRequest>(
            "/buy-for-me/$id/accept",
            AcceptBuyForMeRequest(reason = reason)
        )
    }

    /** Customer rejects a quote with a required reason. */
    suspend fun reject(id: String, reason: String): Result<Unit> = runCatching {
        api.post<BuyForMeAckResponse, RejectBuyForMeRequest>(
            "/buy-for-me/$id/reject",
            RejectBuyForMeRequest(reason = reason)
        )
        Unit
    }

    /** Operator sets the quote — flips status pending_quote → quoted and
     *  triggers the customer's "quote ready" email server-side. */
    suspend fun quote(
        id: String,
        estimateGbp: Double,
        markupPct: Double = 10.0,
        notes: String? = null
    ): Result<Unit> = runCatching {
        api.post<BuyForMeAckResponse, QuoteBuyForMeRequest>(
            "/buy-for-me/$id/quote",
            QuoteBuyForMeRequest(estimateGbp = estimateGbp, markupPct = markupPct, notes = notes)
        )
        Unit
    }

    suspend fun cancel(id: String): Result<Unit> = runCatching {
        api.post<BuyForMePayResponse, String>("/buy-for-me/$id/cancel", null)
    }
}
