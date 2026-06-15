package es.schsebastian.foodrats.core.data.share

import androidx.compose.ui.graphics.ImageBitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap

/**
 * Pre-decodes a plate's short-lived signed URL into an [ImageBitmap] off the main thread, reusing the
 * already-installed singleton Coil 3 [coil3.ImageLoader] so the signed URL + HTTP engine are shared
 * (spec §5, phase 1). The off-screen [StoryCardRenderer] cannot rely on `AsyncImage` resolving inside
 * a one-shot capture, so the share flow decodes here first, then renders with the resolved bitmap.
 *
 * On any failure (network, decode, no image) it returns `null` — the card renders a branded
 * placeholder, never a broken image. Never throws.
 *
 * The Coil decode is a `suspend` call that runs off the main thread inside Coil; no extra
 * `withContext` is needed here (it is not a public repository method — it is a thin adapter helper).
 */
class PlateImageDecoder(
    private val platformContext: PlatformContext,
) {
    /**
     * Decodes [url] (a signed plate URL) to an [ImageBitmap], or `null` on any failure.
     * Pass the result into the card's `plate` slot before [StoryCardRenderer.renderToPng].
     */
    suspend fun decode(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val loader = SingletonImageLoader.get(platformContext)
            val request = ImageRequest.Builder(platformContext)
                .data(url)
                // The off-screen renderer draws this onto a software Canvas; a hardware bitmap can't be
                // drawn there ("Software rendering doesn't support hardware bitmaps"). Android-only.
                .softwareBitmapForCapture()
                .build()
            (loader.execute(request) as? SuccessResult)
                ?.image
                ?.toBitmap()
                ?.let(::imageBitmapFromCoil)
        }.getOrNull()
    }
}

/**
 * Bridges a Coil [coil3.Bitmap] (platform `android.graphics.Bitmap` / Skia `Bitmap`) to a Compose
 * [ImageBitmap]. expect/actual because the conversion differs per platform.
 */
internal expect fun imageBitmapFromCoil(bitmap: coil3.Bitmap): ImageBitmap

/**
 * Forces a software-backed (non-hardware) decode so the result can be drawn onto a software Canvas
 * by the off-screen [StoryCardRenderer]. Android disables hardware bitmaps; other platforms (Skia)
 * have no hardware-bitmap concept, so this is a no-op there.
 */
internal expect fun ImageRequest.Builder.softwareBitmapForCapture(): ImageRequest.Builder
