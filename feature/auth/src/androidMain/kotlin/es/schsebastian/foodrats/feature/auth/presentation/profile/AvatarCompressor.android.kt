package es.schsebastian.foodrats.feature.auth.presentation.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

internal actual fun encodeAvatarJpeg(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray? = try {
    // Decode bounds ONLY (no pixel allocation) to size the downsample.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        // Downsample DURING decode so a full-size gallery pick never allocates its full ~48 MB
        // ARGB_8888 bitmap, which OOM-crashes low-heap devices. inSampleSize is a power of two; pick
        // the largest that still leaves the longest edge >= maxDimension, then exact-scale below.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        if (src == null) {
            null
        } else {
            val w = src.width
            val h = src.height
            val ratio = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h).coerceAtMost(1f)
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(src, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
            } else src
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (scaled !== src) scaled.recycle()
            src.recycle()
            out.toByteArray()
        }
    }
} catch (_: Throwable) {
    // Undecodable/OOM input must NOT fall back to the original bytes — a raw multi-MB photo
    // would sail past this seam and be rejected by the 1 MB avatars/{accountId} Storage rule.
    null
}
