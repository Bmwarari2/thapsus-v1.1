package com.thapsus.cargo.android.ui.operator

object OperatorRoutes {
    // BFM-primary pivot — BFM queue leads the operator workflow.
    const val BFM = "operator/bfm"
    const val RECEIVE = "operator/receive"
    const val CONSOLS = "operator/consols"
    const val DISPATCH = "operator/dispatch"
    const val ACCOUNT = "operator/account"

    // Today is no longer a top-level tab; it lives behind Account → Today's summary.
    const val TODAY = "operator/today"
    const val SCANNER = "operator/scanner"
    const val CONSOL_DETAIL = "operator/consols/{consolId}"
    fun consolDetail(id: String) = "operator/consols/$id"
}
