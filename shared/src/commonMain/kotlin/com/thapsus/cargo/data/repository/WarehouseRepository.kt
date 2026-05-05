package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.WarehouseAddressDto
import com.thapsus.cargo.data.dto.WarehouseAddressesResponse
import com.thapsus.cargo.data.remote.ThapsusApiClient

class WarehouseRepository(private val api: ThapsusApiClient) {

    suspend fun addresses(): Result<Map<String, WarehouseAddressDto>> = runCatching {
        api.get<WarehouseAddressesResponse>("/warehouse/addresses").addresses
    }
}
