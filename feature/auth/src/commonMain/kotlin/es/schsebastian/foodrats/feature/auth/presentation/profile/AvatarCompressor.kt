package es.schsebastian.foodrats.feature.auth.presentation.profile

/**
 * Mirrors the `avatars/{accountId}` Storage rule: `request.resource.size < 1 * 1024 * 1024`. An upload at or
 * above this cap is rejected server-side with PERMISSION_DENIED, so the compressor must produce a
 * strictly smaller payload — or refuse to hand anything back.
 */
internal const val AVATAR_UPLOAD_BYTE_CAP: Int = 1 * 1024 * 1024

/**
 * Outcome of preparing picked avatar bytes for upload.
 *
 * There is deliberately no "pass the original through" case: the old contract returned the raw
 * picked bytes on any decode/OOM/encode failure, which let an unreadable or huge photo travel to
 * Storage verbatim and trip the 1 MB avatar cap. Now a failure is a failure the UI can explain.
 */
internal sealed interface AvatarCompression {
    /** Re-encoded JPEG strictly under the byte cap — safe to upload. */
    class Fit(val bytes: ByteArray) : AvatarCompression

    /** The platform codec couldn't decode/encode the picked bytes (corrupt file, OOM, …). */
    data object Unreadable : AvatarCompression

    /** Even the smallest ladder rung couldn't get under the cap — pathological input. */
    data object TooLarge : AvatarCompression
}

/**
 * Dimension × quality ladder, coarsest-savings first: shrink the long edge before degrading JPEG
 * quality, because resolution dominates encoded size at avatar scale (avatars render at <= 64dp,
 * so even the 96px floor stays sharp on-device). First rung strictly under [byteCap] wins.
 */
private val AVATAR_DIMENSION_LADDER = listOf(256, 192, 128, 96)
private val AVATAR_QUALITY_LADDER = listOf(80, 65, 50, 35)

/**
 * Resize + re-encode picked avatar bytes so the upload fits the Storage rule, walking the ladder
 * until a rung fits. Never returns the original bytes: an encode failure is [AvatarCompression.Unreadable],
 * an exhausted ladder is [AvatarCompression.TooLarge].
 *
 * [encode] is injectable for tests; production uses the platform codec via [encodeAvatarJpeg].
 */
internal fun ByteArray.compressAvatarForUpload(
    byteCap: Int = AVATAR_UPLOAD_BYTE_CAP,
    encode: (bytes: ByteArray, maxDimension: Int, quality: Int) -> ByteArray? = ::encodeAvatarJpeg,
): AvatarCompression {
    for (maxDimension in AVATAR_DIMENSION_LADDER) {
        for (quality in AVATAR_QUALITY_LADDER) {
            val candidate = encode(this, maxDimension, quality) ?: return AvatarCompression.Unreadable
            if (candidate.size < byteCap) return AvatarCompression.Fit(candidate)
        }
    }
    return AvatarCompression.TooLarge
}

/**
 * Decode [bytes], cap the longest edge at [maxDimension], re-encode as JPEG at [quality].
 * Returns null on ANY failure (undecodable input, zero bounds, codec error, OOM) — never the
 * original bytes. Platform-specific because it needs the native image codecs.
 */
internal expect fun encodeAvatarJpeg(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray?
