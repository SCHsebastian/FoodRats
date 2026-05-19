package es.schsebastian.foodrats.feature.meal.presentation.components

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Throwable) {
    null
}
