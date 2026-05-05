package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.RetailerDto
import com.thapsus.cargo.data.dto.RetailersListResponse
import com.thapsus.cargo.data.remote.ThapsusApiClient

/**
 * Read-only catalog of curated retailers (migration 029 / server PR 4).
 *
 * Single endpoint — GET /api/retailers — returns active retailers in
 * sort order. Both webapp and iOS use this to populate the Buy-for-me
 * form's picker; customers can still choose "Other" and type a free-text
 * URL when their retailer isn't in the catalog.
 */
class RetailersRepository(
    private val api: ThapsusApiClient
) {
    suspend fun list(): Result<List<RetailerDto>> = runCatching {
        api.get<RetailersListResponse>("/retailers").retailers
    }
}
