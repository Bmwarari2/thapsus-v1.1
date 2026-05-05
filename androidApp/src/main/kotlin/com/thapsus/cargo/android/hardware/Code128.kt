package com.thapsus.cargo.android.hardware

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Minimal Code 128 (subset B) renderer for on-device label printing.
 * No external library needed — Code 128 B is a fixed lookup table of 107
 * patterns (start B + checksum + stop). Mirrors what the iOS app gets via
 * Core Image's `CICode128BarcodeGenerator`.
 */
object Code128 {

    private val patterns = arrayOf(
        // index → "BSBSBS" widths (B=bar, S=space) for 11 modules total per char.
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312",
        "132212", "221213", "221312", "231212", "112232", "122132", "122231", "113222",
        "123122", "123221", "223211", "221132", "221231", "213212", "223112", "312131",
        "311222", "321122", "321221", "312212", "322112", "322211", "212123", "212321",
        "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121",
        "313121", "211331", "231131", "213113", "213311", "213131", "311123", "311321",
        "331121", "312113", "312311", "332111", "314111", "221411", "431111", "111224",
        "111422", "121124", "121421", "141122", "141221", "112214", "112412", "122114",
        "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112",
        "421211", "212141", "214121", "412121", "111143", "111341", "131141", "114113",
        "114311", "411113", "411311", "113141", "114131", "311141", "411131", "211412",
        "211214", "211232", "2331112"
    )

    private const val START_B = 104
    private const val STOP = 106

    /** Encode an ASCII (Code-128 B) string into a list of bar/space widths. */
    fun encode(text: String): IntArray {
        val codes = mutableListOf<Int>()
        codes += START_B
        for (c in text) {
            val v = c.code
            if (v in 32..127) codes += v - 32 else codes += '?'.code - 32
        }
        // Checksum: start + sum(i * data) mod 103
        var sum = START_B.toLong()
        for ((i, code) in codes.drop(1).withIndex()) {
            sum += (i + 1) * code.toLong()
        }
        codes += (sum % 103).toInt()
        codes += STOP

        // Each pattern encodes 6 widths (bar/space alternating). Stop = 7.
        val widths = mutableListOf<Int>()
        for (code in codes) {
            patterns[code].forEach { widths += it.digitToInt() }
        }
        return widths.toIntArray()
    }

    /** Render the encoded barcode as a black-on-white bitmap. */
    fun render(text: String, widthPx: Int, heightPx: Int): Bitmap {
        val widths = encode(text)
        val totalUnits = widths.sum()
        val scale = (widthPx.toFloat() / totalUnits).coerceAtLeast(1f)
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        var x = 0f
        var bar = true
        for (w in widths) {
            val pixels = w * scale
            if (bar) {
                canvas.drawRect(x, 0f, x + pixels, heightPx.toFloat(), paint)
            }
            x += pixels
            bar = !bar
        }
        return bmp
    }
}
