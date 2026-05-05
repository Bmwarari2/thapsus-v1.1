package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.CustomsEntryDto
import com.thapsus.cargo.data.dto.CustomsStatus
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.local.ThapsusLocalCache.Companion.toDto
import com.thapsus.cargo.data.remote.ThapsusApiClient
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateCustomsEntryRequest(
    @SerialName("parcel_id") val parcelId: String,
    @SerialName("idf_no") val idfNo: String?,
    @SerialName("entry_no") val entryNo: String?,
    @SerialName("cif_kes") val cifKes: Double,
    @SerialName("duty_kes") val dutyKes: Double,
    @SerialName("vat_kes") val vatKes: Double,
    @SerialName("idf_kes") val idfKes: Double,
    @SerialName("rdl_kes") val rdlKes: Double,
    val status: String? = null,
    val notes: String? = null,
    @SerialName("doc_url") val docUrl: String? = null
)

@Serializable
internal data class CreateCustomsEntryResponse(
    val success: Boolean = true,
    @SerialName("entry_id") val entryId: String? = null
)

@Serializable
internal data class UpdateCustomsEntryRequest(
    val status: String? = null,
    val notes: String? = null,
    @SerialName("doc_url") val docUrl: String? = null
)

@Serializable
internal data class BulkCustomsEntriesRequest(
    @SerialName("consolidation_id") val consolidationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("idf_no") val idfNo: String?,
    @SerialName("entry_no") val entryNo: String?,
    @SerialName("cif_kes") val cifKes: Double,
    @SerialName("duty_kes") val dutyKes: Double,
    @SerialName("vat_kes") val vatKes: Double,
    @SerialName("idf_kes") val idfKes: Double,
    @SerialName("rdl_kes") val rdlKes: Double,
    val status: String? = null,
    val notes: String? = null,
    @SerialName("doc_url") val docUrl: String? = null
)

@Serializable
data class BulkCustomsEntriesResponse(
    val success: Boolean = true,
    @SerialName("consolidation_id") val consolidationId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("parcel_count") val parcelCount: Int = 0,
    @SerialName("split_basis") val splitBasis: String? = null,
    val entries: List<BulkCustomsEntryRow> = emptyList()
)

@Serializable
data class BulkCustomsEntryRow(
    @SerialName("entry_id") val entryId: String,
    @SerialName("parcel_id") val parcelId: String,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    @SerialName("duty_kes") val dutyKes: Double = 0.0,
    @SerialName("vat_kes") val vatKes: Double = 0.0
)

@Serializable
data class CustomsAttachmentUploadUrlResponse(
    val success: Boolean = true,
    val bucket: String,
    val path: String,
    @SerialName("signed_url") val signedUrl: String,
    val token: String? = null
)

@Serializable
internal data class AgentConsolParcelsResponse(
    val success: Boolean = true,
    val parcels: List<AgentParcelRow> = emptyList()
)

@Serializable
data class AgentParcelRow(
    val id: String,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    val retailer: String? = null,
    val description: String? = null,
    @SerialName("declared_value") val declaredValue: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("chargeable_kg") val chargeableKg: Double? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("consignee_name") val consigneeName: String? = null,
    val phone: String? = null,
    @SerialName("entry_id") val entryId: String? = null,
    @SerialName("idf_no") val idfNo: String? = null,
    @SerialName("entry_no") val entryNo: String? = null,
    @SerialName("duty_kes") val dutyKes: Double? = null,
    @SerialName("vat_kes") val vatKes: Double? = null,
    @SerialName("idf_kes") val idfKes: Double? = null,
    @SerialName("rdl_kes") val rdlKes: Double? = null,
    @SerialName("entry_status") val entryStatus: String? = null
) {
    fun toEntryDto(): CustomsEntryDto? {
        val eid = entryId ?: return null
        val statusEnum = runCatching {
            CustomsStatus.valueOf((entryStatus ?: "pre_alert").uppercase())
        }.getOrDefault(CustomsStatus.PRE_ALERT)
        return CustomsEntryDto(
            id = eid,
            parcelId = id,
            idfNo = idfNo,
            entryNo = entryNo,
            cifKes = 0.0,
            dutyKes = dutyKes ?: 0.0,
            vatKes = vatKes ?: 0.0,
            idfKes = idfKes ?: 0.0,
            rdlKes = rdlKes ?: 0.0,
            status = statusEnum
        )
    }
}

class CustomsRepository(
    private val supabase: SupabaseClient,
    private val cache: ThapsusLocalCache,
    private val api: ThapsusApiClient
) {
    fun observeForConsolidation(consolidationId: String): Flow<List<CustomsEntryDto>> =
        cache.observeCustomsForConsolidation(consolidationId).map { rows ->
            rows.map { it.toDto() }
        }

    /**
     * Pulls the agent-scoped pre-alert pack from Express. Joins
     * `orders → customs_entries` server-side, so we don't depend on a
     * `customs_entries.consolidation_id` column (which doesn't exist on the
     * live schema).
     */
    suspend fun refreshForConsolidation(consolidationId: String): Result<List<CustomsEntryDto>> =
        runCatching {
            val parcels = api.get<AgentConsolParcelsResponse>(
                "/customs/agent/consolidations/$consolidationId/parcels"
            ).parcels
            val now = Clock.System.now().toEpochMilliseconds()
            val entries = parcels.mapNotNull { it.toEntryDto() }
            entries.forEach { cache.upsertCustomsEntry(it, now) }
            entries
        }

    /**
     * SKIE-friendly helper — returns the parcel pre-alert pack directly so
     * the iOS new-entry sheet can show a parcel picker instead of asking the
     * agent to type a UUID.
     */
    suspend fun parcelsForConsolidation(consolidationId: String): List<AgentParcelRow> = try {
        api.get<AgentConsolParcelsResponse>(
            "/customs/agent/consolidations/$consolidationId/parcels"
        ).parcels
    } catch (_: Throwable) {
        emptyList()
    }

    /**
     * Submits a customs entry via the Express clearing-agent route. The
     * server fills in `agent_id` from the JWT and cascades the parcel into
     * `status = 'customs'`. Returns the new entry id.
     */
    suspend fun submitEntry(
        parcelId: String,
        idfNo: String?,
        entryNo: String?,
        cifKes: Double,
        dutyKes: Double,
        vatKes: Double,
        idfKes: Double,
        rdlKes: Double,
        notes: String? = null,
        docUrl: String? = null
    ): Result<String?> = runCatching {
        api.post<CreateCustomsEntryResponse, CreateCustomsEntryRequest>(
            "/customs/entries",
            CreateCustomsEntryRequest(
                parcelId = parcelId,
                idfNo = idfNo,
                entryNo = entryNo,
                cifKes = cifKes,
                dutyKes = dutyKes,
                vatKes = vatKes,
                idfKes = idfKes,
                rdlKes = rdlKes,
                notes = notes,
                docUrl = docUrl
            )
        ).entryId
    }

    /**
     * Files ONE set of customs paperwork that gets fanned out across every
     * un-entered parcel belonging to a single customer in a consolidation.
     * Server splits the cif/duty/vat/idf/rdl figures proportionally by
     * chargeable_kg (or declared_value, or evenly) and reconciles the
     * residual on the last parcel so the totals add back exactly.
     *
     * Audit follow-up: "parcels from one customer to be grouped and only
     * one set of tax details will be entered."
     */
    suspend fun submitBulkEntries(
        consolidationId: String,
        userId: String,
        idfNo: String?,
        entryNo: String?,
        cifKes: Double,
        dutyKes: Double,
        vatKes: Double,
        idfKes: Double,
        rdlKes: Double,
        notes: String? = null,
        docUrl: String? = null
    ): Result<BulkCustomsEntriesResponse> = runCatching {
        api.post<BulkCustomsEntriesResponse, BulkCustomsEntriesRequest>(
            "/customs/entries/bulk",
            BulkCustomsEntriesRequest(
                consolidationId = consolidationId,
                userId = userId,
                idfNo = idfNo,
                entryNo = entryNo,
                cifKes = cifKes,
                dutyKes = dutyKes,
                vatKes = vatKes,
                idfKes = idfKes,
                rdlKes = rdlKes,
                notes = notes,
                docUrl = docUrl
            )
        )
    }

    /**
     * Advance an existing customs entry's status (or update notes / doc_url).
     * Server allow-list lives at routes/customs.js:133. The transitions
     * customers care about: idf_submitted → entry_filed → duty_assessed →
     * duty_paid → released. When status="released" the server also flips
     * the parcel into out_for_delivery automatically.
     */
    suspend fun updateEntryStatus(entryId: String, status: String): Result<Unit> = runCatching {
        api.patch<GenericAck, UpdateCustomsEntryRequest>(
            "/customs/entries/$entryId",
            UpdateCustomsEntryRequest(status = status)
        )
        Unit
    }

    /**
     * Mints a 5-min signed-upload URL into the agent-invoices bucket
     * (re-used for customs IDF/KRA docs since the bucket policy already
     * gates clearing-agent uploads). Server endpoint: POST
     * /api/agent-invoices/upload-url. Returns the canonical bucket path.
     *
     * Returns the bare DTO and throws on failure — SKIE bridges
     * `Result<T>` as `Any?` on the Swift side, see memory
     * `repos_branches.md`.
     */
    suspend fun requestEntryDocUploadUrl(filename: String? = null): CustomsAttachmentUploadUrlResponse =
        api.post<CustomsAttachmentUploadUrlResponse, UploadUrlBody>(
            "/agent-invoices/upload-url",
            UploadUrlBody(filename = filename)
        )
}

@Serializable
internal data class UploadUrlBody(val filename: String? = null)
