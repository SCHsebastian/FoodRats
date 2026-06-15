package es.schsebastian.foodrats.core.data.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat

/**
 * iOS [StoryCardRenderer] actual (spec §5, iOS actual). Uses Compose Multiplatform's
 * [ImageComposeScene] to render the card to a Skia `Image` without a live `UIView`, then encodes it
 * to PNG bytes via `Image.encodeToData(PNG)` — the same Skia path already used by the avatar/meal
 * compressors on iOS. Pure Kotlin/Native: only the *share presentation* needs Swift (§6.2).
 *
 * The card is composed at density 1f so 1 dp == 1 px and the scene's pixel size is exactly the
 * requested export size. The scene render runs on the main dispatcher (Compose state must be touched
 * there); PNG encoding runs on the default dispatcher — the single, justified adapter boundary.
 */
actual typealias StoryCardRenderer = StoryCardRendererIos

@OptIn(ExperimentalComposeUiApi::class)
class StoryCardRendererIos(
    private val dispatchers: DispatcherProvider,
) {

    suspend fun renderToPng(
        widthPx: Int,
        heightPx: Int,
        content: @Composable () -> Unit,
    ): ByteArray {
        val pngBytes = withContext(Dispatchers.Main) {
            val scene = ImageComposeScene(
                width = widthPx,
                height = heightPx,
                density = Density(1f),
                content = content,
            )
            try {
                scene.render()
                    .encodeToData(EncodedImageFormat.PNG)
                    ?.bytes
            } finally {
                scene.close()
            }
        } ?: ByteArray(0)

        // Encoding already produced the bytes above (Skia encode is cheap + synchronous); the
        // default-dispatcher hop keeps the public method's boundary explicit and off the main thread
        // for any follow-on work. No-op copy keeps the contract clear.
        return withContext(dispatchers.io) { pngBytes }
    }
}
