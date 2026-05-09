package com.thapsus.cargo.di

import com.thapsus.cargo.data.local.DatabaseDriverFactory
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.remote.AuthEventFlags
import com.thapsus.cargo.data.remote.RealtimeSync
import com.thapsus.cargo.data.remote.SecureKeys
import com.thapsus.cargo.data.remote.SecureSettings
import com.thapsus.cargo.data.remote.SupabaseClientFactory
import com.thapsus.cargo.data.remote.ThapsusApiClient
import com.thapsus.cargo.data.repository.AdminRepository
import com.thapsus.cargo.data.repository.AgentInvoicesRepository
import com.thapsus.cargo.data.repository.CustomerConsolidationsRepository
import com.thapsus.cargo.data.repository.AppConfigRepository
import com.thapsus.cargo.data.repository.AuthRepository
import com.thapsus.cargo.data.repository.BuyForMeRepository
import com.thapsus.cargo.data.repository.PricingTiersRepository
import com.thapsus.cargo.data.repository.ConsolidationRepository
import com.thapsus.cargo.data.repository.CustomsRepository
import com.thapsus.cargo.data.repository.DsarRepository
import com.thapsus.cargo.data.repository.InsuranceRepository
import com.thapsus.cargo.data.repository.KpiRepository
import com.thapsus.cargo.data.repository.LastMileRepository
import com.thapsus.cargo.data.repository.NotificationsRepository
import com.thapsus.cargo.data.repository.NpsRepository
import com.thapsus.cargo.data.repository.TrackingRepository
import com.thapsus.cargo.data.repository.OrdersRepository
import com.thapsus.cargo.data.repository.PackageRepository
import com.thapsus.cargo.data.repository.PricingRepository
import com.thapsus.cargo.data.repository.ProhibitedRepository
import com.thapsus.cargo.data.repository.ReferralsRepository
import com.thapsus.cargo.data.repository.StorageRepository
import com.thapsus.cargo.data.repository.TicketsRepository
import com.thapsus.cargo.data.repository.PaymentsRepository
import com.thapsus.cargo.data.repository.RetailersRepository
import com.thapsus.cargo.data.repository.WarehouseRepository
import com.thapsus.cargo.domain.pricing.QuoteEngine
import io.github.jan.supabase.SupabaseClient
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

object ThapsusKoin {
    val SUPABASE_URL = named("supabase.url")
    val SUPABASE_KEY = named("supabase.anonKey")
    val API_BASE_URL = named("api.baseUrl")

    fun module(
        driverFactory: DatabaseDriverFactory,
        secureSettings: SecureSettings
    ): Module = module {
        single { ThapsusLocalCache(driverFactory) }
        single { secureSettings }

        // HttpClient — platform-specific (Darwin engine on iOS).
        single { HttpClient() }

        // ThapsusApiClient depends on SecureSettings (for sc_token) BUT not on
        // AuthRepository (which would be a cycle). The unauthorized hook clears
        // the cached tokens; UI noticed via the AuthRepository state flow.
        single {
            val settings: SecureSettings = get()
            ThapsusApiClient(
                baseUrl = get<String>(API_BASE_URL),
                tokenProvider = { settings.getString(SecureKeys.SC_TOKEN) },
                onUnauthorized = {
                    // Audit follow-up — graceful 401 UX. Clear the
                    // Keychain entries first so any in-flight reads
                    // see no token, then signal AuthEventFlags. The
                    // signal flips the AuthRepository state flow to
                    // SignedOut (via the callback wired in
                    // AuthRepository.init) AND raises the
                    // sessionExpired flag the SignInView reads on
                    // appear to show its "your session expired"
                    // banner.
                    settings.clear()
                    AuthEventFlags.markServerSignOut()
                },
                engine = get()
            )
        }

        single { AuthRepository(api = get(), settings = get(), cache = get()) }

        // Supabase client uses the Express-minted Supabase JWT for PostgREST/Realtime.
        // The token has a ~1h TTL, so the provider refreshes it via /auth/supabase-token
        // when it's within 60s of expiry — otherwise Realtime channels reconnect with
        // an expired bearer and the websocket is rejected with InvalidJWTToken.
        single<SupabaseClient> {
            val settings: SecureSettings = get()
            val auth: AuthRepository = get()
            SupabaseClientFactory(
                supabaseUrl = get<String>(SUPABASE_URL),
                supabaseAnonKey = get<String>(SUPABASE_KEY),
                accessTokenProvider = {
                    if (auth.supabaseTokenExpiresWithinSeconds(60)) {
                        auth.refreshSupabaseToken()
                    }
                    settings.getString(SecureKeys.SUPABASE_TOKEN)
                }
            ).create()
        }
        single { PackageRepository(get(), get(), get()) }
        single { ConsolidationRepository(supabase = get(), cache = get(), api = get()) }
        single { CustomsRepository(supabase = get(), cache = get(), api = get()) }
        single { LastMileRepository(get(), get(), get()) }
        single { PricingRepository(api = get()) }
        single { StorageRepository(supabase = get(), api = get()) }
        single { RealtimeSync(get(), get()) }
        single { QuoteEngine() }
        single { PaymentsRepository(api = get()) }
        single { OrdersRepository(supabase = get(), api = get()) }
        single { NotificationsRepository(api = get(), supabase = get(), cache = get()) }
        single { NpsRepository(api = get()) }
        single { TrackingRepository(api = get()) }
        single { KpiRepository(api = get()) }
        single { TicketsRepository(api = get(), supabase = get(), cache = get()) }
        single { ReferralsRepository(api = get()) }
        single { DsarRepository(api = get()) }
        single { BuyForMeRepository(api = get(), supabase = get()) }
        single { WarehouseRepository(api = get()) }
        single { InsuranceRepository(api = get()) }
        single { ProhibitedRepository(api = get()) }
        single { AgentInvoicesRepository(api = get()) }
        single { CustomerConsolidationsRepository(supabase = get(), api = get()) }
        single { AdminRepository(api = get(), supabase = get()) }
        single { PricingTiersRepository(api = get()) }
        single { AppConfigRepository(api = get()) }
        single { RetailersRepository(api = get()) }
    }
}
