package es.schsebastian.foodrats.core.designsystem.image

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android: pack straight RGBA bytes into an ARGB_8888 [Bitmap] (`setPixels` expects ARGB ints),
 * then bridge to a Compose [ImageBitmap].
 */
internal actual fun imageBitmapFromRgba(width: Int, height: Int, rgba: ByteArray): ImageBitmap {
    val pixels = IntArray(width * height)
    var i = 0
    for (p in pixels.indices) {
        val r = rgba[i].toInt() and 255
        val g = rgba[i + 1].toInt() and 255
        val b = rgba[i + 2].toInt() and 255
        val a = rgba[i + 3].toInt() and 255
        pixels[p] = (a shl 24) or (r shl 16) or (g shl 8) or b
        i += 4
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}
