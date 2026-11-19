package pl.adrian.ptaki.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.adrian.ptaki.Hub

/** Przewijający się na żywo spektrogram (ostatnie 12 s, 0–12 kHz) z podpisami rozpoznanych ptaków. */
@Composable
fun SpectrogramView(modifier: Modifier = Modifier) {
    val buf = Hub.spectrogram
    val bmp = remember { Bitmap.createBitmap(buf.cols, buf.rows, Bitmap.Config.ARGB_8888) }
    val image = remember { bmp.asImageBitmap() }
    val pixels = remember { IntArray(buf.cols * buf.rows) }
    var shown by remember { mutableLongStateOf(-1L) }
    val markers by Hub.markers.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            if (buf.total != shown) {
                val t = buf.render(pixels)
                bmp.setPixels(pixels, 0, buf.cols, 0, 0, buf.cols, buf.rows)
                shown = t
            }
        }
    }

    val axisPaint = remember {
        Paint().apply { isAntiAlias = true; color = C.text2.copy(alpha = 0.75f).toArgb() }
    }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = C.text.toArgb()
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setShadowLayer(6f, 0f, 0f, C.bg.toArgb())
        }
    }

    Canvas(modifier) {
        val total = shown   // odczyt stanu => przerysowanie przy każdej nowej kolumnie
        drawImage(
            image = image,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low,
        )

        // siatka kHz
        axisPaint.textSize = 10.sp.toPx()
        for (k in 2..10 step 2) {
            val y = size.height * (1f - k * 1000f / Hub.MAX_HZ)
            drawLine(C.text2.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText("$k kHz", 6f, y - 4f, axisPaint)
        }

        // podpisy ptaków w miejscu, gdzie zaśpiewały
        labelPaint.textSize = 12.sp.toPx()
        var row = 0
        for (m in markers) {
            val age = total - m.col
            if (age < 0 || age >= buf.cols) continue
            val x = size.width * (1f - age.toFloat() / buf.cols)
            drawLine(C.accent.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            val y = 16.sp.toPx() + (row % 3) * 15.sp.toPx()
            val w = labelPaint.measureText(m.label)
            val tx = if (x + 6f + w > size.width) x - 6f - w else x + 6f
            drawContext.canvas.nativeCanvas.drawText(m.label, tx, y, labelPaint)
            row++
        }
    }
}
