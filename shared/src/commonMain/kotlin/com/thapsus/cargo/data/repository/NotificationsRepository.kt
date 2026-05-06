package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.NotificationAckResponse
import com.thapsus.cargo.data.dto.NotificationDto
import com.thapsus.cargo.data.dto.NotificationListResponse
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.local.ThapsusLocalCache.Companion.toDto
import com.thapsus.cargo.data.remote.Tables
import com.thapsus.cargo.data.remote.ThapsusApiClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import com.thapsus.cargo.util.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Inbox + live banners. Reads route through Express
 * (`GET /api/notifications`) for the initial bootstrap; once cached, the
 * SwiftUI inbox observes the local SQLDelight rows. New rows arriving on
 * Supabase Realtime are written through the cache so the inbox refreshes
 * the instant the server pushes a row.
 */
class NotificationsRepository(
    private val api: ThapsusApiClient,
    private val supabase: SupabaseClient,
    private val cache: ThapsusLocalCache,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler)

    /**
     * Live inbox stream for one user. Bootstraps from Express, writes through
     * the cache, then subscribes to `notifications` INSERT/UPDATE events
     * filtered by `user_id = userId`. The cache is the single source of
     * truth — both the bootstrap fetch and the realtime stream funnel through
     * `cache.upsertNotification`.
     */
    fun observeForUser(userId: String): Flow<List<NotificationDto>> = callbackFlow {
        // Initial cache emit so the UI doesn't flash empty. The catch keeps a
        // missing/corrupt cache table from cancelling the parent scope (which
        // previously froze the dashboard when NotificationEntity hadn't been
        // migrated in on an upgraded install).
        val cacheJob = scope.launch {
            cache.observeNotificationsForUser(userId)
                .map { rows -> rows.map { it.toDto() } }
                .catch { trySend(emptyList()) }
                .collect { trySend(it) }
        }

        // Bootstrap from Express (best-effort; cache wins on failure).
        scope.launch {
            runCatching { api.get<NotificationListResponse>("/notifications?limit=100") }
                .onSuccess { resp ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    resp.notifications.forEach { cache.upsertNotification(it, userId, now) }
                }
        }

        // Live updates.
        val sid = Clock.System.now().toEpochMilliseconds()
        val channel: RealtimeChannel = supabase.channel("rt-notifications-$userId-$sid")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.NOTIFICATIONS
            filter("user_id", FilterOperator.EQ, userId)
        }
        val rtJob = scope.launch {
            changes.collect { action ->
                val record = when (action) {
                    is PostgresAction.Insert -> action.record
                    is PostgresAction.Update -> action.record
                    else -> null
                } ?: return@collect
                runCatching {
                    val n = json.decodeFromJsonElement(NotificationDto.serializer(), record)
                    cache.upsertNotification(n, userId, Clock.System.now().toEpochMilliseconds())
                }
            }
        }
        channel.subscribe()

        awaitClose {
            cacheJob.cancel()
            rtJob.cancel()
            scope.launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    /** One-shot refresh — useful for pull-to-refresh fallback if Realtime drops. */
    suspend fun refresh(userId: String, limit: Int = 100): Result<List<NotificationDto>> = runCatching {
        val resp = api.get<NotificationListResponse>("/notifications?limit=$limit")
        val now = Clock.System.now().toEpochMilliseconds()
        resp.notifications.forEach { cache.upsertNotification(it, userId, now) }
        resp.notifications
    }

    suspend fun markRead(id: String): Result<Unit> = runCatching {
        api.put<NotificationAckResponse, String>("/notifications/$id/read", null)
        cache.markNotificationRead(id)
    }

    suspend fun markAllRead(userId: String): Result<Unit> = runCatching {
        api.put<NotificationAckResponse, String>("/notifications/read-all", null)
        cache.markAllNotificationsRead(userId)
    }
}
