package com.thapsus.cargo.android.hardware

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.print.PrintHelper

/** Spec for a 4×6" warehouse label, the Compose-side twin of `WarehouseLabel`. */
data class WarehouseLabel(
    val sku: String,
    val customerName: String,
    val customerWarehouseCode: String?,
    val warehouseCode: String,
    val retailer: String?,
    val description: String?,
    val welcomeMessage: String
)

object LabelPrinter {

    /** Renders the label to a bitmap and dispatches the AndroidX print dialog. */
    fun print(context: Context, label: WarehouseLabel) {
        val bitmap = renderBitmap(label)
        val helper = PrintHelper(context).apply {
            scaleMode = PrintHelper.SCALE_MODE_FIT
            colorMode = PrintHelper.COLOR_MODE_MONOCHROME
            orientation = PrintHelper.ORIENTATION_PORTRAIT
        }
        helper.printBitmap("Thapsus Cargo ${label.sku}", bitmap)
    }

    /** 4×6" at 300 dpi → 1200×1800 px. Matches the iOS WarehouseLabel scale. */
    fun renderBitmap(label: WarehouseLabel): Bitmap {
        val w = 1200
        val h = 1800
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        val orange = Color.parseColor("#F5731A")
        val ink = Color.parseColor("#101214")

        val brand = Paint().apply {
            color = ink; isAntiAlias = true; textSize = 60f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val brandOrange = Paint(brand).apply { color = orange }
        val mono = Paint().apply {
            color = ink; isAntiAlias = true; textSize = 48f; typeface = Typeface.MONOSPACE
        }
        val monoOrange = Paint(mono).apply { color = orange; textSize = 90f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val small = Paint().apply { color = ink; isAntiAlias = true; textSize = 36f }
        val muted = Paint(small).apply { color = Color.parseColor("#666666") }
        val welcome = Paint(muted).apply { textSize = 28f }

        var y = 100f
        c.drawText("Thapsus", 60f, y, brand); c.drawText("Cargo", 60f + brand.measureText("Thapsus") + 12f, y, brandOrange)
        y += 60f
        c.drawText("UK → KENYA", 60f, y, small)

        y += 80f
        c.drawText("CUSTOMER", 60f, y, muted); y += 50f
        c.drawText(label.customerName, 60f, y, mono); y += 60f
        label.customerWarehouseCode?.let {
            c.drawText(it, 60f, y, mono.apply { color = orange })
            y += 60f
        }

        y += 30f
        c.drawText("SKU", 60f, y, muted); y += 60f
        c.drawText(label.sku, 60f, y, monoOrange); y += 70f

        // Code 128 barcode strip
        val barcodeBmp = Code128.render(label.sku, w - 120, 220)
        c.drawBitmap(barcodeBmp, 60f, y, null)
        y += 240f

        c.drawText("HUB ${label.warehouseCode}", 60f, y, small); y += 80f
        label.retailer?.let { c.drawText("From: $it", 60f, y, small); y += 50f }
        label.description?.let { c.drawText(truncate(it, 50), 60f, y, small); y += 50f }

        // Welcome message at the bottom
        wrap(label.welcomeMessage, 50).forEachIndexed { idx, line ->
            c.drawText(line, 60f, h - 120f + idx * 36f, welcome)
        }

        return bmp
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1) + "…"

    private fun wrap(s: String, perLine: Int): List<String> {
        if (s.length <= perLine) return listOf(s)
        val out = mutableListOf<String>()
        var rest = s
        while (rest.length > perLine) {
            val cut = rest.lastIndexOf(' ', perLine).coerceAtLeast(0).takeIf { it > 0 } ?: perLine
            out += rest.substring(0, cut)
            rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }
}
