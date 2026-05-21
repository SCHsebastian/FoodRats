package es.schsebastian.foodrats.feature.auth.presentation.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

internal actual fun ByteArray.resizeAvatarForUpload(maxDim: Int, quality: Int): ByteArray = try {
    val src = BitmapFactory.decodeByteArray(this, 0, size) ?: return this
    val w = src.width
    val h = src.height
    val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h).coerceAtMost(1f)
    val scaled = if (ratio < 1f) {
        Bitmap.createScaledBitmap(src, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
    } else src
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    if (scaled !== src) src.recycle()
    out.toByteArray()
} catch (_: Throwable) {
    this
}
