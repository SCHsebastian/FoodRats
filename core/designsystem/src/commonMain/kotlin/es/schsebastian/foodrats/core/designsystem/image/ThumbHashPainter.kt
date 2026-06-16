package es.schsebastian.foodrats.core.designsystem.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Decodes a base64-encoded ThumbHash string (as written onto the meal doc by the server image
 * pipeline) into a Compose [ImageBitmap], or `null` when the hash is missing/malformed. The bitmap
 * is the tiny (~32px) blurred preview used as the instant `placeholder` behind a Coil `AsyncImage`
 * while the full plate loads.
 *
 * `expect/actual` only for the final `IntArray(ARGB) → ImageBitmap` step, which needs a platform
 * pixel writer (Android `Bitmap.setPixels`, Skia on iOS). The byte-level ThumbHash math itself is
 * common ([ThumbHash]).
 */
@OptIn(ExperimentalEncodingApi::class)
fun decodeThumbHash(base64Hash: String?): ImageBitmap? {
    if (base64Hash.isNullOrBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64Hash)
        val decoded = ThumbHash.decode(bytes) ?: return@runCatching null
        imageBitmapFromRgba(decoded.width, decoded.height, decoded.rgba)
    }.getOrNull()
}

/**
 * Remembers a [Painter] for the ThumbHash placeholder, keyed by the hash string so the decode runs
 * once per distinct hash even across recompositions. Returns `null` when there is no usable hash —
 * the caller then falls back to a flat branded placeholder.
 */
@Composable
fun rememberThumbHashPainter(base64Hash: String?): Painter? {
    val bitmap = remember(base64Hash) { decodeThumbHash(base64Hash) }
    return remember(bitmap) { bitmap?.let { BitmapPainter(it) } }
}

/**
 * Builds a Compose [ImageBitmap] from straight RGBA bytes. `expect/actual` because the pixel
 * upload differs per platform.
 */
internal expect fun imageBitmapFromRgba(width: Int, height: Int, rgba: ByteArray): ImageBitmap
