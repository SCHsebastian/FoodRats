package es.schsebastian.foodrats.feature.meal.presentation.components

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? = try {
    val options = BitmapFactory.Options()
    if (maxDimension != Int.MAX_VALUE) {
        // Bounds-only pass, then subsample on the real decode — the same inSampleSize
        // precedent as resizeBannerForUpload (a5e0ace): the full-res bitmap is never allocated.
        options.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = inSampleSizeFor(options.outWidth, options.outHeight, maxDimension)
        options.inJustDecodeBounds = false
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
} catch (_: Throwable) {
    null
}

/**
 * Largest power-of-two sample size whose result still has its longest side >= [maxDimension]
 * (the standard BitmapFactory pattern: subsample aggressively but never below the requested size).
 */
private fun inSampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
    val longest = maxOf(width, height)
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= maxDimension) sampleSize *= 2
    return sampleSize
}
