package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.CreateOrderRequest
import com.thapsus.cargo.data.dto.OrderDto
import com.thapsus.cargo.data.dto.OrderListResponse
import com.thapsus.cargo.data.dto.OrderResponse
import com.thapsus.cargo.data.remote.Tables
import com.thapsus.cargo.data.remote.ThapsusApiClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Customer-facing orders.
 *   • Reads       → Supabase PostgREST + Realtime (RLS by user_id).
 *   • Writes      → Express (/api/orders) so the existing business logic — pricing,
 *                   AML screening, electronics surcharge, status state machine —
 *                   stays the single source of truth.
 */
class OrdersRepository(
    private val supabase: SupabaseClient,
    private val api: ThapsusApiClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler)
    private val json = Json { ignoreUnknownKeys = true }

    fun observeOrders(userId: String): Flow<List<OrderDto>> = callbackFlow {
        val initial = runCatching { fetchOrders(userId) }.getOrDefault(emptyList())
        trySend(initial)
        var current = initial

        val channel = supabase.channel("rt-orders-$userId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.ORDERS
            filter("user_id", FilterOperator.EQ, userId)
        }
        val job = scope.launch {
            changeFlow.collect { action ->
                when (action) {
                    is PostgresAction.Insert -> {
                        runCatching {
                            val o = json.decodeFromJsonElement(OrderDto.serializer(), action.record)
                            current = (listOf(o) + current).distinctBy { it.id }
                            trySend(current)
                        }
                    }
                    is PostgresAction.Update -> {
                        runCatching {
                            val o = json.decodeFromJsonElement(OrderDto.serializer(), action.record)
                            current = current.map { if (it.id == o.id) o else it }
                            trySend(current)
                        }
                    }
                    is PostgresAction.Delete -> Unit
                    is PostgresAction.Select -> Unit
                }
            }
        }
        channel.subscribe()

        awaitClose {
            job.cancel()
            scope.launch { runCatching { channel.unsubscribe() } }
        }
    }

    suspend fun fetchOrders(userId: String, limit: Long = 100): List<OrderDto> =
        supabase.from(Tables.ORDERS)
            .select {
                filter { eq("user_id", userId) }
                order("created_at", order = Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    suspend fun fetchOne(orderId: String): OrderDto? = supabase.from(Tables.ORDERS)
        .select { filter { eq("id", orderId) }; limit(1) }
        .decodeSingleOrNull()

    suspend fun create(req: CreateOrderRequest): Result<OrderDto> = runCatching {
        val resp = api.post<OrderResponse, CreateOrderRequest>("/orders", req)
        resp.order ?: error(resp.message ?: "Order creation failed")
    }

    suspend fun cancel(orderId: String): Result<Unit> = runCatching {
        api.post<OrderResponse, Map<String, String>>("/orders/$orderId/cancel", null)
        Unit
    }

    /**
     * GET /api/orders/:id/pod — fresh POD summary for a delivered parcel.
     * Server mints short-lived (5 min) signed download URLs for the photo
     * + signature, so callers must use the response immediately. Returns
     * null on 404 (no POD recorded yet).
     */
    suspend fun fetchPod(orderId: String): com.thapsus.cargo.data.dto.PodDetailDto? =
        runCatching {
            api.get<com.thapsus.cargo.data.dto.PodDetailResponse>("/orders/$orderId/pod").pod
        }.getOrNull()
}
