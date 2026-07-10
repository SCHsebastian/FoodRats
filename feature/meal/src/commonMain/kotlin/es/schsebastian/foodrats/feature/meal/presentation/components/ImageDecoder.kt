package es.schsebastian.foodrats.feature.meal.presentation.components

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes an in-memory JPEG/PNG byte array into a Compose [ImageBitmap].
 *
 * Used to render captured meal photos before they're uploaded to Storage — both the
 * Compose screen (preview after capture) and the Publish/Feed cards consume this.
 *
 * [maxDimension] caps the longest side of the decoded bitmap so callers can decode to
 * display size instead of retaining the full upload-resolution bitmap (a 2048px plate is
 * ~16 MB of ARGB). Android honors the cap *pre-decode* via `BitmapFactory.inSampleSize`
 * (power-of-two subsampling, never shrinking below the cap); iOS/Skia has no subsampled
 * decode, so it decodes fully and then scales down (retained-memory win only). The default
 * keeps the original decode-at-full-size behavior for existing callers.
 *
 * This is a synchronous CPU-bound decode — call it off the main thread for anything bigger
 * than a thumbnail. Returns null on any decoding failure (corrupt bytes, unsupported
 * format, OOM).
 */
internal expect fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int = Int.MAX_VALUE): ImageBitmap?
