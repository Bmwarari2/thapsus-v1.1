package com.thapsus.cargo.android.hardware

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.thapsus.cargo.data.dto.PackageDto

/**
 * A4 manifest print pipeline — Android twin of iOS `ManifestPrinter`. Builds
 * an HTML document with the consolidation header + parcel table + totals,
 * loads it into a hidden `WebView`, then hands the resulting print adapter
 * to the system `PrintManager`.
 */
object ManifestPrinter {
    fun print(
        context: Context,
        consolidationId: String,
        parcels: List<PackageDto>,
        totalParcels: Int,
        totalChargeableKg: Double,
        totalDeclaredValueGbp: Double,
        masterAwb: String?,
        operatorName: String
    ) {
        val html = buildHtml(
            consolidationId = consolidationId,
            parcels = parcels,
            totalParcels = totalParcels,
            totalChargeableKg = totalChargeableKg,
            totalDeclaredValueGbp = totalDeclaredValueGbp,
            masterAwb = masterAwb,
            operatorName = operatorName
        )
        val webView = WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val jobName = "Thapsus Manifest ${consolidationId.take(8)}"
                    val adapter = view.createPrintDocumentAdapter(jobName)
                    printManager.print(
                        jobName,
                        adapter,
                        PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .build()
                    )
                }
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun buildHtml(
        consolidationId: String,
        parcels: List<PackageDto>,
        totalParcels: Int,
        totalChargeableKg: Double,
        totalDeclaredValueGbp: Double,
        masterAwb: String?,
        operatorName: String
    ): String {
        val rows = parcels.mapIndexed { idx, p ->
            val sku = p.barcode ?: p.id.take(8)
            val desc = (p.description ?: p.retailer ?: "Parcel").take(60)
            val retailer = p.retailer.orEmpty()
            val kg = "%.2f".format(p.chargeableKg ?: p.actualKg ?: 0.0)
            val declared = "%.2f".format(p.declaredValueGbpPence / 100.0)
            "<tr><td>${idx + 1}</td><td>$sku</td><td>$desc</td><td>$retailer</td><td>$kg kg</td><td>£$declared</td></tr>"
        }.joinToString("\n")

        return """
        <html>
        <head>
        <style>
        body { font-family: -apple-system, sans-serif; padding: 24px; color: #101214; }
        h1 { font-size: 22px; margin-bottom: 4px; }
        h2 { font-size: 14px; margin: 12px 0 4px; }
        .meta { font-size: 12px; color: #555; margin-bottom: 24px; }
        table { width: 100%; border-collapse: collapse; font-size: 11px; }
        th, td { border-bottom: 1px solid #ddd; padding: 6px 4px; text-align: left; }
        th { background: #FCEAE6; }
        .total { font-weight: 700; font-size: 14px; margin-top: 16px; }
        .footer { margin-top: 32px; font-size: 11px; color: #555; }
        .accent { color: #F5731A; font-weight: 700; }
        </style>
        </head>
        <body>
        <h1>Thapsus Cargo — Consolidation manifest</h1>
        <div class="meta">
            Consolidation <strong>${consolidationId.take(8)}</strong>
            ${if (masterAwb != null) " · Master AWB <span class='accent'>$masterAwb</span>" else ""}
            <br/>Operator: $operatorName
        </div>
        <table>
            <thead>
                <tr><th>#</th><th>SKU</th><th>Description</th><th>Retailer</th><th>Chargeable</th><th>Declared £</th></tr>
            </thead>
            <tbody>
                $rows
            </tbody>
        </table>
        <div class="total">Total: $totalParcels parcels · ${"%.2f".format(totalChargeableKg)} kg · £${"%.2f".format(totalDeclaredValueGbp)} declared</div>
        <div class="footer">KRA customs reminder: declared values must match commercial invoices. File the master AWB + this manifest with Tudor on handover.</div>
        </body>
        </html>
        """.trimIndent()
    }
}
