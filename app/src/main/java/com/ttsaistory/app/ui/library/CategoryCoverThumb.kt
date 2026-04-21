package com.ttsaistory.app.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hiển thị ảnh đại diện thể loại (file JPEG đã resize trên đĩa). */
@Composable
internal fun CategoryCoverThumb(
    coverImagePath: String?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val decodeMaxPx =
        with(density) {
            (size * 2.25f).roundToPx().coerceIn(96, 320)
        }
    var bitmap by remember(coverImagePath, decodeMaxPx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(coverImagePath, decodeMaxPx) {
        bitmap =
            withContext(Dispatchers.IO) {
                val p = coverImagePath?.trim().orEmpty()
                if (p.isEmpty()) return@withContext null
                val f = File(p)
                if (!f.isFile) return@withContext null
                decodeThumbFromFile(f, decodeMaxPx)?.asImageBitmap()
            }
    }
    val bm = bitmap
    if (bm != null) {
        Image(
            bitmap = bm,
            contentDescription = null,
            modifier = modifier.size(size).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun decodeThumbFromFile(file: File, maxSidePx: Int): Bitmap? {
    val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, optsBounds)
    if (optsBounds.outWidth <= 0 || optsBounds.outHeight <= 0) return null
    val sample =
        calculateInSampleSize(optsBounds.outWidth, optsBounds.outHeight, maxSidePx, maxSidePx)
    val opts =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sample.coerceAtLeast(1)
        }
    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
    val w = bmp.width
    val h = bmp.height
    val longest = max(w, h)
    if (longest <= maxSidePx) return bmp
    val scale = maxSidePx.toFloat() / longest
    val nw = (w * scale).toInt().coerceAtLeast(1)
    val nh = (h * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
    if (scaled != bmp && !bmp.isRecycled) bmp.recycle()
    return scaled
}

private fun calculateInSampleSize(outWidth: Int, outHeight: Int, reqW: Int, reqH: Int): Int {
    var inSampleSize = 1
    if (outHeight > reqH || outWidth > reqW) {
        var halfH = outHeight / 2
        var halfW = outWidth / 2
        while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}
