package es.schsebastian.foodrats.feature.auth.presentation.profile

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

internal actual fun encodeAvatarJpeg(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray? = try {
    val image = Image.makeFromEncoded(bytes)
    val w = image.width
    val h = image.height
    val ratio = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h).coerceAtMost(1f)
    val newW = (w * ratio).toInt().coerceAtLeast(1)
    val newH = (h * ratio).toInt().coerceAtLeast(1)

    val finalImage = if (ratio < 1f) {
        val surface = Surface.makeRasterN32Premul(newW, newH)
        surface.canvas.drawImageRect(
            image,
            Rect.makeWH(w.toFloat(), h.toFloat()),
            Rect.makeWH(newW.toFloat(), newH.toFloat()),
        )
        surface.makeImageSnapshot()
    } else image

    finalImage.encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
} catch (_: Throwable) {
    // Undecodable input must NOT fall back to the original bytes — a raw multi-MB photo
    // would sail past this seam and be rejected by the 1 MB avatars/{accountId} Storage rule.
    null
}
