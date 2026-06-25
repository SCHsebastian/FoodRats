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
 *  - [MAX_EDGE_PX] = 2048 — caps the longest edge. Top of the §5.1 "~1440–2048px" band; the plate is
 *    the app's hero surface (full-screen detail header + pinch-zoom to 5×) and the FULL image is the
 *    hard ceiling on every display path's quality, so we keep the most detail the band allows. The
 *    server still derives its own small thumbnail; this only bounds the FULL image.
 *  - [JPEG_QUALITY] = 85 — visually-lossless for photographic plates; the small extra bytes over 80
 *    buy crisper edges on the large detail hero and the structural feed bento tiles.
 */
internal object PlateCompression {
    const val MAX_EDGE_PX: Int = 2048
    const val JPEG_QUALITY: Int = 85

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
