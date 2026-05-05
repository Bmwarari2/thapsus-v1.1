package com.thapsus.cargo.android.ui.customer

object CustomerRoutes {
    const val HOME = "customer/home"
    const val TRACKING = "customer/tracking"
    const val WALLET = "customer/wallet"
    const val QUOTE = "customer/quote"
    const val ACCOUNT = "customer/account"

    const val NEW_ORDER = "customer/new-order"
    const val PARCEL_DETAIL = "customer/parcel/{parcelId}"
    fun parcelDetail(parcelId: String) = "customer/parcel/$parcelId"
    const val NOTIFICATIONS = "customer/notifications"
    const val PROFILE_EDIT = "customer/profile-edit"
}
