package es.schsebastian.foodrats.core.data.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.request.ImageRequest
import coil3.request.allowHardware

/** Coil's Android bitmap is `android.graphics.Bitmap` → Compose [ImageBitmap] via `asImageBitmap()`. */
internal actual fun imageBitmapFromCoil(bitmap: coil3.Bitmap): ImageBitmap = bitmap.asImageBitmap()

/** Disables Coil's hardware-bitmap decode so the result is drawable on a software Canvas. */
internal actual fun ImageRequest.Builder.softwareBitmapForCapture(): ImageRequest.Builder =
    allowHardware(false)
