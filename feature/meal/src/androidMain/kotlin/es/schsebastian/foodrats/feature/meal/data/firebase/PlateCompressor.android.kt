package es.schsebastian.foodrats.feature.meal.data.firebase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import java.io.ByteArrayOutputStream

/**
 * Android plate compressor: decode → downscale (longest edge ≤ [PlateCompression.MAX_EDGE_PX] via
 * [PlateCompression.scaledSize]) → re-encode as JPEG at [PlateCompression.JPEG_QUALITY].
 *
 * `inSampleSize` first halves the decode for free (memory + time), then an exact `createScaledBitmap`
 * lands the target size. Best-effort: returns the original bytes when the source can't be decoded,
 * already fits, or the re-encode would be larger — a compression failure must never block a publish.
 */
internal actual class PlateCompressor actual constructor() {

    actual fun compress(source: ByteArray): ByteArray = runCatching {
        if (source.isEmpty()) return source
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return source

        val target = PlateCompression.scaledSize(srcW, srcH)
        if (target.width == srcW && target.height == srcH) {
            // Already within the cap — still re-encode at our quality only if it shrinks; otherwise
            // keep the original to avoid a needless re-compress that could grow a small file.
            return reencodeIfSmaller(source) ?: source
        }

        val sampled = BitmapFactory.Options().apply {
            inSampleSize = inSampleSizeFor(srcW, srcH, target.width, target.height)
        }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, sampled) ?: return source
        val scaled = Bitmap.createScaledBitmap(decoded, target.width, target.height, true)
        if (scaled != decoded) decoded.recycle()

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, PlateCompression.JPEG_QUALITY, out)
        scaled.recycle()
        val bytes = out.toByteArray()
        if (bytes.isNotEmpty() && bytes.size < source.size) bytes else source
    }.getOrElse { t ->
        FrLog.w("PlateCompress", t) { "compress failed, uploading original: ${t.message}" }
        source
    }

    private fun reencodeIfSmaller(source: ByteArray): ByteArray? = runCatching {
        val bmp = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, PlateCompression.JPEG_QUALITY, out)
        bmp.recycle()
        out.toByteArray().takeIf { it.isNotEmpty() && it.size < source.size }
    }.getOrNull()

    /** Largest power-of-two sample size that keeps the decode at least the target dimensions. */
    private fun inSampleSizeFor(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        var sample = 1
        var halfW = srcW / 2
        var halfH = srcH / 2
        while (halfW >= dstW && halfH >= dstH) {
            sample *= 2
            halfW /= 2
            halfH /= 2
        }
        return sample
    }
}
