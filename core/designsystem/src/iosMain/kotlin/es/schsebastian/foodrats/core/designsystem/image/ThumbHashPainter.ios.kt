package es.schsebastian.foodrats.core.designsystem.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * iOS/Skia: install the straight RGBA bytes into a Skia [Bitmap] (RGBA_8888, unpremultiplied) and
 * bridge to a Compose [ImageBitmap]. ThumbHash already emits straight (non-premultiplied) alpha.
 */
internal actual fun imageBitmapFromRgba(width: Int, height: Int, rgba: ByteArray): ImageBitmap {
    val info = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.RGBA_8888,
        alphaType = ColorAlphaType.UNPREMUL,
    )
    val bitmap = Bitmap()
    bitmap.allocPixels(info)
    bitmap.installPixels(rgba)
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}
