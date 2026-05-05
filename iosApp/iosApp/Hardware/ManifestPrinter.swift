// ManifestPrinter.swift
// Phase D — operator-printed consolidation manifest. Reuses the AirPrint
// pipeline from Phase C but renders via UIMarkupTextPrintFormatter (HTML)
// instead of a single image, because manifests paginate naturally over A4
// when parcel counts are large and tabular text wants kerning the
// ImageRenderer route doesn't preserve well.

import UIKit
import ThapsusShared

enum ManifestPrinter {
    @MainActor
    static func print(
        consolidationId: String,
        parcels: [PackageDto],
        totalParcels: Int,
        totalChargeableKg: Double,
        totalDeclaredValueGbp: Double,
        masterAwb: String?,
        operatorName: String,
        onComplete: @escaping (Bool) -> Void
    ) {
        let html = buildHtml(
            consolidationId: consolidationId,
            parcels: parcels,
            totalParcels: totalParcels,
            totalChargeableKg: totalChargeableKg,
            totalDeclaredValueGbp: totalDeclaredValueGbp,
            masterAwb: masterAwb,
            operatorName: operatorName
        )

        let info = UIPrintInfo.printInfo()
        info.outputType = .general
        info.jobName = "Thapsus Manifest \(consolidationId.prefix(8))"
        info.orientation = .portrait

        let formatter = UIMarkupTextPrintFormatter(markupText: html)
        formatter.perPageContentInsets = UIEdgeInsets(top: 36, left: 36, bottom: 36, right: 36)

        let controller = UIPrintInteractionController.shared
        controller.printInfo = info
        controller.printFormatter = formatter
        controller.present(animated: true) { _, ok, _ in
            onComplete(ok)
        }
    }

    private static func buildHtml(
        consolidationId: String,
        parcels: [PackageDto],
        totalParcels: Int,
        totalChargeableKg: Double,
        totalDeclaredValueGbp: Double,
        masterAwb: String?,
        operatorName: String
    ) -> String {
        let dateFmt = DateFormatter()
        dateFmt.dateStyle = .medium
        dateFmt.timeStyle = .short
        let printedAt = dateFmt.string(from: Date())

        var rowsHtml = ""
        for (i, p) in parcels.enumerated() {
            let weight = p.chargeableKg?.doubleValue
                ?? p.actualKg?.doubleValue
                ?? 0
            let value = Double(p.declaredValueGbpPence) / 100
            rowsHtml += """
              <tr>
                <td class="num">\(i + 1)</td>
                <td class="mono">\(escape(p.barcode ?? String(p.id.prefix(8))))</td>
                <td>\(escape(p.description_ ?? "—"))</td>
                <td>\(escape(p.retailer ?? "—"))</td>
                <td class="num">\(String(format: "%.2f kg", weight))</td>
                <td class="num">£\(String(format: "%.2f", value))</td>
              </tr>
              """
        }

        let awbLine = (masterAwb?.isEmpty == false)
            ? "<div class='kv'><span>Master AWB</span><strong>\(escape(masterAwb!))</strong></div>"
            : ""

        return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <style>
          @page { size: A4; margin: 18mm; }
          body { font-family: -apple-system, "SF Pro Text", sans-serif; color: #1a1a1a; }
          h1 { font-size: 20pt; letter-spacing: 2pt; margin: 0 0 4pt 0; }
          h2 { font-size: 11pt; font-weight: 600; color: #6a6a6a; margin: 0 0 18pt 0; letter-spacing: 1pt; }
          .header { border-bottom: 2px solid #1a1a1a; padding-bottom: 12pt; margin-bottom: 16pt; }
          .meta-grid { display: table; width: 100%; margin-bottom: 18pt; font-size: 10pt; }
          .kv { display: table-cell; padding-right: 24pt; }
          .kv span { display: block; color: #888; font-size: 8pt; letter-spacing: 1.5pt; text-transform: uppercase; margin-bottom: 2pt; }
          .kv strong { font-size: 12pt; font-weight: 700; }
          table { width: 100%; border-collapse: collapse; font-size: 10pt; }
          th { text-align: left; background: #f4f4f4; padding: 8pt 6pt; border-bottom: 1px solid #ccc; font-weight: 700; letter-spacing: 0.5pt; }
          td { padding: 6pt; border-bottom: 1px solid #eee; vertical-align: top; }
          td.num { text-align: right; font-variant-numeric: tabular-nums; }
          td.mono { font-family: "SF Mono", Menlo, monospace; font-size: 9pt; }
          .totals { margin-top: 16pt; border-top: 2px solid #1a1a1a; padding-top: 12pt; font-size: 11pt; }
          .totals strong { font-size: 14pt; }
          .footer { margin-top: 32pt; font-size: 9pt; color: #888; border-top: 1px dashed #ccc; padding-top: 12pt; }
        </style>
        </head>
        <body>
          <div class="header">
            <h1>THAPSUS CARGO</h1>
            <h2>CONSOLIDATION MANIFEST</h2>
          </div>

          <div class="meta-grid">
            <div class="kv"><span>Consolidation</span><strong>\(escape(consolidationId.prefix(13)))</strong></div>
            \(awbLine)
            <div class="kv"><span>Printed</span><strong>\(escape(printedAt))</strong></div>
            <div class="kv"><span>Operator</span><strong>\(escape(operatorName))</strong></div>
          </div>

          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>SKU / ID</th>
                <th>Description</th>
                <th>Retailer</th>
                <th class="num">Chargeable</th>
                <th class="num">Declared</th>
              </tr>
            </thead>
            <tbody>
              \(rowsHtml)
            </tbody>
          </table>

          <div class="totals">
            <div class="kv"><span>Total parcels</span><strong>\(totalParcels)</strong></div>
            <div class="kv"><span>Total chargeable</span><strong>\(String(format: "%.2f kg", totalChargeableKg))</strong></div>
            <div class="kv"><span>Total declared</span><strong>£\(String(format: "%.2f", totalDeclaredValueGbp))</strong></div>
          </div>

          <div class="footer">
            Generated \(escape(printedAt)). Submit alongside the master AWB and Tudor invoice. KRA customs review may adjust HS classification per parcel.
          </div>
        </body>
        </html>
        """
    }

    private static func escape(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;")
         .replacingOccurrences(of: "<", with: "&lt;")
         .replacingOccurrences(of: ">", with: "&gt;")
         .replacingOccurrences(of: "\"", with: "&quot;")
    }

    private static func escape(_ s: Substring) -> String { escape(String(s)) }
}
