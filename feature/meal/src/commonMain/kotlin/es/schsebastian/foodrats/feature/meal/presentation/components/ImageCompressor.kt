package es.schsebastian.foodrats.feature.meal.presentation.components

/**
 * Resize + re-encode JPEG so meals upload at a sane size — not the camera's full
 * 12MP/4K capture. Target: max [maxDim] pixels on the longest side, JPEG at
 * [quality] (1..100). Defaults give ~100-200 KB output for typical camera input,
 * which is the right ballpark for a free-tier meal-sharing app: visible quality,
 * fast uploads, low Storage cost.
 *
 * Returns the original bytes unchanged on any decoding failure (rather than
 * failing the publish — the user already accepted the image, we'd rather upload
 * something than nothing).
 */
internal expect fun ByteArray.resizeForUpload(
    maxDim: Int = 1280,
    quality: Int = 75,
): ByteArray
