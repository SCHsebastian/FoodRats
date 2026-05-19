package es.schsebastian.foodrats.feature.meal.presentation.components

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes an in-memory JPEG/PNG byte array into a Compose [ImageBitmap].
 *
 * Used to render captured meal photos before they're uploaded to Storage — both the
 * Compose screen (preview after capture) and the Publish/Feed cards consume this.
 * Returns null on any decoding failure (corrupt bytes, unsupported format, OOM).
 */
internal expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
