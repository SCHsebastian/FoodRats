package es.schsebastian.foodrats.core.designsystem.image

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin port of the reference [ThumbHash](https://evanw.github.io/thumbhash/) decoder
 * (the `thumbHashToRGBA` / `thumbHashToApproximateAspectRatio` functions). ThumbHash encodes a
 * tiny (~21–25 byte) blurred preview of an image; decoding it yields a small RGBA bitmap we use
 * as the instant placeholder behind a Coil `AsyncImage` while the real plate loads.
 *
 * No platform APIs and no Compose types here — this is portable math, so it runs unchanged on
 * Android and iOS and is unit-testable on the JVM without a graphics stack. The thin
 * Compose-`ImageBitmap` wrapper lives in `ThumbHashPainter.kt`.
 *
 * The byte layout (little-endian header + packed DC/AC coefficients) and the cosine-basis
 * reconstruction below mirror the reference implementation field-for-field; see the project page.
 */
object ThumbHash {

    /** The decoded preview: a [width] x [height] image as straight (non-premultiplied) RGBA bytes. */
    data class DecodedImage(
        val width: Int,
        val height: Int,
        /** Row-major RGBA, 4 bytes per pixel, length == width * height * 4. */
        val rgba: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is DecodedImage && width == other.width && height == other.height &&
                rgba.contentEquals(other.rgba)

        override fun hashCode(): Int =
            (width * 31 + height) * 31 + rgba.contentHashCode()
    }

    /**
     * Decodes [hash] (the raw ThumbHash bytes) into a small RGBA [DecodedImage].
     *
     * Returns `null` when [hash] is too short to carry a valid header (defensive: a corrupt or
     * truncated value must never crash the feed — the caller falls back to a flat placeholder).
     */
    fun decode(hash: ByteArray): DecodedImage? {
        // Minimum: 3-byte L/P/Q + a-channel header + 2 luminance-size bytes = 5 bytes.
        if (hash.size < 5) return null
        return runCatching { decodeUnsafe(hash) }.getOrNull()
    }

    /**
     * The approximate width/height ratio (w/h) recoverable from the header alone, so a placeholder
     * can be laid out at the right aspect ratio before any pixels are reconstructed. Returns `null`
     * for a malformed/too-short hash.
     */
    fun approximateAspectRatio(hash: ByteArray): Float? {
        if (hash.size < 5) return null
        return runCatching {
            // Mirrors the reference `thumbHashToApproximateAspectRatio`: header = byte 3,
            // hasAlpha = top bit of byte 2, isLandscape = top bit of byte 4.
            val header = hash[3].toInt() and 255
            val hasAlpha = (hash[2].toInt() and 0x80) != 0
            val isLandscape = (hash[4].toInt() and 0x80) != 0
            val lx = if (isLandscape) (if (hasAlpha) 5 else 7) else (header and 7)
            val ly = if (isLandscape) (header and 7) else (if (hasAlpha) 5 else 7)
            lx.toFloat() / ly.toFloat()
        }.getOrNull()
    }

    private fun decodeUnsafe(hash: ByteArray): DecodedImage {
        // ---- Header (little-endian) ----
        val header24 = (hash[0].toInt() and 255) or
            ((hash[1].toInt() and 255) shl 8) or
            ((hash[2].toInt() and 255) shl 16)
        val header16 = (hash[3].toInt() and 255) or ((hash[4].toInt() and 255) shl 8)
        val lDc = (header24 and 63) / 63.0f
        val pDc = ((header24 ushr 6) and 63) / 31.5f - 1.0f
        val qDc = ((header24 ushr 12) and 63) / 31.5f - 1.0f
        val lScale = ((header24 ushr 18) and 31) / 31.0f
        val hasAlpha = (header24 ushr 23) and 1 != 0
        val pScale = ((header16 ushr 3) and 63) / 63.0f
        val qScale = ((header16 ushr 9) and 63) / 63.0f
        val isLandscape = (header16 ushr 15) != 0
        val lx = max(3, if (isLandscape) (if (hasAlpha) 5 else 7) else (header16 and 7))
        val ly = max(3, if (isLandscape) (header16 and 7) else (if (hasAlpha) 5 else 7))

        // ---- Read AC coefficients via a 1.5-nibble-per-coefficient stream ----
        var ac = AcReader(hash, start = if (hasAlpha) 6 else 5)

        // L uses l_scale only; P/Q boost saturation by 1.25x (matching the reference decoder).
        val lChannel = Channel(lDc, lScale, lx, ly).also { it.decode(ac) }
        ac = lChannel.next
        val pChannel = Channel(pDc, pScale * 1.25f, 3, 3).also { it.decode(ac) }
        ac = pChannel.next
        val qChannel = Channel(qDc, qScale * 1.25f, 3, 3).also { it.decode(ac) }
        ac = qChannel.next

        var aDc = 1.0f
        var aScale = 1.0f
        var aChannel: Channel? = null
        if (hasAlpha) {
            aDc = (hash[5].toInt() and 15) / 15.0f
            aScale = ((hash[5].toInt() and 255) ushr 4) / 15.0f
            aChannel = Channel(aDc, aScale, 5, 5).also { it.decode(ac) }
        }

        // ---- Reconstruct pixels ----
        // Ratio from the UNCLAMPED header values (the reference does this), so the output box has
        // the same aspect as the source even when lx/ly were clamped up to 3 for the DCT bases.
        val ratio = approximateAspectRatio(hash) ?: (lx.toFloat() / ly.toFloat())
        val w = (if (ratio > 1f) 32 else (32 * ratio).roundToInt()).coerceAtLeast(1)
        val h = (if (ratio > 1f) (32 / ratio).roundToInt() else 32).coerceAtLeast(1)
        val rgba = ByteArray(w * h * 4)

        val fx = FloatArray(7)
        val fy = FloatArray(7)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val l = lChannel.sample(x, y, w, h, fx, fy)
                val p = pChannel.sample(x, y, w, h, fx, fy)
                val q = qChannel.sample(x, y, w, h, fx, fy)
                val a = aChannel?.sample(x, y, w, h, fx, fy) ?: 1.0f
                // YPbPr-ish → RGB (reference coefficients).
                val b = l - 2.0f / 3.0f * p
                val r = (3.0f * l - b + q) / 2.0f
                val g = r - q
                rgba[i] = toByte(r)
                rgba[i + 1] = toByte(g)
                rgba[i + 2] = toByte(b)
                rgba[i + 3] = toByte(a)
                i += 4
            }
        }
        return DecodedImage(w, h, rgba)
    }

    private fun toByte(v: Float): Byte = (255.0f * max(0.0f, min(1.0f, v))).roundToInt().toByte()

    /**
     * Reads packed 4-bit AC coefficients from the hash, 1.5 bytes covering 3 nibbles. Each
     * coefficient is a nibble in [0,15] mapped to [-1, 1].
     */
    private class AcReader(val data: ByteArray, val start: Int) {
        var index = start
        var nibblePending = false
        var pendingHigh = 0

        fun read(): Float {
            val value = if (nibblePending) {
                nibblePending = false
                pendingHigh
            } else {
                val byte = data[index++].toInt() and 255
                nibblePending = true
                pendingHigh = byte ushr 4
                byte and 15
            }
            return (value / 7.5f) - 1.0f
        }
    }

    /**
     * One reconstructed channel (luminance, chroma-P, chroma-Q, or alpha). Holds its DC term plus
     * the [nx] x [ny] block of AC coefficients, and reconstructs a pixel via the cosine basis.
     */
    private class Channel(
        val dc: Float,
        val scale: Float,
        val nx: Int,
        val ny: Int,
    ) {
        // ac[cy * nx + cx], cx+cy in (0, ...). dc is stored separately.
        val ac = FloatArray(nx * ny)
        lateinit var next: AcReader

        fun decode(reader: AcReader) {
            var r = reader
            for (cy in 0 until ny) {
                val cxStart = if (cy == 0) 1 else 0
                var cx = cxStart
                while (cx * ny < nx * (ny - cy)) {
                    ac[cy * nx + cx] = r.read() * scale
                    cx++
                }
            }
            next = r
        }

        fun sample(x: Int, y: Int, w: Int, h: Int, fx: FloatArray, fy: FloatArray): Float {
            // Precompute cosine bases for this pixel.
            for (cx in 0 until nx) {
                fx[cx] = cos(PI.toFloat() / w * (x + 0.5f) * cx)
            }
            for (cy in 0 until ny) {
                fy[cy] = cos(PI.toFloat() / h * (y + 0.5f) * cy)
            }
            var value = dc
            for (cy in 0 until ny) {
                val cxStart = if (cy == 0) 1 else 0
                var cx = cxStart
                while (cx * ny < nx * (ny - cy)) {
                    value += ac[cy * nx + cx] * fx[cx] * fy[cy] * 2.0f
                    cx++
                }
            }
            return value
        }
    }
}
