package com.thapsus.cargo.android.ui.customer

object CustomerRoutes {
    const val HOME = "customer/home"
    const val SHOP = "customer/shop"
    const val ACTIVITY = "customer/activity"
    const val TRACKING = "customer/tracking"
    const val INVOICES = "customer/invoices"
    const val TRANSACTIONS = "customer/transactions"
    const val CREDIT = "customer/credit"
    const val CONSOLIDATIONS = "customer/consolidations"
    const val WAREHOUSE = "customer/warehouse"
    /** Legacy wallet alias — kept for any deep links that still point here; renders CreditCenterScreen. */
    const val WALLET = "customer/wallet"
    const val QUOTE = "customer/quote"
    const val ACCOUNT = "customer/account"

    const val NEW_ORDER = "customer/new-order"
    const val PARCEL_DETAIL = "customer/parcel/{parcelId}"
    fun parcelDetail(parcelId: String) = "customer/parcel/$parcelId"
    const val NOTIFICATIONS = "customer/notifications"
    const val PROFILE_EDIT = "customer/profile-edit"

    /** Pay-invoice flow — title is URL-encoded by callers. */
    const val PAY_INVOICE = "customer/pay/{kind}/{id}/{amount}?title={title}"
    fun payInvoice(kind: String, id: String, amount: Long, title: String): String {
        val encoded = java.net.URLEncoder.encode(title, "UTF-8")
        return "customer/pay/$kind/$id/$amount?title=$encoded"
    }
}
