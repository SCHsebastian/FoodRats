package es.schsebastian.foodrats.core.data.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap

/** Coil's iOS bitmap is `org.jetbrains.skia.Bitmap` → Compose [ImageBitmap] via `asComposeImageBitmap()`. */
internal actual fun imageBitmapFromCoil(bitmap: coil3.Bitmap): ImageBitmap = bitmap.asComposeImageBitmap()
