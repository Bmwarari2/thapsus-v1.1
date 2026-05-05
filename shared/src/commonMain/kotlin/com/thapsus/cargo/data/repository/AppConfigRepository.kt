package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.AppConfigDto
import com.thapsus.cargo.data.dto.AppConfigResponse
import com.thapsus.cargo.data.remote.ThapsusApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Server-driven app config (audit S2-3). One-shot fetch on app launch /
 * sign-in, exposed as a hot StateFlow so views can read synchronously
 * without re-issuing the request. Falls back to the bundled defaults
 * (the same values the server emits) if the network fetch fails — that
 * way the WhatsApp button + warehouse barcode still work offline / on
 * day-one provisioning before the env vars are configured on Railway.
 */
class AppConfigRepository(
    private val api: ThapsusApiClient
) {
    private val _config = MutableStateFlow(AppConfigDto())
    val config: StateFlow<AppConfigDto> = _config.asStateFlow()

    private var lastRefreshMs: Long = 0
    private val ttlMs: Long = 5 * 60 * 1000

    /**
     * Fetch the latest config. Cheap to call repeatedly — TTL-gated so
     * appearance hooks ("on screen open", "on app foreground") don't
     * thunder the endpoint.
     */
    suspend fun refresh(force: Boolean = false): Result<AppConfigDto> = runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        if (!force && now - lastRefreshMs < ttlMs && lastRefreshMs > 0) {
            return@runCatching _config.value
        }
        val resp = api.get<AppConfigResponse>("/app-config")
        _config.value = resp.config
        lastRefreshMs = now
        resp.config
    }
}
