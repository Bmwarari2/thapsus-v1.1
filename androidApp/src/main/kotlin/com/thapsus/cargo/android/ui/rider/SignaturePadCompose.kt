package com.thapsus.cargo.android.ui.rider

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thapsus.cargo.android.ui.theme.Brand
import java.io.ByteArrayOutputStream

/**
 * Touch-driven signature pad. Strokes are stored as in-memory `List<Offset>`
 * series; the Compose Canvas paints them in real time and `toPngBytes(width,
 * height)` re-rasterises into an `android.graphics.Canvas` so the resulting
 * PNG matches what the rider drew at native resolution (not display
 * resolution, which would clip on Android density variance).
 *
 * Mirrors iOS SignaturePadView. Returned bytes are PNG-encoded RGBA.
 */
@Composable
fun SignaturePadCompose(
    onConfirm: (pngBytes: ByteArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Sign below",
            color = Brand.ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Brand.cream.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .border(1.dp, Brand.ink.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                strokes.add(mutableListOf(offset))
                            },
                            onDrag = { change, _ ->
                                strokes.lastOrNull()?.add(change.position)
                                change.consume()
                            }
                        )
                    }
            ) {
                strokes.forEach { stroke ->
                    if (stroke.size < 2) return@forEach
                    val path = Path().apply {
                        moveTo(stroke[0].x, stroke[0].y)
                        for (i in 1 until stroke.size) {
                            lineTo(stroke[i].x, stroke[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = androidx.compose.ui.graphics.Color.Black,
                        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = { strokes.clear() }) { Text("Clear") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = {
                    if (strokes.isEmpty()) return@Button
                    val bytes = strokesToPngBytes(strokes, width = 800, height = 400)
                    onConfirm(bytes)
                },
                enabled = strokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Brand.Orange,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    disabledContainerColor = Brand.Orange.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save signature", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Convert the in-memory stroke offsets (display coordinates) into a PNG at a
 * fixed output resolution. The strokes were captured against the Canvas's
 * own coordinate space, so we measure the bounding box and rescale into
 * the requested width/height keeping aspect.
 */
private fun strokesToPngBytes(
    strokes: List<List<Offset>>,
    width: Int,
    height: Int
): ByteArray {
    val flat = strokes.flatten()
    if (flat.isEmpty()) return ByteArray(0)

    val minX = flat.minOf { it.x }
    val maxX = flat.maxOf { it.x }
    val minY = flat.minOf { it.y }
    val maxY = flat.maxOf { it.y }
    val srcW = (maxX - minX).coerceAtLeast(1f)
    val srcH = (maxY - minY).coerceAtLeast(1f)
    val scale = minOf(width / srcW, height / srcH) * 0.92f
    val offsetX = (width - srcW * scale) / 2f - minX * scale
    val offsetY = (height - srcH * scale) / 2f - minY * scale

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        if (stroke.size < 2) return@forEach
        val path = android.graphics.Path()
        path.moveTo(stroke[0].x * scale + offsetX, stroke[0].y * scale + offsetY)
        for (i in 1 until stroke.size) {
            path.lineTo(stroke[i].x * scale + offsetX, stroke[i].y * scale + offsetY)
        }
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}
