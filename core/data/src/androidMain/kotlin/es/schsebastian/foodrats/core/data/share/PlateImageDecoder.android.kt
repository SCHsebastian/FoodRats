package es.schsebastian.foodrats.core.data.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Coil's Android bitmap is `android.graphics.Bitmap` → Compose [ImageBitmap] via `asImageBitmap()`. */
internal actual fun imageBitmapFromCoil(bitmap: coil3.Bitmap): ImageBitmap = bitmap.asImageBitmap()
