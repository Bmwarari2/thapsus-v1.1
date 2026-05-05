package com.thapsus.cargo.data.remote

/** Centralised table-name constants — keep in sync with Supabase migrations. */
object Tables {
    const val USERS = "users"
    const val PACKAGES = "packages"
    const val PARCEL_ITEMS = "parcel_items"
    const val CONSOLIDATIONS = "consolidations"
    const val CUSTOMER_CONSOLIDATIONS = "customer_consolidations"
    const val PALLETS = "pallets"
    const val CUSTOMS_ENTRIES = "customs_entries"
    const val PVOC_DOCUMENTS = "pvoc_documents"
    const val INSURANCE_POLICIES = "insurance_policies"
    const val PRICING_TIERS = "pricing_tiers"
    const val FEES = "fees"
    const val FX_RATES = "fx_rates"
    const val PROMOTIONS = "promotions"
    const val LAST_MILE_RUNS = "last_mile_runs"
    const val RUN_STOPS = "run_stops"
    const val POD_EVENTS = "pod_events"
    const val AGENT_INVOICES = "agent_invoices"
    const val TUDOR_INVOICES = "tudor_invoices"
    const val DUTY_INVOICES = "duty_invoices"
    const val PROHIBITED_ITEMS = "prohibited_items"
    const val WHATSAPP_MESSAGES = "whatsapp_messages"
    const val DSAR_REQUESTS = "dsar_requests"
    const val AML_FLAGS = "aml_flags"
    const val NPS_RESPONSES = "nps_responses"

    // Web-side parity tables (added by the hybrid sync work)
    const val WALLET = "wallet"
    const val TRANSACTIONS = "transactions"
    const val ORDERS = "orders"

    // Streaming tables published by migration 007.
    const val NOTIFICATIONS = "notifications"
    const val TICKETS = "tickets"
    const val TICKET_MESSAGES = "ticket_messages"
    const val BUY_FOR_ME_ORDERS = "buy_for_me_orders"

    // Migration 028 — payments + per-user credits.
    const val PAYMENTS = "payments"
    const val USER_CREDITS = "user_credits"
    const val CREDIT_LEDGER = "credit_ledger"
}
