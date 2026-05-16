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
    const val CUSTOMER_CONSOLS = "admin/customer-consols"
    const val ORDERS = "admin/orders"
    const val ORDER_DETAIL = "admin/orders/{orderId}"
    fun orderDetail(id: String) = "admin/orders/$id"
    const val REVENUE = "admin/revenue"
    const val AUDIT_LOGS = "admin/audit-logs"
    const val ERROR_LOGS = "admin/error-logs"
    const val DSAR_QUEUE = "admin/dsar-queue"
    const val AML_QUEUE = "admin/aml-queue"
    const val CONSOL_DETAIL = "admin/consols/{consolId}"
    fun consolDetail(id: String) = "admin/consols/$id"
}
