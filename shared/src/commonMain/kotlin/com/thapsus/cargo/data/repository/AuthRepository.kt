package com.thapsus.cargo.data.repository

import com.thapsus.cargo.data.dto.AuthResponseDto
import com.thapsus.cargo.data.dto.ChangePasswordRequest
import com.thapsus.cargo.data.dto.ForgotPasswordRequest
import com.thapsus.cargo.data.dto.GenericAckResponse
import com.thapsus.cargo.data.dto.LoginRequest
import com.thapsus.cargo.data.dto.MeResponseDto
import com.thapsus.cargo.data.dto.RegisterRequest
import com.thapsus.cargo.data.dto.ResendVerificationRequest
import com.thapsus.cargo.data.dto.ResetPasswordRequest
import com.thapsus.cargo.data.dto.VerifyEmailRequest
import com.thapsus.cargo.data.dto.ScUserDto
import com.thapsus.cargo.data.dto.SupabaseTokenResponse
import com.thapsus.cargo.data.dto.UpdateProfileRequest
import com.thapsus.cargo.data.dto.UpdateProfileResponse
import com.thapsus.cargo.data.dto.UserDto
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.remote.AuthEventFlags
import com.thapsus.cargo.data.remote.SecureKeys
import com.thapsus.cargo.data.remote.SecureSettings
import com.thapsus.cargo.data.remote.ThapsusApiClient
import com.thapsus.cargo.domain.model.UserRole
import com.thapsus.cargo.util.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Hybrid auth bridge. Login flow:
 *
 *   1. POST /api/auth/login (Express) → { token (sc_token), supabase_token, user }
 *   2. Persist sc_token  → drives Bearer auth on every Ktor REST call.
 *   3. Persist supabase_token → SupabaseClientFactory's accessTokenProvider
 *      pulls it on every PostgREST/Realtime request so RLS sees auth.uid().
 *   4. Persist the user profile so the next launch can rehydrate offline.
 *
 * Sign-out clears every token in one batch so the next launch lands on
 * SignInView. Phone OTP is unimplemented at v1 — Express only does email+password.
 */
class AuthRepository(
    private val api: ThapsusApiClient,
    private val settings: SecureSettings,
    private val cache: ThapsusLocalCache
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val _state = MutableStateFlow<AuthSession>(AuthSession.Initializing)
    val state: StateFlow<AuthSession> = _state.asStateFlow()

    /** Convenience: current sc_token for outgoing REST calls. */
    fun currentScToken(): String? = settings.getString(SecureKeys.SC_TOKEN)

    /** Convenience: current Supabase JWT, used by SupabaseClientFactory. */
    fun currentSupabaseToken(): String? = settings.getString(SecureKeys.SUPABASE_TOKEN)

    init {
        // Audit follow-up — graceful 401 UX. Wire the AuthEventFlags
        // callback so a server-side 401 (handled in
        // ThapsusApiClient.onUnauthorized) flips this repository's
        // state to SignedOut immediately instead of leaving it stuck
        // at Authenticated until the next rehydrate / app launch.
        // The companion sessionExpired flag is set by
        // markServerSignOut and drives the SignInView banner.
        AuthEventFlags.onServerSignOut = {
            runCatching { cache.clearAll() }
            _state.value = AuthSession.SignedOut
        }

        // Defensive try/catch — `rehydrate` reads SecureSettings + decodes
        // a cached profile; a corrupted blob or settings backend hiccup
        // (rare on iOS, more common after a wipe-app-data cycle) would
        // otherwise propagate to the K/N abort handler at app start.
        scope.launch {
            try {
                rehydrate()
                // Audit W6.1 — silent refresh. Once the local cache has
                // populated state, fire a /me request so any aged sc_token
                // gets rotated before the user starts navigating. Errors
                // are swallowed: the local state is still useful even if
                // the network is down. Run in the same coroutine so we
                // never race rehydrate's _state.value assignment.
                if (_state.value is AuthSession.Authenticated) {
                    runCatching { refreshSession() }
                        .onFailure { e ->
                            println("[AuthRepository] silent refresh failed: ${e::class.simpleName}: ${e.message}")
                        }
                }
            } catch (e: Throwable) {
                _state.value = AuthSession.SignedOut
                println("[AuthRepository] rehydrate failed: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    // ----- Public API -----

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        val resp: AuthResponseDto = api.post<AuthResponseDto, LoginRequest>(
            "/auth/login", LoginRequest(email = email, password = password)
        )
        applyAuthResponse(resp)
    }

    /**
     * Result of POST /auth/register. PR N split registration into two
     * outcomes:
     *   - [VerificationRequired] — the account exists but email confirmation
     *     is pending. No JWT was issued; the client should show a
     *     "check your inbox" surface and wait for the user to hit the
     *     activation link. Calling [verifyEmail] with the token from the
     *     email finishes the flow and lands the session in `Authenticated`.
     *   - [Authenticated] — legacy server that auto-signs the user in
     *     (pre-PR-N). Kept so the same client code base stays compatible
     *     with older deployments during the rollout window.
     */
    sealed class SignUpResult {
        data class VerificationRequired(val email: String) : SignUpResult()
        data object Authenticated : SignUpResult()
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String? = null,
        phone: String? = null,
        countryOfResidence: String? = null
    ): Result<SignUpResult> = runCatching {
        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@').replace(Regex("[._-]+"), " ").trim()
                .ifBlank { "Customer" }
        val resp: AuthResponseDto = api.post<AuthResponseDto, RegisterRequest>(
            "/auth/register",
            RegisterRequest(
                email = email,
                password = password,
                name = resolvedName,
                phone = phone,
                countryOfResidence = countryOfResidence
            )
        )
        if (resp.verificationRequired == true || resp.scToken.isNullOrBlank()) {
            SignUpResult.VerificationRequired(email)
        } else {
            applyAuthResponse(resp)
            SignUpResult.Authenticated
        }
    }

    /**
     * POST /auth/verify-email — consumes the one-shot token from the
     * activation email, stamps `users.email_verified_at` server-side, and
     * returns the same auth bundle `/login` would. On success the
     * AuthSession state flips to Authenticated and the role-routed tab
     * view takes over automatically.
     */
    suspend fun verifyEmail(token: String): Result<Unit> = runCatching {
        val resp: AuthResponseDto = api.post<AuthResponseDto, VerifyEmailRequest>(
            "/auth/verify-email",
            VerifyEmailRequest(token = token)
        )
        applyAuthResponse(resp)
    }

    /**
     * POST /auth/resend-verification — requests a fresh activation email
     * for an unverified account. The server uses an anti-enumeration
     * generic response shape, so this method only signals "request
     * accepted" — it doesn't tell the caller whether an email was
     * actually dispatched. Surface a generic "if your account exists,
     * we've sent a fresh link" message in the UI.
     */
    suspend fun resendVerification(email: String): Result<Unit> = runCatching {
        api.post<GenericAckResponse, ResendVerificationRequest>(
            "/auth/resend-verification",
            ResendVerificationRequest(email = email)
        )
    }

    /** Phone OTP not supported by the Express backend yet — keep the surface
     *  to avoid breaking compile, but fail explicitly so calls don't no-op. */
    suspend fun signInWithPhoneOtp(phone: String): Result<Unit> = Result.failure(
        UnsupportedOperationException("Phone OTP is not yet supported by the Thapsus backend.")
    )

    suspend fun verifyPhoneOtp(phone: String, token: String): Result<Unit> = Result.failure(
        UnsupportedOperationException("Phone OTP is not yet supported by the Thapsus backend.")
    )

    /**
     * Signs the user out. Best-effort calls `POST /api/auth/logout` on the
     * Express backend (see migration 004 / PR #34) so the bearer token is
     * recorded in `revoked_tokens` and can no longer be replayed even before
     * its `exp` elapses. The local-state clear runs unconditionally — if the
     * server call fails (offline, 5xx) we still want the user signed out
     * locally; the server-side revocation is a defense-in-depth layer, not
     * a precondition.
     */
    suspend fun signOut(): Result<Unit> = runCatching {
        runCatching {
            api.post<GenericAckResponse, String>("/auth/logout", null)
        }
        settings.clear()
        // Wipe SQLDelight cache so the next account on this device doesn't
        // see leftover packages/consolidations/tickets from the previous one.
        runCatching { cache.clearAll() }
        _state.value = AuthSession.SignedOut
    }

    /**
     * PUT /api/auth/profile — update name/phone/language/delivery address.
     * Re-emits an updated AuthSession so the UI picks up the new values
     * immediately. Empty strings on `deliveryAddress` are deliberate (the
     * server treats them as a clear); `null` means "field not supplied"
     * and the server leaves it untouched.
     */
    suspend fun updateProfile(
        name: String? = null,
        phone: String? = null,
        languagePref: String? = null,
        deliveryAddress: String? = null
    ): Result<Unit> = runCatching {
        val resp: UpdateProfileResponse = api.put<UpdateProfileResponse, UpdateProfileRequest>(
            "/auth/profile",
            UpdateProfileRequest(
                name = name,
                phone = phone,
                languagePref = languagePref,
                deliveryAddress = deliveryAddress
            )
        )
        val updated = resp.user ?: error("profile update: missing user in response")
        // Swap the stored sc_token if the server returned a fresh one (T21).
        // Required so any subsequent REST call carries the updated `name` /
        // `warehouse_id` claims — middleware/auth.js stamps req.user from the
        // JWT, not from a fresh DB read.
        resp.token?.let { settings.putString(SecureKeys.SC_TOKEN, it) }
        settings.putString(SecureKeys.USER_PROFILE, json.encodeToString(ScUserDto.serializer(), updated))
        _state.value = sessionFromUser(updated)
    }

    /** PUT /api/auth/password — change password while signed in. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        api.put<GenericAckResponse, ChangePasswordRequest>(
            "/auth/password",
            ChangePasswordRequest(currentPassword = currentPassword, newPassword = newPassword)
        )
    }

    /** POST /api/auth/forgot-password — kick off the reset email. */
    suspend fun forgotPassword(email: String): Result<Unit> = runCatching {
        api.post<GenericAckResponse, ForgotPasswordRequest>(
            "/auth/forgot-password",
            ForgotPasswordRequest(email = email)
        )
    }

    /**
     * POST /api/auth/reset-password — completes the reset from a token
     * carried in the email link (Universal Link). Token is the hex string
     * appended to `https://www.thapsus.uk/reset-password?token=...`; the
     * iOS deep-link handler in RootView pulls it out and hands it here.
     */
    suspend fun resetPassword(token: String, newPassword: String): Result<Unit> = runCatching {
        api.post<GenericAckResponse, ResetPasswordRequest>(
            "/auth/reset-password",
            ResetPasswordRequest(token = token, newPassword = newPassword)
        )
    }

    /** Trades the current sc_token for a fresh Supabase JWT. */
    suspend fun refreshSupabaseToken(): Result<Unit> = runCatching {
        val resp: SupabaseTokenResponse = api.post<SupabaseTokenResponse, String>(
            "/auth/supabase-token", null
        )
        val token = resp.supabaseToken ?: error("supabase_token missing")
        val exp = resp.supabaseTokenExpiresAt ?: error("expiry missing")
        settings.putString(SecureKeys.SUPABASE_TOKEN, token)
        settings.putString(SecureKeys.SUPABASE_TOKEN_EXP, exp.toString())
    }

    fun supabaseTokenExpiresWithinSeconds(seconds: Int): Boolean {
        val exp = settings.getString(SecureKeys.SUPABASE_TOKEN_EXP)?.toLongOrNull() ?: return true
        val now = Clock.System.now().epochSeconds
        return (exp - now) < seconds
    }

    /**
     * Audit W6.1 — silent refresh. Calls GET /auth/me; if the server
     * returned a `refreshed_token` (because our presented sc_token's
     * `iat` is older than the server's JWT_REFRESH_AFTER_SECONDS),
     * swap the new token into Keychain and update the cached profile.
     *
     * Fired automatically on app boot from the init block. Public so
     * callers (e.g. an app-foreground listener) can also opt in to
     * an extra rotation if they want a tighter window than the
     * once-per-launch default.
     *
     * Errors are caught by the caller via runCatching — a network
     * blip on launch must never break the local-cache-driven
     * AuthSession.Authenticated state. The next launch tries again.
     */
    suspend fun refreshSession(): Result<Unit> = runCatching {
        val resp: MeResponseDto = api.get<MeResponseDto>("/auth/me")
        // Update the cached user profile from the live server row so
        // a name / email / role change made elsewhere (admin console,
        // self-edit on a different device) lands on the next launch.
        resp.user?.let { user ->
            settings.putString(
                SecureKeys.USER_PROFILE,
                json.encodeToString(ScUserDto.serializer(), user)
            )
            _state.value = sessionFromUser(user)
        }
        // The token swap is the actual W6.1 payload. Only write when
        // present — older server builds omit the field and we'd
        // otherwise overwrite the live token with null.
        resp.refreshedToken?.let { fresh ->
            settings.putString(SecureKeys.SC_TOKEN, fresh)
        }
    }

    // ----- Internals -----

    private fun applyAuthResponse(resp: AuthResponseDto) {
        val sc = resp.scToken ?: error("sc_token missing in auth response")
        val user = resp.user ?: error("user missing in auth response")

        // Flush local SQLDelight cache before swapping tokens so any stale
        // rows from a previous account (or a server-side data purge) can't
        // bleed into the new session. Existing observers re-emit empty;
        // each screen's onAppear `refreshForUser` then repopulates from the
        // live database. Wrapped defensively because a cache failure must
        // not block the user from signing in.
        runCatching { cache.clearAll() }

        settings.putString(SecureKeys.SC_TOKEN, sc)
        resp.supabaseToken?.let { settings.putString(SecureKeys.SUPABASE_TOKEN, it) }
        resp.supabaseTokenExpiresAt?.let {
            settings.putString(SecureKeys.SUPABASE_TOKEN_EXP, it.toString())
        }
        settings.putString(SecureKeys.USER_PROFILE, json.encodeToString(ScUserDto.serializer(), user))
        _state.value = sessionFromUser(user)
    }

    private fun sessionFromUser(user: ScUserDto): AuthSession.Authenticated {
        return AuthSession.Authenticated(
            userId = user.id,
            email = user.email,
            role = roleFromApi(user.role),
            profile = user.toProfile()
        )
    }

    private fun rehydrate() {
        val cached = settings.getString(SecureKeys.USER_PROFILE)
        val sc = settings.getString(SecureKeys.SC_TOKEN)
        if (cached.isNullOrBlank() || sc.isNullOrBlank()) {
            _state.value = AuthSession.SignedOut
            return
        }
        val user = runCatching { json.decodeFromString(ScUserDto.serializer(), cached) }.getOrNull()
        if (user == null) {
            settings.clear()
            _state.value = AuthSession.SignedOut
            return
        }
        _state.value = sessionFromUser(user)
    }
}

private fun ScUserDto.toProfile(): UserDto = UserDto(
    id = id,
    email = email,
    phone = phone,
    fullName = name,
    role = roleFromApi(role),
    warehouseId = warehouseId,
    referralCode = referralCode,
    languagePref = languagePref,
    countryOfResidence = countryOfResidence,
    deliveryAddress = deliveryAddress
)

private fun roleFromApi(value: String): UserRole = when (value.lowercase()) {
    "admin" -> UserRole.ADMIN
    "operator" -> UserRole.OPERATOR
    "clearing_agent" -> UserRole.CLEARING_AGENT
    "rider" -> UserRole.RIDER
    else -> UserRole.CUSTOMER
}

sealed interface AuthSession {
    data object Initializing : AuthSession
    data object SignedOut : AuthSession
    data class Authenticated(
        val userId: String,
        val email: String?,
        val role: UserRole,
        val profile: UserDto?
    ) : AuthSession
}
