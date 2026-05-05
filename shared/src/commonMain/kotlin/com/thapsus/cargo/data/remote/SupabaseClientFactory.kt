package com.thapsus.cargo.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Builds the singleton Supabase client used by repositories.
 *
 * Anonymous public key only. Service-role keys must never reach a mobile binary.
 * The URL + anon key are injected from the iOS app at startup (see ThapsusSdk).
 *
 * `accessTokenProvider` lets the hybrid auth flow route Supabase requests
 * (PostgREST + Realtime) through the Express-minted Supabase JWT instead of
 * the anon key, so RLS sees `auth.uid()` and Realtime channels are scoped.
 */
class SupabaseClientFactory(
    private val supabaseUrl: String,
    private val supabaseAnonKey: String,
    private val accessTokenProvider: (suspend () -> String?)? = null
) {
    fun create(): SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseAnonKey
    ) {
        // Hybrid auth: Express owns password verification and mints the Supabase
        // JWT we hand over here. supabase-kt forbids combining the Auth plugin
        // with a custom accessToken provider — so the Auth plugin stays out and
        // PostgREST/Realtime/Storage just read the bearer token from this hook.
        accessTokenProvider?.let { provider ->
            accessToken = { provider() ?: supabaseAnonKey }
        }

        install(Postgrest)
        install(Storage)
        install(Realtime)
        install(Functions)
    }
}
