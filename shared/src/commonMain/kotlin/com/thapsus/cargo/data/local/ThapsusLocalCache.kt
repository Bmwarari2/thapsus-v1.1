package com.thapsus.cargo.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.thapsus.cargo.data.dto.ConsolidationDto
import com.thapsus.cargo.data.dto.ConsolidationStatus
import com.thapsus.cargo.data.dto.CustomsEntryDto
import com.thapsus.cargo.data.dto.CustomsStatus
import com.thapsus.cargo.data.dto.LastMileRunDto
import com.thapsus.cargo.data.dto.NotificationDto
import com.thapsus.cargo.data.dto.OutboxFailureDto
import com.thapsus.cargo.data.dto.PackageDto
import com.thapsus.cargo.data.dto.PackageStatus
import com.thapsus.cargo.data.dto.RunStatus
import com.thapsus.cargo.data.dto.ScreeningResult
import com.thapsus.cargo.data.dto.TicketDto
import com.thapsus.cargo.data.dto.TicketMessageDto
import com.thapsus.cargo.db.ConsolidationEntity
import com.thapsus.cargo.db.CustomsEntryEntity
import com.thapsus.cargo.db.LastMileRunEntity
import com.thapsus.cargo.db.NotificationEntity
import com.thapsus.cargo.db.OutboxFailureEntity
import com.thapsus.cargo.db.PackageEntity
import com.thapsus.cargo.db.ThapsusDatabase
import com.thapsus.cargo.db.TicketEntity
import com.thapsus.cargo.db.TicketMessageEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first cache. Repositories write through here on every Supabase fetch,
 * and read-only flows expose data to view models when offline.
 *
 * Critical for the rider role per the Phase-1 mandate: rider PWA must work in
 * Nairobi low-coverage zones, queue mutations, and replay on reconnect.
 */
class ThapsusLocalCache(
    factory: DatabaseDriverFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val database = ThapsusDatabase(factory.create())
    private val packages = database.packageQueries
    private val consolidations = database.consolidationQueries
    private val customs = database.customsEntryQueries
    private val runs = database.lastMileRunQueries
    private val outbox = database.pendingMutationQueries
    private val outboxFailures = database.outboxFailureQueries
    private val notifications = database.notificationQueries
    private val tickets = database.ticketQueries
    private val ticketMessages = database.ticketMessageQueries
    private val homeGreetingSeen = database.homeGreetingSeenQueries

    // ----------------------- Packages -----------------------

    fun observePackagesForUser(userId: String): Flow<List<PackageEntity>> =
        packages.selectForUser(userId).asFlow().mapToList(ioDispatcher)

    fun observeAllPackages(): Flow<List<PackageEntity>> =
        packages.selectAll().asFlow().mapToList(ioDispatcher)

    fun observePackagesInConsolidation(consolidationId: String): Flow<List<PackageEntity>> =
        packages.selectInConsolidation(consolidationId).asFlow().mapToList(ioDispatcher)

    fun observePackage(id: String): Flow<PackageEntity?> =
        packages.selectById(id).asFlow().mapToOneOrNull(ioDispatcher)

    fun upsertPackage(pkg: PackageDto, nowMs: Long, dirty: Boolean = false) {
        packages.upsert(
            id = pkg.id,
            user_id = pkg.userId,
            order_id = pkg.orderId,
            tracking_number = pkg.trackingNumber,
            barcode = pkg.barcode,
            retailer = pkg.retailer,
            description = pkg.description,
            declared_value_gbp_pence = pkg.declaredValueGbpPence,
            actual_kg = pkg.actualKg,
            volumetric_kg = pkg.volumetricKg,
            chargeable_kg = pkg.chargeableKg,
            length_cm = pkg.lengthCm,
            width_cm = pkg.widthCm,
            height_cm = pkg.heightCm,
            status = pkg.status.name,
            hold_reason = pkg.holdReason,
            photo_url = pkg.photoUrl,
            screening_result = pkg.screeningResult.name,
            consolidation_id = pkg.consolidationId,
            insurance_policy_id = pkg.insurancePolicyId,
            updated_at_epoch_ms = nowMs,
            cached_at_epoch_ms = nowMs,
            is_dirty = if (dirty) 1L else 0L
        )
    }

    fun dirtyPackages(): List<PackageEntity> = packages.selectDirty().executeAsList()
    fun markPackageClean(id: String) = packages.markClean(id)

    // ----------------------- Consolidations -----------------------

    fun observeOpenConsolidations(): Flow<List<ConsolidationEntity>> =
        consolidations.selectOpen().asFlow().mapToList(ioDispatcher)

    fun observeAllConsolidations(): Flow<List<ConsolidationEntity>> =
        consolidations.selectAll().asFlow().mapToList(ioDispatcher)

    fun observeConsolidation(id: String): Flow<ConsolidationEntity?> =
        consolidations.selectById(id).asFlow().mapToOneOrNull(ioDispatcher)

    fun upsertConsolidation(c: ConsolidationDto, nowMs: Long) {
        consolidations.upsert(
            id = c.id,
            week_start = c.weekStart,
            cutoff_at = c.cutoffAt,
            departure_at = c.departureAt,
            status = c.status.name,
            total_kg = c.totalKg,
            total_parcels = c.totalParcels.toLong(),
            master_awb_no = c.masterAwbNo,
            master_awb_pdf_url = c.masterAwbPdfUrl,
            tudor_invoice_no = c.tudorInvoiceNo,
            manifest_pdf_url = c.manifestPdfUrl,
            assigned_agent_id = c.assignedAgentId,
            updated_at_epoch_ms = nowMs,
            cached_at_epoch_ms = nowMs
        )
    }

    // ----------------------- Customs -----------------------

    fun observeCustomsForConsolidation(consolidationId: String): Flow<List<CustomsEntryEntity>> =
        customs.selectForConsolidation(consolidationId).asFlow().mapToList(ioDispatcher)

    fun upsertCustomsEntry(e: CustomsEntryDto, nowMs: Long, dirty: Boolean = false) {
        // DB stores values as REAL KES; the cache table is INTEGER cents.
        // Multiply once on the way in, divide on the way out (toDto).
        customs.upsert(
            id = e.id,
            parcel_id = e.parcelId,
            // DB doesn't carry consolidation on the entry — the link is via
            // orders.consolidation_id. Cache as empty so the index column
            // stays NOT-NULL; callers filter via the in-memory parcel list.
            consolidation_id = "",
            idf_no = e.idfNo,
            entry_no = e.entryNo,
            cif_kes_cents = ((e.cifKes ?: 0.0) * 100).toLong(),
            duty_kes_cents = ((e.dutyKes ?: 0.0) * 100).toLong(),
            vat_kes_cents = ((e.vatKes ?: 0.0) * 100).toLong(),
            idf_kes_cents = ((e.idfKes ?: 0.0) * 100).toLong(),
            rdl_kes_cents = ((e.rdlKes ?: 0.0) * 100).toLong(),
            status = e.status.name,
            submitted_at_epoch_ms = null,
            released_at_epoch_ms = null,
            cached_at_epoch_ms = nowMs,
            is_dirty = if (dirty) 1L else 0L
        )
    }

    // ----------------------- Last-mile -----------------------

    fun observeRunsForRider(riderId: String): Flow<List<LastMileRunEntity>> =
        runs.selectRunsForRider(riderId).asFlow().mapToList(ioDispatcher)

    fun upsertRun(r: LastMileRunDto, nowMs: Long, dirty: Boolean = false) {
        runs.upsertRun(
            id = r.id,
            rider_id = r.riderId,
            zone = r.zone,
            run_date = r.runDate,
            status = r.status.name,
            started_at_epoch_ms = null,
            completed_at_epoch_ms = null,
            cached_at_epoch_ms = nowMs,
            is_dirty = if (dirty) 1L else 0L
        )
    }

    // ----------------------- Notifications -----------------------

    fun observeNotificationsForUser(userId: String): Flow<List<NotificationEntity>> =
        notifications.selectForUser(userId).asFlow().mapToList(ioDispatcher)

    fun upsertNotification(n: NotificationDto, userId: String, nowMs: Long) {
        notifications.upsert(
            id = n.id,
            user_id = userId,
            type = n.type,
            message = n.message,
            is_read = if (n.isRead) 1L else 0L,
            created_at = n.createdAt,
            cached_at_epoch_ms = nowMs
        )
    }

    fun markNotificationRead(id: String) = notifications.markRead(id)

    fun markAllNotificationsRead(userId: String) = notifications.markAllRead(userId)

    // ----------------------- Tickets -----------------------

    fun observeTicketsForUser(userId: String): Flow<List<TicketEntity>> =
        tickets.selectForUser(userId).asFlow().mapToList(ioDispatcher)

    fun observeAllTickets(): Flow<List<TicketEntity>> =
        tickets.selectAll().asFlow().mapToList(ioDispatcher)

    fun observeTicket(id: String): Flow<TicketEntity?> =
        tickets.selectById(id).asFlow().mapToOneOrNull(ioDispatcher)

    fun upsertTicket(t: TicketDto, nowMs: Long) {
        tickets.upsert(
            id = t.id,
            user_id = t.userId,
            subject = t.subject,
            description = t.description,
            status = t.status,
            priority = t.priority,
            photo_url = t.photoUrl,
            created_at = t.createdAt,
            updated_at = t.updatedAt,
            cached_at_epoch_ms = nowMs
        )
    }

    // ----------------------- Ticket messages -----------------------

    fun observeTicketMessages(ticketId: String): Flow<List<TicketMessageEntity>> =
        ticketMessages.selectForTicket(ticketId).asFlow().mapToList(ioDispatcher)

    fun upsertTicketMessage(m: TicketMessageDto, ticketId: String, nowMs: Long, senderId: String? = null) {
        ticketMessages.upsert(
            id = m.id,
            ticket_id = ticketId,
            sender_id = senderId,
            message = m.message,
            sender_email = m.email,
            sender_name = m.name,
            sender_role = m.role,
            created_at = m.createdAt,
            cached_at_epoch_ms = nowMs
        )
    }

    // ----------------------- Home greeting "seen" markers -----------------------

    /**
     * Returns a map of greeting-id → last-seen-epoch-ms for the user. Used by
     * HomeGreetingBuilder to filter status greetings whose underlying event
     * timestamp is older than the last time the customer opened the destination.
     */
    fun homeGreetingSeenForUser(userId: String): Map<String, Long> =
        homeGreetingSeen.selectForUser(userId).executeAsList().associate {
            it.greeting_id to it.last_seen_at_ms
        }

    fun markHomeGreetingSeen(userId: String, greetingId: String, nowMs: Long) {
        homeGreetingSeen.upsert(userId, greetingId, nowMs)
    }

    // ----------------------- Outbox -----------------------

    fun enqueueMutation(
        id: String,
        kind: String,
        payloadJson: String,
        targetTable: String,
        targetId: String?,
        nowMs: Long
    ) {
        outbox.enqueue(
            id = id,
            kind = kind,
            payload_json = payloadJson,
            target_table = targetTable,
            target_id = targetId,
            attempts = 0,
            last_error = null,
            enqueued_at_epoch_ms = nowMs,
            next_attempt_at_epoch_ms = nowMs
        )
    }

    fun pendingCount(): Long = outbox.countAll().executeAsOne()

    fun dequeueDue(nowMs: Long, limit: Int = 20) =
        outbox.dequeueDue(nowMs, limit.toLong()).executeAsList()

    /**
     * Returns every queued mutation regardless of its `next_attempt_at_epoch_ms`.
     * Use ONLY from the user-facing "Flush now" button — background workers
     * must keep honouring exponential backoff via [dequeueDue]. Audit
     * follow-up: manual taps within the 64s backoff window were returning
     * zero rows, so the rider thought the outbox was dead.
     */
    fun dequeueAll(limit: Int = 20) =
        outbox.dequeueAll(limit.toLong()).executeAsList()

    fun removeMutation(id: String) = outbox.remove(id)

    fun bumpRetry(id: String, nextAttemptAtMs: Long, lastError: String?) =
        outbox.bumpRetry(lastError, nextAttemptAtMs, id)

    // ----------------------- Outbox 4xx failures (audit M2) -----------------------

    /**
     * Move a terminally-rejected mutation (4xx) out of the retry queue and
     * into the failure ledger. Existing failures with the same mutation id
     * are overwritten so a manual retry that fails again only ever produces
     * one row.
     */
    fun recordOutboxFailure(
        mutationId: String,
        kind: String,
        payloadJson: String,
        targetTable: String,
        targetId: String?,
        errorStatus: Int,
        errorMessage: String?,
        nowMs: Long
    ) {
        outboxFailures.insertFailure(
            mutation_id = mutationId,
            kind = kind,
            payload_json = payloadJson,
            target_table = targetTable,
            target_id = targetId,
            error_status = errorStatus.toLong(),
            error_message = errorMessage,
            failed_at_epoch_ms = nowMs
        )
    }

    fun observeOutboxFailures(): Flow<List<OutboxFailureEntity>> =
        outboxFailures.selectAll().asFlow().mapToList(ioDispatcher)

    fun selectOutboxFailures(): List<OutboxFailureEntity> =
        outboxFailures.selectAll().executeAsList()

    fun selectOutboxFailuresForTarget(targetId: String): List<OutboxFailureEntity> =
        outboxFailures.selectByTargetId(targetId).executeAsList()

    fun removeOutboxFailure(mutationId: String) = outboxFailures.removeFailure(mutationId)

    fun removeOutboxFailuresForTarget(targetId: String) =
        outboxFailures.removeForTargetId(targetId)

    /**
     * Wipe every cached row across every table. Called on sign-in (fresh
     * data must come from the server, never a stale cache from a previous
     * account or pre-purge state) and on sign-out (no leftover data after
     * the user logs out on a shared device).
     *
     * Wrapped in a single transaction so observers fire once instead of
     * thrashing through nine empty emissions.
     */
    fun clearAll() {
        database.transaction {
            packages.deleteAll()
            consolidations.deleteAll()
            customs.deleteAll()
            runs.deleteAllStops()
            runs.deleteAllRuns()
            notifications.deleteAll()
            ticketMessages.deleteAll()
            tickets.deleteAll()
            outbox.deleteAll()
            outboxFailures.deleteAll()
            homeGreetingSeen.deleteAll()
        }
    }

    /** Mappers from row → DTO so view models hold transport-shape data even offline. */
    companion object {
        fun PackageEntity.toDto(): PackageDto = PackageDto(
            id = id,
            userId = user_id,
            orderId = order_id,
            trackingNumber = tracking_number,
            barcode = barcode,
            retailer = retailer,
            description = description,
            declaredValueGbpPence = declared_value_gbp_pence,
            actualKg = actual_kg,
            volumetricKg = volumetric_kg,
            chargeableKg = chargeable_kg,
            lengthCm = length_cm,
            widthCm = width_cm,
            heightCm = height_cm,
            status = runCatching { PackageStatus.valueOf(status) }
                .getOrDefault(PackageStatus.PRE_REGISTERED),
            holdReason = hold_reason,
            photoUrl = photo_url,
            screeningResult = runCatching { ScreeningResult.valueOf(screening_result) }
                .getOrDefault(ScreeningResult.PENDING),
            consolidationId = consolidation_id,
            insurancePolicyId = insurance_policy_id
        )

        fun ConsolidationEntity.toDto(): ConsolidationDto = ConsolidationDto(
            id = id,
            weekStart = week_start,
            cutoffAt = cutoff_at,
            departureAt = departure_at,
            status = runCatching { ConsolidationStatus.valueOf(status) }
                .getOrDefault(ConsolidationStatus.OPEN),
            totalKg = total_kg,
            totalParcels = total_parcels.toInt(),
            masterAwbNo = master_awb_no,
            masterAwbPdfUrl = master_awb_pdf_url,
            tudorInvoiceNo = tudor_invoice_no,
            manifestPdfUrl = manifest_pdf_url,
            assignedAgentId = assigned_agent_id
        )

        fun LastMileRunEntity.toDto(): LastMileRunDto = LastMileRunDto(
            id = id,
            riderId = rider_id,
            zone = zone,
            runDate = run_date,
            // Local cache writes the wire-format SerialName ("planned", etc.).
            // RunStatus.valueOf() expects the Kotlin name (PLANNED), so map
            // by SerialName via uppercase first.  Fallback to PLANNED to
            // match the server-side default.
            status = runCatching { RunStatus.valueOf(status.uppercase()) }
                .getOrDefault(RunStatus.PLANNED)
        )

        fun CustomsEntryEntity.toDto(): CustomsEntryDto = CustomsEntryDto(
            id = id,
            parcelId = parcel_id,
            idfNo = idf_no,
            entryNo = entry_no,
            cifKes = cif_kes_cents / 100.0,
            dutyKes = duty_kes_cents / 100.0,
            vatKes = vat_kes_cents / 100.0,
            idfKes = idf_kes_cents / 100.0,
            rdlKes = rdl_kes_cents / 100.0,
            status = runCatching { CustomsStatus.valueOf(status) }
                .getOrDefault(CustomsStatus.PRE_ALERT)
        )

        fun NotificationEntity.toDto(): NotificationDto = NotificationDto(
            id = id,
            type = type,
            message = message,
            isRead = is_read != 0L,
            createdAt = created_at
        )

        fun TicketEntity.toDto(): TicketDto = TicketDto(
            id = id,
            userId = user_id,
            subject = subject,
            description = description,
            status = status,
            priority = priority,
            photoUrl = photo_url,
            createdAt = created_at,
            updatedAt = updated_at
        )

        fun OutboxFailureEntity.toDto(): OutboxFailureDto = OutboxFailureDto(
            mutationId = mutation_id,
            kind = kind,
            payloadJson = payload_json,
            targetTable = target_table,
            targetId = target_id,
            errorStatus = error_status.toInt(),
            errorMessage = error_message,
            failedAtEpochMs = failed_at_epoch_ms
        )

        fun TicketMessageEntity.toDto(): TicketMessageDto = TicketMessageDto(
            id = id,
            message = message,
            createdAt = created_at,
            email = sender_email,
            name = sender_name,
            role = sender_role
        )
    }
}
