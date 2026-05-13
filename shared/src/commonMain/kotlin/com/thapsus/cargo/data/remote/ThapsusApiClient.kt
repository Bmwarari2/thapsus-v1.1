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
            val raw = envelope?.message?.takeIf { it.isNotBlank() }
                ?: "Request failed (${response.status.value})"
            // `detail` is intentionally dropped from the user-facing
            // message — the server attaches the raw Postgres / framework
            // string here for log-side debugging, never for end users.
            // sanitize() additionally rewrites any message that leaks
            // HTTP verbs, paths, SQL keywords, or stack-trace fragments
            // into a friendly fallback so a stale endpoint or a
            // hand-typed dev error never lands in a customer banner.
            throw ApiException(response.status.value, sanitize(raw, response.status.value))
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

/**
 * Filter dev-facing strings out of error messages before they bubble
 * up to UI banners. The server's PRODUCTION-correct messages
 * ("Email already taken", "Order not found", "Insufficient balance")
 * pass through unchanged. Strings that look like deprecation hints,
 * raw stack traces, SQL fragments, or HTTP routing instructions get
 * collapsed to a status-appropriate friendly fallback.
 *
 * Visible for testing.
 */
@PublishedApi
internal fun sanitize(raw: String, status: Int): String {
    val techHints = listOf(
        Regex("""\b(POST|GET|PUT|DELETE|PATCH)\s+/""", RegexOption.IGNORE_CASE),
        Regex("""(/api/|/rest/|/v\d+/|/buy-for-me/|/auth/|/admin/)""", RegexOption.IGNORE_CASE),
        Regex("""\btarget_kind\b|\btarget_id\b|\buser_id\b|\bpayment_id\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(SELECT|INSERT|UPDATE|DELETE FROM|JOIN|WHERE|LIMIT)\b\s+[a-z_"\(\.]""", RegexOption.IGNORE_CASE),
        Regex("""null\s*pointer|cannot\s+be\s+null|NullPointerException|undefined\s+is\s+not""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+[a-zA-Z_][\w$.]*\.[a-zA-Z_]\w*\("""),
        Regex("""\b(removed|deprecated)\b.*\b(use|call)\b""", RegexOption.IGNORE_CASE)
    )
    return if (techHints.any { it.containsMatchIn(raw) }) friendlyForStatus(status) else raw
}

@PublishedApi
internal fun friendlyForStatus(status: Int): String = when (status) {
    400 -> "We couldn't process that. Please check your details and try again."
    401, 403 -> "You're not allowed to do that right now."
    404 -> "We couldn't find what you were looking for."
    408, 429 -> "The request timed out. Please try again in a moment."
    in 500..599 -> "Something's not right on our side. We're on it — please try again shortly."
    else -> "Something went wrong. Please try again."
}
