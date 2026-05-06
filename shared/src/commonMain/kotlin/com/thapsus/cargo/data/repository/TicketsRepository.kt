package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.CreateTicketRequest
import com.thapsus.cargo.data.dto.CreateTicketResponse
import com.thapsus.cargo.data.dto.PostMessageRequest
import com.thapsus.cargo.data.dto.PostMessageResponse
import com.thapsus.cargo.data.dto.TicketDetailResponse
import com.thapsus.cargo.data.dto.TicketDto
import com.thapsus.cargo.data.dto.TicketListResponse
import com.thapsus.cargo.data.dto.TicketMessageDto
import com.thapsus.cargo.data.dto.TicketMessageRowDto
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
 * Customer + admin tickets with live thread updates.
 *
 *   • [observeMine] / [observeAdminAll]   — list streams from cache + Realtime.
 *   • [observeThread]                     — single-ticket detail with live messages.
 *
 * Realtime emits raw rows (no users JOIN). Email/name/role on each message
 * fill in only on the next bootstrap fetch — that's a deliberate trade-off
 * to avoid the extra round-trip per push event.
 */
class TicketsRepository(
    private val api: ThapsusApiClient,
    private val supabase: SupabaseClient,
    private val cache: ThapsusLocalCache,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler)

    // ── Customer mine list ──────────────────────────────────────
    fun observeMine(userId: String): Flow<List<TicketDto>> = callbackFlow {
        val cacheJob = scope.launch {
            cache.observeTicketsForUser(userId)
                .map { rows -> rows.map { it.toDto() } }
                .catch { trySend(emptyList()) }
                .collect { trySend(it) }
        }
        scope.launch {
            runCatching { api.get<TicketListResponse>("/tickets") }
                .onSuccess { resp ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    resp.tickets.forEach { cache.upsertTicket(it, now) }
                }
        }
        val channel = supabase.channel("rt-tickets-mine-$userId-${Clock.System.now().toEpochMilliseconds()}")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.TICKETS
            filter("user_id", FilterOperator.EQ, userId)
        }
        val rtJob = scope.launch {
            flow.collect { action -> applyTicketAction(action) }
        }
        channel.subscribe()

        awaitClose {
            cacheJob.cancel(); rtJob.cancel()
            scope.launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    // ── Admin all-tickets list ──────────────────────────────────
    fun observeAdminAll(): Flow<List<TicketDto>> = callbackFlow {
        val cacheJob = scope.launch {
            cache.observeAllTickets()
                .map { rows -> rows.map { it.toDto() } }
                .catch { trySend(emptyList()) }
                .collect { trySend(it) }
        }
        scope.launch {
            runCatching { api.get<TicketListResponse>("/tickets/admin/all") }
                .onSuccess { resp ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    resp.tickets.forEach { cache.upsertTicket(it, now) }
                }
        }
        val channel = supabase.channel("rt-tickets-admin-${Clock.System.now().toEpochMilliseconds()}")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.TICKETS
        }
        val rtJob = scope.launch {
            flow.collect { action -> applyTicketAction(action) }
        }
        channel.subscribe()

        awaitClose {
            cacheJob.cancel(); rtJob.cancel()
            scope.launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    // ── Single-ticket thread ────────────────────────────────────
    /**
     * Live message thread for a ticket. Bootstraps from
     * `GET /api/tickets/:id` (which JOINs users for sender attribution),
     * then subscribes to `ticket_messages` filtered on `ticket_id`. Cache
     * is the source of truth for the SwiftUI thread view.
     */
    fun observeThread(ticketId: String): Flow<List<TicketMessageDto>> = callbackFlow {
        val cacheJob = scope.launch {
            cache.observeTicketMessages(ticketId)
                .map { rows -> rows.map { it.toDto() } }
                .catch { trySend(emptyList()) }
                .collect { trySend(it) }
        }
        scope.launch {
            runCatching { api.get<TicketDetailResponse>("/tickets/$ticketId") }
                .onSuccess { resp ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    resp.ticket?.let { cache.upsertTicket(it, now) }
                    resp.messages.forEach { cache.upsertTicketMessage(it, ticketId, now) }
                }
        }
        val channel = supabase.channel("rt-ticket-msgs-$ticketId-${Clock.System.now().toEpochMilliseconds()}")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.TICKET_MESSAGES
            filter("ticket_id", FilterOperator.EQ, ticketId)
        }
        val rtJob = scope.launch {
            flow.collect { action ->
                val record = when (action) {
                    is PostgresAction.Insert -> action.record
                    is PostgresAction.Update -> action.record
                    else -> null
                } ?: return@collect
                runCatching {
                    val row = json.decodeFromJsonElement(TicketMessageRowDto.serializer(), record)
                    val dto = TicketMessageDto(
                        id = row.id,
                        message = row.message,
                        createdAt = row.createdAt
                        // email/name/role missing — bootstrap fetch will fill in
                    )
                    cache.upsertTicketMessage(dto, row.ticketId, Clock.System.now().toEpochMilliseconds(), row.senderId)
                }
            }
        }
        channel.subscribe()

        awaitClose {
            cacheJob.cancel(); rtJob.cancel()
            scope.launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    private fun applyTicketAction(action: PostgresAction) {
        val record = when (action) {
            is PostgresAction.Insert -> action.record
            is PostgresAction.Update -> action.record
            else -> null
        } ?: return
        runCatching {
            val ticket = json.decodeFromJsonElement(TicketDto.serializer(), record)
            cache.upsertTicket(ticket, Clock.System.now().toEpochMilliseconds())
        }
    }

    // ── Mutations (Express-only) ───────────────────────────────
    suspend fun create(subject: String, description: String): Result<TicketDto> = runCatching {
        val resp = api.post<CreateTicketResponse, CreateTicketRequest>(
            "/tickets",
            CreateTicketRequest(subject = subject, description = description)
        )
        val ticket = resp.ticket ?: error("Create ticket: missing ticket in response")
        cache.upsertTicket(ticket, Clock.System.now().toEpochMilliseconds())
        ticket
    }

    suspend fun postMessage(
        id: String,
        message: String,
        attachmentUrl: String? = null
    ): Result<String?> = runCatching {
        val resp = api.post<PostMessageResponse, PostMessageRequest>(
            "/tickets/$id/message",
            PostMessageRequest(message = message, attachmentUrl = attachmentUrl)
        )
        resp.messageId
    }

    /**
     * Mints a signed-upload URL for a ticket attachment. Server endpoint
     * (PR #36) backs the private `ticket-attachments` bucket. iOS PUTs the
     * bytes to `signedUrl`, then POSTs the returned `path` back as
     * `attachment_url` on /tickets/:id/message. S1-5.
     *
     * Returns the bare DTO and throws on failure — SKIE bridges
     * `Result<T>` as `Any?` on the Swift side, see memory
     * `repos_branches.md`.
     */
    suspend fun requestAttachmentUploadUrl(filename: String? = null): com.thapsus.cargo.data.dto.TicketAttachmentUploadUrl =
        api.post<com.thapsus.cargo.data.dto.TicketAttachmentUploadUrl, com.thapsus.cargo.data.dto.TicketAttachmentUploadUrlRequest>(
            "/tickets/attachments/upload-url",
            com.thapsus.cargo.data.dto.TicketAttachmentUploadUrlRequest(filename = filename)
        )

    /**
     * 5-minute signed download URL for an attachment. Owning ticket-author
     * OR admin/operator only (server enforces).
     */
    suspend fun requestAttachmentDownloadUrl(messageId: String): com.thapsus.cargo.data.dto.TicketAttachmentDownloadUrl =
        api.get<com.thapsus.cargo.data.dto.TicketAttachmentDownloadUrl>("/tickets/messages/$messageId/attachment-url")

    /** Legacy refresh-only fetches retained for one-shot bootstrap callers. */
    suspend fun list(): Result<List<TicketDto>> = runCatching {
        api.get<TicketListResponse>("/tickets").tickets
    }

    suspend fun listAllAdmin(): Result<List<TicketDto>> = runCatching {
        api.get<TicketListResponse>("/tickets/admin/all").tickets
    }

    suspend fun detail(id: String): Result<TicketDetailResponse> = runCatching {
        api.get<TicketDetailResponse>("/tickets/$id")
    }
}
