package es.schsebastian.foodrats.feature.meal.data.firebase

/**
 * Downscales + re-encodes a captured plate JPEG before upload, to cut bytes-on-the-wire and
 * storage cost (roadmap §5.1: cap the longest edge, re-encode at ~80% JPEG quality).
 *
 * `expect/actual` because the actual re-encode needs platform image codecs (Android
 * `BitmapFactory`/`Bitmap.compress`, iOS `UIImage`/`UIImageJPEGRepresentation`). The pure scaling
 * math is shared in [PlateCompression] so it can be unit-tested without a graphics stack; the
 * actuals delegate to it for the target dimensions.
 *
 * Best-effort by contract: an actual returns the ORIGINAL bytes on any decode/encode failure — a
 * compression failure must never block a publish. Data-layer-private; never leaves `data/firebase/`.
 */
internal expect class PlateCompressor() {
    /**
     * Returns a smaller JPEG for [source] (longest edge capped to [PlateCompression.MAX_EDGE_PX],
     * quality [PlateCompression.JPEG_QUALITY]). Already-small images and any failure return the
     * input bytes unchanged.
     */
    fun compress(source: ByteArray): ByteArray
}

/**
 * Pure, platform-independent compression policy + scaling math. The defaults follow roadmap §5.1
 * (the spec is silent on exact numbers, so these are documented choices, not hidden magic):
 *
 *  - [MAX_EDGE_PX] = 1600 — caps the longest edge. Mid-range of the §5.1 "~1440–2048px" band;
 *    ample for a full-screen plate hero on any phone while roughly halving a 12-MP capture's
 *    linear size. The server still derives its own small thumbnail; this only bounds the FULL image.
 *  - [JPEG_QUALITY] = 80 — the §5.1 target; near-visually-lossless for photos at a large byte saving.
 */
internal object PlateCompression {
    const val MAX_EDGE_PX: Int = 1600
    const val JPEG_QUALITY: Int = 80

    /**
     * Computes the target [width]/[height] for a source of [srcWidth] x [srcHeight], scaled so the
     * longest edge is at most [maxEdge], preserving aspect ratio and never UPSCALING. Returns the
     * source size unchanged when it already fits (or when an input is non-positive — defensive).
     */
    fun scaledSize(
        srcWidth: Int,
        srcHeight: Int,
        maxEdge: Int = MAX_EDGE_PX,
    ): Size {
        if (srcWidth <= 0 || srcHeight <= 0 || maxEdge <= 0) return Size(srcWidth, srcHeight)
        val longest = maxOf(srcWidth, srcHeight)
        if (longest <= maxEdge) return Size(srcWidth, srcHeight)
        val scale = maxEdge.toDouble() / longest.toDouble()
        val w = (srcWidth * scale).toInt().coerceAtLeast(1)
        val h = (srcHeight * scale).toInt().coerceAtLeast(1)
        return Size(w, h)
    }

    data class Size(val width: Int, val height: Int)
}
