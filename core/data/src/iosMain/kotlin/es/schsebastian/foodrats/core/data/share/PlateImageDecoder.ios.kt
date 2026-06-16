package es.schsebastian.foodrats.core.data.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.request.ImageRequest

/** Coil's iOS bitmap is `org.jetbrains.skia.Bitmap` → Compose [ImageBitmap] via `asComposeImageBitmap()`. */
internal actual fun imageBitmapFromCoil(bitmap: coil3.Bitmap): ImageBitmap = bitmap.asComposeImageBitmap()

/** No-op: Skia has no hardware-bitmap concept; the iOS renderer uses ImageComposeScene. */
internal actual fun ImageRequest.Builder.softwareBitmapForCapture(): ImageRequest.Builder = this
