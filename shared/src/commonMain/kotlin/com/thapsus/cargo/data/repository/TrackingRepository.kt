package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.TrackingDto
import com.thapsus.cargo.data.dto.TrackingResponse
import com.thapsus.cargo.data.remote.ThapsusApiClient

/**
 * Public parcel tracking. `GET /api/tracking/:trackingNumber` is open to
 * unauthenticated callers, so anyone with a tracking number can render the
 * 7-step timeline. Authenticated users see richer detail via `OrdersRepository`.
 */
class TrackingRepository(private val api: ThapsusApiClient) {
    suspend fun publicTrack(trackingNumber: String): Result<TrackingDto> = runCatching {
        api.get<TrackingResponse>("/tracking/$trackingNumber").tracking
            ?: error("Tracking response missing payload")
    }
}
