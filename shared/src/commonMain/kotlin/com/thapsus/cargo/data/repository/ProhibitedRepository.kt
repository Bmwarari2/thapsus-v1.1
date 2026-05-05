package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.ProhibitedItemDto
import com.thapsus.cargo.data.remote.ThapsusApiClient
import kotlinx.serialization.Serializable

@Serializable
data class ProhibitedCheckResponse(
    val success: Boolean = true,
    val items: List<ProhibitedItemDto> = emptyList()
)

@Serializable
data class ProhibitedCategorySummary(
    val category: String,
    @kotlinx.serialization.SerialName("risk_level") val riskLevel: String? = null,
    @kotlinx.serialization.SerialName("item_count") val itemCount: Int = 0,
    val reason: String? = null
)

@Serializable
data class ProhibitedCategoriesResponse(
    val success: Boolean = true,
    val categories: List<ProhibitedCategorySummary> = emptyList()
)

@Serializable
data class ProhibitedCategoryBody(
    val category: String,
    @kotlinx.serialization.SerialName("risk_level") val riskLevel: String? = null,
    val reason: String? = null,
    val items: List<String> = emptyList()
)

@Serializable
data class ProhibitedCategoryDetailResponse(
    val success: Boolean = true,
    val category: ProhibitedCategoryBody? = null
)

class ProhibitedRepository(private val api: ThapsusApiClient) {

    /** Search the prohibited dictionary by free-text term. */
    suspend fun check(query: String, language: String = "en"): Result<List<ProhibitedItemDto>> = runCatching {
        val q = query.trim().ifEmpty { return@runCatching emptyList() }
        api.get<ProhibitedCheckResponse>("/prohibited/search?q=$q&language=$language").items
    }

    suspend fun categories(): Result<List<ProhibitedCategorySummary>> = runCatching {
        api.get<ProhibitedCategoriesResponse>("/prohibited/categories").categories
    }

    suspend fun categoryDetail(name: String): Result<ProhibitedCategoryBody> = runCatching {
        // Re-encode spaces for the URL path; let Ktor handle other percent-escapes.
        val encoded = name.replace(" ", "%20")
        api.get<ProhibitedCategoryDetailResponse>("/prohibited/categories/$encoded").category
            ?: error("Category not found: $name")
    }

    // ----- Admin CRUD -----

    suspend fun create(
        term: String,
        severity: String = "prohibited",
        jurisdiction: String = "KE",
        language: String = "en",
        reason: String? = null
    ): Result<String?> = runCatching {
        api.post<com.thapsus.cargo.data.dto.ProhibitedAdminAckResponse, com.thapsus.cargo.data.dto.CreateProhibitedRequest>(
            "/prohibited",
            com.thapsus.cargo.data.dto.CreateProhibitedRequest(
                term = term,
                severity = severity,
                jurisdiction = jurisdiction,
                language = language,
                reason = reason
            )
        ).id
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        api.delete<com.thapsus.cargo.data.dto.ProhibitedAdminAckResponse>("/prohibited/$id")
    }
}
