package es.schsebastian.foodrats.feature.crew.data.firebase

/**
 * Resize + re-encode a picked crew-banner image to a small JPEG before upload.
 *
 * Why this exists: the `crew_banners/{crewId}/banner.jpg` Storage rule caps the object at 2 MB and
 * requires `image/jpeg`. A raw full-resolution gallery photo is routinely several MB (and may be a
 * PNG, since the picker accepts both), so uploading the picked bytes verbatim is rejected with
 * PERMISSION_DENIED — the "crew banner does not get uploaded" bug. Capping the longest edge to
 * [maxDim] and re-encoding at JPEG [quality] brings any photo well under the limit and makes the
 * datasource's declared `image/jpeg` content type honest. Mirrors the avatar/plate compressors
 * ([es.schsebastian.foodrats.feature.auth.presentation.profile] avatar resize / `PlateCompressor`).
 *
 * Best-effort by contract: returns the ORIGINAL bytes on any decode/encode failure — compression
 * must never be the thing that hard-fails an upload. The Storage rule's size check is the backstop.
 *
 * `expect/actual` because the re-encode needs platform image codecs (Android
 * `BitmapFactory`/`Bitmap.compress`, iOS Skia `Image`/`Surface`). Data-layer-private.
 */
internal expect fun ByteArray.resizeBannerForUpload(
    maxDim: Int = 1280,
    quality: Int = 80,
): ByteArray
