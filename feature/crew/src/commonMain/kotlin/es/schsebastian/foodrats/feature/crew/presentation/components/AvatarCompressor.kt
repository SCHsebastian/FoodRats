package es.schsebastian.foodrats.feature.crew.presentation.components

/**
 * Resize + re-encode a JPEG so an avatar uploads small (~10–30 KB). Avatars are rendered
 * at <=64dp; 256px on the long side is plenty for high-DPI without paying meal-sized
 * Storage cost. Returns the original bytes on any decoding failure.
 */
internal expect fun ByteArray.resizeAvatarForUpload(
    maxDim: Int = 256,
    quality: Int = 80,
): ByteArray
