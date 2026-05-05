package com.thapsus.cargo.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * REST client that talks to the Express backend. Mirrors `client/src/api/client.js`:
 *   • Bearer auth using sc_token from /api/auth/login
 *   • 15-second timeout
 *   • Error envelope: { success: false, message: "..." }
 *
 * Writes (wallet top-up, order creation, status changes) go through this client.
 * Reads + Realtime stay on the Supabase client (with the supabase_token JWT).
 */
class ThapsusApiClient(
    baseUrl: String,
    @PublishedApi internal val tokenProvider: () -> String?,
    @PublishedApi internal val onUnauthorized: suspend () -> Unit,
    engine: HttpClient
) {
    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @PublishedApi
    internal val normalisedBase: String =
        if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

    @PublishedApi
    internal val httpClient: HttpClient = engine.config {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        defaultRequest {
            url.takeFrom(normalisedBase)
            contentType(ContentType.Application.Json)
        }
    }

    suspend inline fun <reified T> get(path: String): T {
        val response = httpClient.get(buildPath(path)) { authorise() }
        return decode(path, response)
    }

    suspend inline fun <reified T, reified B : Any> post(path: String, body: B?): T {
        val response = httpClient.post(buildPath(path)) {
            authorise()
            if (body != null) setBody(body)
        }
        return decode(path, response)
    }

    suspend inline fun <reified T, reified B : Any> put(path: String, body: B?): T {
        val response = httpClient.put(buildPath(path)) {
            authorise()
            if (body != null) setBody(body)
        }
        return decode(path, response)
    }

    suspend inline fun <reified T, reified B : Any> patch(path: String, body: B?): T {
        val response = httpClient.patch(buildPath(path)) {
            authorise()
            if (body != null) setBody(body)
        }
        return decode(path, response)
    }

    suspend inline fun <reified T> delete(path: String): T {
        val response = httpClient.delete(buildPath(path)) { authorise() }
        return decode(path, response)
    }

    /**
     * Variant that ships a JSON body on a DELETE — Express endpoints that
     * take `{ path }` to identify which orphan to clean up
     * (e.g. agent-invoice / parcel / pod orphan-cleanup endpoints).
     * Plain DELETE-with-no-body would have no way to disambiguate.
     */
    suspend inline fun <reified T, reified B : Any> delete(path: String, body: B?): T {
        val response = httpClient.delete(buildPath(path)) {
            authorise()
            if (body != null) setBody(body)
        }
        return decode(path, response)
    }

    @PublishedApi
    internal fun buildPath(path: String): String =
        if (path.startsWith("/")) "$normalisedBase$path" else "$normalisedBase/$path"

    @PublishedApi
    internal fun HttpRequestBuilder.authorise() {
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    @PublishedApi
    internal suspend inline fun <reified T> decode(path: String, response: HttpResponse): T {
        if (response.status == HttpStatusCode.Unauthorized && !path.contains("/auth/")) {
            onUnauthorized()
            throw ApiException(401, "Session expired. Please sign in again.")
        }
        if (response.status.value !in 200..299) {
            val envelope = runCatching { response.body<ErrorEnvelope>() }.getOrNull()
            val base = envelope?.message ?: "Request failed (${response.status.value})"
            // Pre-launch dev: server may include `detail` (raw Postgres message)
            // alongside the user-facing message. Surface it so the UI banner
            // shows the actual cause without needing Railway log access.
            val detail = envelope?.detail?.takeIf { it.isNotBlank() }
            val msg = if (detail != null) "$base — $detail" else base
            throw ApiException(response.status.value, msg)
        }
        return response.body()
    }
}

@kotlinx.serialization.Serializable
data class ErrorEnvelope(
    val success: Boolean = false,
    val message: String = "Something went wrong",
    val detail: String? = null
)

class ApiException(val status: Int, message: String) : RuntimeException(message)
