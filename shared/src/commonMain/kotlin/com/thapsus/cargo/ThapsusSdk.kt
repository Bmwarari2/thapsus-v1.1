package com.thapsus.cargo

import com.thapsus.cargo.data.local.DatabaseDriverFactory
import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.remote.RealtimeSync
import com.thapsus.cargo.data.remote.SecureSettings
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
import com.thapsus.cargo.data.repository.OrdersRepository
import com.thapsus.cargo.data.repository.TrackingRepository
import com.thapsus.cargo.data.repository.PackageRepository
import com.thapsus.cargo.data.repository.PricingRepository
import com.thapsus.cargo.data.repository.ProhibitedRepository
import com.thapsus.cargo.data.repository.ReferralsRepository
import com.thapsus.cargo.data.repository.StorageRepository
import com.thapsus.cargo.data.repository.TicketsRepository
import com.thapsus.cargo.data.repository.WarehouseRepository
import com.thapsus.cargo.di.ThapsusKoin
import com.thapsus.cargo.domain.pricing.QuoteEngine
import com.thapsus.cargo.presentation.AdminDashboardViewModel
import com.thapsus.cargo.presentation.AgentInvoicesViewModel
import com.thapsus.cargo.presentation.AuthViewModel
import com.thapsus.cargo.presentation.BuyForMeViewModel
import com.thapsus.cargo.presentation.OpsSettingsViewModel
import com.thapsus.cargo.presentation.ConsolidationDetailViewModel
import com.thapsus.cargo.presentation.ConsolidationListViewModel
import com.thapsus.cargo.presentation.CustomerConsolidationViewModel
import com.thapsus.cargo.presentation.CustomerDashboardViewModel
import com.thapsus.cargo.presentation.CustomsAgentViewModel
import com.thapsus.cargo.presentation.DispatchViewModel
import com.thapsus.cargo.presentation.DsarViewModel
import com.thapsus.cargo.presentation.InsuranceViewModel
import com.thapsus.cargo.presentation.IntakeViewModel
import com.thapsus.cargo.presentation.KPIDashboardViewModel
import com.thapsus.cargo.presentation.NotificationInboxViewModel
import com.thapsus.cargo.presentation.OperatorTodayViewModel
import com.thapsus.cargo.presentation.SkuScannerViewModel
import com.thapsus.cargo.presentation.OutboxViewModel
import com.thapsus.cargo.presentation.ParcelPreRegViewModel
import com.thapsus.cargo.presentation.ProfileEditViewModel
import com.thapsus.cargo.presentation.ProhibitedSearchViewModel
import com.thapsus.cargo.presentation.QuoteViewModel
import com.thapsus.cargo.presentation.ReferralViewModel
import com.thapsus.cargo.presentation.RiderRunViewModel
import com.thapsus.cargo.presentation.RunStopsViewModel
import com.thapsus.cargo.presentation.TicketDetailViewModel
import com.thapsus.cargo.presentation.TicketsListViewModel
import com.thapsus.cargo.presentation.WarehouseViewModel
import com.thapsus.cargo.util.installUnhandledExceptionHook
import io.github.jan.supabase.SupabaseClient
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Single-entry façade that the SwiftUI layer uses to bootstrap the shared module.
 * Declared as a Kotlin `object` so K/N exports it as `ThapsusSdk.shared` in Swift.
 */
object ThapsusSdk {

    private var application: KoinApplication? = null

    fun start(
        supabaseUrl: String,
        supabaseAnonKey: String,
        apiBaseUrl: String,
        driverFactory: DatabaseDriverFactory,
        secureSettings: SecureSettings
    ) {
        if (application != null) return
        // Install BEFORE Koin / repos / scopes are created so any throw during
        // bootstrap (corrupt secure-settings, bad Koin module, etc.) lands as
        // a logged Kotlin exception in the Xcode console instead of an opaque
        // K/N abort with only `Kotlin_processUnhandledException` in the trace.
        installUnhandledExceptionHook()
        val configModule = module {
            single(ThapsusKoin.SUPABASE_URL) { supabaseUrl }
            single(ThapsusKoin.SUPABASE_KEY) { supabaseAnonKey }
            single(ThapsusKoin.API_BASE_URL) { apiBaseUrl }
        }
        application = startKoin {
            modules(configModule, ThapsusKoin.module(driverFactory, secureSettings))
        }
    }

    private fun koin() = application?.koin
        ?: error("ThapsusSdk not started — call start(...) first")

    // ----- Repositories -----
    fun auth(): AuthRepository = koin().get()
    fun packages(): PackageRepository = koin().get()
    fun consolidations(): ConsolidationRepository = koin().get()
    fun customs(): CustomsRepository = koin().get()
    fun lastMile(): LastMileRepository = koin().get()
    fun pricing(): PricingRepository = koin().get()
    fun storage(): StorageRepository = koin().get()
    fun realtime(): RealtimeSync = koin().get()
    fun quoteEngine(): QuoteEngine = koin().get()
    fun payments(): com.thapsus.cargo.data.repository.PaymentsRepository = koin().get()
    fun orders(): OrdersRepository = koin().get()
    fun notifications(): NotificationsRepository = koin().get()
    fun nps(): NpsRepository = koin().get()
    fun tracking(): TrackingRepository = koin().get()
    fun kpi(): KpiRepository = koin().get()
    fun tickets(): TicketsRepository = koin().get()
    fun referrals(): ReferralsRepository = koin().get()
    fun dsar(): DsarRepository = koin().get()
    fun buyForMe(): BuyForMeRepository = koin().get()
    fun warehouse(): WarehouseRepository = koin().get()
    fun insurance(): InsuranceRepository = koin().get()
    fun prohibited(): ProhibitedRepository = koin().get()
    fun agentInvoices(): AgentInvoicesRepository = koin().get()
    fun customerConsolidations(): CustomerConsolidationsRepository = koin().get()
    fun adminRepo(): AdminRepository = koin().get()
    fun pricingTiers(): PricingTiersRepository = koin().get()
    fun appConfig(): AppConfigRepository = koin().get()
    fun retailers(): com.thapsus.cargo.data.repository.RetailersRepository = koin().get()
    private fun cache(): ThapsusLocalCache = koin().get()
    private fun supabase(): SupabaseClient = koin().get()

    /**
     * Snapshot of the current Supabase JWT, or null if signed out / not yet
     * exchanged. Synchronous so Swift can use it without going through the
     * K/N coroutine bridge — used by the agent-invoice direct-upload path
     * that writes via URLSession instead of supabase-kt.
     */
    fun currentSupabaseToken(): String? {
        val settings: com.thapsus.cargo.data.remote.SecureSettings = koin().get()
        return settings.getString(com.thapsus.cargo.data.remote.SecureKeys.SUPABASE_TOKEN)
    }

    /**
     * Record that the authed customer has just opened the destination behind
     * a home-screen greeting. Writes a row to the seen-marker table; the home
     * VM observes that table reactively so the relevant Status greeting
     * drops on the next emission with no manual refresh.
     *
     * Called from non-home view-models (e.g. TicketDetailScreen) that don't
     * hold a `CustomerDashboardViewModel` reference. Resolves the user-id
     * from the current `AuthSession.Authenticated`; no-ops while signed out.
     */
    fun markHomeGreetingSeen(greetingId: String) {
        val session = auth().state.value as? com.thapsus.cargo.data.repository.AuthSession.Authenticated
            ?: return
        val nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        cache().markHomeGreetingSeen(session.userId, greetingId, nowMs)
    }

    // ----- View-model factories (Swift owns lifetime via clear()) -----
    fun authViewModel() = AuthViewModel(auth())

    fun customerDashboardViewModel(userId: String) =
        CustomerDashboardViewModel(
            userId = userId,
            packages = packages(),
            consolidations = customerConsolidations(),
            buyForMe = buyForMe(),
            payments = payments(),
            orders = orders(),
            dsar = dsar(),
            nps = nps(),
            tickets = tickets(),
            referrals = referrals(),
            auth = auth(),
            cache = cache()
        )

    fun parcelPreRegViewModel(userId: String) =
        ParcelPreRegViewModel(userId, orders(), packages())

    fun intakeViewModel(operatorId: String) =
        IntakeViewModel(operatorId, packages())

    fun skuScannerViewModel() = SkuScannerViewModel(packages())

    fun riderRunViewModel(riderId: String) =
        RiderRunViewModel(riderId, lastMile())

    fun runStopsViewModel(runId: String) =
        RunStopsViewModel(runId, lastMile())

    fun outboxViewModel() = OutboxViewModel(cache(), lastMile())

    fun quoteViewModel() = QuoteViewModel(pricing(), quoteEngine())

    fun operatorTodayViewModel() =
        OperatorTodayViewModel(supabase(), cache(), packages())

    fun consolidationListViewModel() = ConsolidationListViewModel(consolidations())

    fun consolidationDetailViewModel(consolidationId: String) =
        ConsolidationDetailViewModel(consolidationId, cache(), packages(), consolidations())

    fun dispatchViewModel() = DispatchViewModel(supabase(), cache(), lastMile())

    fun kpiDashboardViewModel() = KPIDashboardViewModel(cache(), adminRepo(), kpi())

    fun customsAgentViewModel(agentId: String) =
        CustomsAgentViewModel(agentId, customs(), koin().get())

    fun paymentsViewModel() = com.thapsus.cargo.presentation.PaymentsViewModel(payments())
    fun adminPaymentsViewModel() = com.thapsus.cargo.presentation.AdminPaymentsViewModel(payments())
    fun transactionsViewModel() = com.thapsus.cargo.presentation.TransactionsViewModel(payments())
    fun adminIssueInvoiceViewModel() =
        com.thapsus.cargo.presentation.AdminIssueInvoiceViewModel(adminRepo(), customerConsolidations())
    fun adminCreateBuyForMeViewModel() =
        com.thapsus.cargo.presentation.AdminCreateBuyForMeViewModel(adminRepo(), buyForMe(), retailers())

    fun npsSurveyViewModel(parcelId: String? = null) =
        com.thapsus.cargo.presentation.NpsSurveyViewModel(nps(), parcelId)

    fun publicTrackingViewModel() =
        com.thapsus.cargo.presentation.PublicTrackingViewModel(tracking())

    fun cutoffBannerViewModel() =
        com.thapsus.cargo.presentation.CutoffBannerViewModel(consolidations())

    // ----- Phase 1 customer ViewModels -----

    fun notificationInboxViewModel(userId: String) = NotificationInboxViewModel(userId, notifications())

    fun profileEditViewModel() = ProfileEditViewModel(auth())

    fun warehouseViewModel() = WarehouseViewModel(warehouse())

    fun prohibitedSearchViewModel() = ProhibitedSearchViewModel(prohibited())

    fun referralViewModel() = ReferralViewModel(referrals())

    fun dsarViewModel() = DsarViewModel(dsar())

    fun accountDeletionRepo(): com.thapsus.cargo.data.repository.AccountDeletionRepository =
        koin().get()

    fun accountDeletionViewModel() =
        com.thapsus.cargo.presentation.AccountDeletionViewModel(accountDeletionRepo())

    fun ticketsListViewModel(userId: String) = TicketsListViewModel(tickets(), userId = userId, asAdmin = false)

    fun adminTicketsListViewModel() = TicketsListViewModel(tickets(), userId = null, asAdmin = true)

    fun ticketDetailViewModel(ticketId: String) =
        TicketDetailViewModel(ticketId = ticketId, tickets = tickets())

    fun insuranceViewModel() = InsuranceViewModel(insurance())

    fun buyForMeViewModel() = BuyForMeViewModel(buyForMe(), retailers())

    fun opsBuyForMeViewModel() =
        com.thapsus.cargo.presentation.OpsBuyForMeViewModel(buyForMe())

    fun customerConsolidationViewModel(consolidationId: String) =
        CustomerConsolidationViewModel(consolidationId = consolidationId, consolidations = koin().get())

    // ----- Phase 2 ops/admin ViewModels -----

    fun agentInvoicesViewModel() = AgentInvoicesViewModel(agentInvoices())

    fun adminDashboardViewModel() = AdminDashboardViewModel(adminRepo())

    fun opsSettingsViewModel() = OpsSettingsViewModel(
        pricing = pricingTiers(),
        pricingRead = pricing(),
        admin = adminRepo(),
        prohibitedRepo = prohibited()
    )

    // ----- Phase 4 admin parity ViewModels -----

    fun adminUsersViewModel() =
        com.thapsus.cargo.presentation.AdminUsersViewModel(adminRepo())

    fun adminUserDetailViewModel(userId: String) =
        com.thapsus.cargo.presentation.AdminUserDetailViewModel(userId, adminRepo())

    fun adminOrdersViewModel() =
        com.thapsus.cargo.presentation.AdminOrdersViewModel(adminRepo())

    fun adminOrderDetailViewModel(orderId: String) =
        com.thapsus.cargo.presentation.AdminOrderDetailViewModel(orderId, adminRepo())

    fun adminErrorLogsViewModel() =
        com.thapsus.cargo.presentation.AdminErrorLogsViewModel(adminRepo())
}
