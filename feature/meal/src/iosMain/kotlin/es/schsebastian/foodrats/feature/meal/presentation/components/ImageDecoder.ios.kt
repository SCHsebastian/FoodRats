package es.schsebastian.foodrats.feature.meal.presentation.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt

internal actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? = try {
    val decoded = Image.makeFromEncoded(bytes)
    val longest = maxOf(decoded.width, decoded.height)
    if (longest <= maxDimension) {
        decoded.toComposeImageBitmap()
    } else {
        // Skia has no pre-decode subsampling (unlike Android's BitmapFactory.inSampleSize), so
        // the full decode is unavoidable — but scaling down still shrinks the RETAINED bitmap
        // that lives for the whole screen session.
        val scale = maxDimension.toFloat() / longest
        val width = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val height = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawImageRect(decoded, Rect.makeWH(width.toFloat(), height.toFloat()))
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
} catch (_: Throwable) {
    null
}
