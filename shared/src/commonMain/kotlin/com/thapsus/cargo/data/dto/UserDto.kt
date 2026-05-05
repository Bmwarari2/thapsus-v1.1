package com.thapsus.cargo.data.dto

import com.thapsus.cargo.domain.model.UserRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("role") val role: UserRole = UserRole.CUSTOMER,
    @SerialName("warehouse_id") val warehouseId: String? = null,
    @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("language_pref") val languagePref: String = "en",
    @SerialName("country_of_residence") val countryOfResidence: String? = null,
    @SerialName("delivery_address") val deliveryAddress: String? = null,
    @SerialName("kyc_status") val kycStatus: String = "none",
    @SerialName("kyc_doc_url") val kycDocUrl: String? = null,
    @SerialName("marketing_consent_at") val marketingConsentAt: String? = null,
    @SerialName("utm_source") val utmSource: String? = null,
    @SerialName("utm_medium") val utmMedium: String? = null,
    @SerialName("utm_campaign") val utmCampaign: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
