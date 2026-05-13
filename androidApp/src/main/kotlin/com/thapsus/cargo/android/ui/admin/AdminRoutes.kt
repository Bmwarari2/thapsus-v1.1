package com.thapsus.cargo.android.ui.admin

object AdminRoutes {
    const val CONSOLE = "admin/console"
    const val KPI = "admin/kpi"
    const val CONSOLS = "admin/consols"
    const val SETTINGS = "admin/settings"
    const val ACCOUNT = "admin/account"

    const val USERS = "admin/users"
    const val PAYMENTS = "admin/payments"
    const val CREATE_BFM = "admin/create-bfm"
    const val ISSUE_INVOICE = "admin/issue-invoice"
    const val CONSOL_DETAIL = "admin/consols/{consolId}"
    fun consolDetail(id: String) = "admin/consols/$id"
}
