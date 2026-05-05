package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `POST /api/parcels/upload-url` — operator intake
 * photo upload (audit N1). The endpoint mints a 5-minute signed PUT URL
 * into the public `parcels` bucket so iOS can `URLSession.upload` raw
 * Swift `Data` directly, bypassing the K/N `KotlinByteArray.from(Data)`
 * bridge that froze the app on multi-MB photos (audit D19).
 */
@Serializable
data class ParcelUploadUrlRequest(
    @SerialName("parcel_id") val parcelId: String
)

/**
 * Response from `POST /api/parcels/upload-url`. The bucket is public, so
 * `publicUrl` resolves directly — `packages.intake_photo_url` is set
 * to it after the PUT completes. No companion document-url endpoint
 * needed (unlike the private `pods` bucket).
 */
@Serializable
data class ParcelUploadUrlResponse(
    val success: Boolean = true,
    val bucket: String? = null,
    val path: String? = null,
    @SerialName("signed_url") val signedUrl: String? = null,
    val token: String? = null,
    @SerialName("public_url") val publicUrl: String? = null
)
