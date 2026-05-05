package com.thapsus.cargo.android.ui.operator

object OperatorRoutes {
    const val TODAY = "operator/today"
    const val RECEIVE = "operator/receive"
    const val CONSOLS = "operator/consols"
    const val DISPATCH = "operator/dispatch"
    const val ACCOUNT = "operator/account"

    const val SCANNER = "operator/scanner"
    const val CONSOL_DETAIL = "operator/consols/{consolId}"
    fun consolDetail(id: String) = "operator/consols/$id"
}
