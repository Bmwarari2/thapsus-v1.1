package com.thapsus.cargo.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    @SerialName("customer") CUSTOMER,
    @SerialName("operator") OPERATOR,
    @SerialName("clearing_agent") CLEARING_AGENT,
    @SerialName("rider") RIDER,
    @SerialName("admin") ADMIN;

    val isStaff: Boolean get() = this == OPERATOR || this == ADMIN
}
