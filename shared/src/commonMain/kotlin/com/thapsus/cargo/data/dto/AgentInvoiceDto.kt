package com.thapsus.cargo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentInvoiceDto(
    val id: String,
    @SerialName("agent_id") val agentId: String,
    @SerialName("consolidation_id") val consolidationId: String? = null,
    @SerialName("invoice_no") val invoiceNo: String? = null,
    @SerialName("amount_kes")
    @Serializable(with = LooseDoubleSerializer::class)
    val amountKes: Double = 0.0,
    @SerialName("doc_url") val docUrl: String? = null,
    val status: String = "submitted",
    val notes: String? = null,
    @SerialName("agent_name") val agentName: String? = null,
    @SerialName("agent_email") val agentEmail: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null
)

@Serializable
data class AgentInvoiceListResponse(
    val success: Boolean = true,
    val invoices: List<AgentInvoiceDto> = emptyList()
)

@Serializable
data class CreateAgentInvoiceRequest(
    @SerialName("consolidation_id") val consolidationId: String? = null,
    @SerialName("invoice_no") val invoiceNo: String? = null,
    @SerialName("amount_kes") val amountKes: Double,
    @SerialName("doc_url") val docUrl: String? = null,
    val notes: String? = null
)

@Serializable
data class CreateAgentInvoiceResponse(
    val success: Boolean = true,
    @SerialName("invoice_id") val invoiceId: String? = null
)
