package es.schsebastian.foodrats.feature.crew.data.firebase

/**
 * Byte cap for a crew-banner upload — mirror of the `crew_banners/{crewId}/{filename}` rule in
 * `storage.rules` (`request.resource.size < 2 * 1024 * 1024`). Kept here so the client rejects an
 * un-shrinkable image with a typed failure instead of shipping bytes the rule will deny.
 */
internal const val BANNER_UPLOAD_BYTE_CAP: Int = 2 * 1024 * 1024

/** Longest-edge ladder tried in order (outer loop) when shrinking a banner under the byte cap. */
private val BANNER_DIMENSION_LADDER = intArrayOf(1280, 1024, 800, 640, 480, 320)

/** JPEG quality ladder tried in order (inner loop) at each dimension rung. */
private val BANNER_QUALITY_LADDER = intArrayOf(80, 65, 50, 35)

/**
 * Outcome of shrinking a picked crew-banner image for upload. Data-layer-private: the datasource
 * translates the two failure legs into vendor-adapter-boundary exceptions the repository maps to
 * typed [es.schsebastian.foodrats.feature.crew.domain.error.CrewError.Banner] leaves.
 */
internal sealed interface BannerCompression {
    /** A re-encoded JPEG strictly under the byte cap — the only bytes allowed to reach Storage. */
    data class Fit(val bytes: ByteArray) : BannerCompression {
        override fun equals(other: Any?): Boolean = other is Fit && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
        override fun toString(): String = "Fit(bytes=${bytes.size}B)"
    }

    /** The picked bytes could not be decoded/re-encoded as an image at all. */
    data object Unreadable : BannerCompression

    /** Every dimension × quality rung was tried and none landed under the cap. */
    data object TooLarge : BannerCompression
}

/**
 * Shrinks a picked banner image until it fits under [byteCap]: walks [BANNER_DIMENSION_LADDER] ×
 * [BANNER_QUALITY_LADDER] (dimension outer, quality inner) and returns the FIRST encode strictly
 * under the cap as [BannerCompression.Fit].
 *
 * Contract: never returns the original bytes — an image that cannot be decoded or shrunk under the
 * cap is a typed failure ([BannerCompression.Unreadable] / [BannerCompression.TooLarge]), not a
 * silent oversized upload (that was the bug: the old best-effort compressor fell back to the
 * originals, which sailed through to a storage-rules deny that read as a generic failure).
 *
 * [encode] is injectable for tests; production uses the platform codec [encodeBannerJpeg]. A null
 * encode result at any rung means the image is undecodable — fail fast as Unreadable rather than
 * grinding through rungs that cannot succeed.
 */
internal fun ByteArray.compressBannerForUpload(
    byteCap: Int = BANNER_UPLOAD_BYTE_CAP,
    encode: (bytes: ByteArray, maxDimension: Int, quality: Int) -> ByteArray? = ::encodeBannerJpeg,
): BannerCompression {
    for (maxDimension in BANNER_DIMENSION_LADDER) {
        for (quality in BANNER_QUALITY_LADDER) {
            val candidate = encode(this, maxDimension, quality) ?: return BannerCompression.Unreadable
            if (candidate.size < byteCap) return BannerCompression.Fit(candidate)
        }
    }
    return BannerCompression.TooLarge
}

/**
 * Decodes [bytes], caps the longest edge to [maxDimension], and re-encodes as JPEG at [quality].
 * Returns null on ANY decode/encode failure or zero bounds — never the original bytes.
 *
 * `expect/actual` because the re-encode needs platform image codecs (Android
 * `BitmapFactory`/`Bitmap.compress`, iOS Skia `Image`/`Surface`). Data-layer-private.
 */
internal expect fun encodeBannerJpeg(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray?
