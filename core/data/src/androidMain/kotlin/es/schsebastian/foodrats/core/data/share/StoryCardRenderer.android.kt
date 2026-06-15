package es.schsebastian.foodrats.core.data.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Android [StoryCardRenderer] actual. Captures an `Fr*ShareCard` off-screen at a fixed pixel size
 * without ever attaching to the window (spec §5, Android actual).
 *
 * Technique: a window-less [ComposeView] is given an in-memory [LifecycleOwner] +
 * [SavedStateRegistryOwner] (so Compose's `AndroidView`/lifecycle machinery is satisfied), measured
 * and laid out at exactly `widthPx × heightPx`, then drawn onto a [Bitmap]-backed [Canvas]. The card
 * layout is deterministic (no scroll / animation) and the plate is already decoded, so one draw pass
 * renders the whole tree. The Compose work runs on the main dispatcher; PNG encoding runs on the
 * default dispatcher — this is the single, justified `withContext` boundary for a platform adapter.
 */
actual typealias StoryCardRenderer = StoryCardRendererAndroid

class StoryCardRendererAndroid(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun renderToPng(
        widthPx: Int,
        heightPx: Int,
        content: @Composable () -> Unit,
    ): ByteArray {
        val bitmap = withContext(Dispatchers.Main) {
            captureToBitmap(widthPx, heightPx, content)
        }
        return withContext(dispatchers.io) {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }
    }

    private suspend fun captureToBitmap(
        widthPx: Int,
        heightPx: Int,
        content: @Composable () -> Unit,
    ): Bitmap = suspendCancellableCoroutine { cont ->
        val lifecycleHost = OffscreenLifecycleOwner()
        lifecycleHost.onCreate()

        val composeView = ComposeView(context).apply {
            // No window to dispose against — tie composition to our in-memory lifecycle instead.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleHost))
            setViewTreeLifecycleOwner(lifecycleHost)
            setViewTreeSavedStateRegistryOwner(lifecycleHost)
            setContent(content)
        }

        // Measure + lay out at the exact export size, off the window. A synchronous failure here
        // happens before the post() lambda runs, so clean up immediately to avoid orphaning the
        // lifecycle host + view.
        try {
            composeView.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
            )
            composeView.layout(0, 0, widthPx, heightPx)
        } catch (t: Throwable) {
            lifecycleHost.onDestroy()
            detach(composeView)
            if (cont.isActive) cont.cancel(t)
            return@suspendCancellableCoroutine
        }

        // Compose composes asynchronously; wait one frame for the first layout/draw to settle,
        // then draw the now-resolved tree into a bitmap-backed canvas.
        composeView.post {
            try {
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                composeView.draw(Canvas(bitmap))
                lifecycleHost.onDestroy()
                detach(composeView)
                if (cont.isActive) cont.resume(bitmap)
            } catch (t: Throwable) {
                lifecycleHost.onDestroy()
                detach(composeView)
                if (cont.isActive) cont.cancel(t)
            }
        }
    }

    private fun detach(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }
}

/** Minimal in-memory lifecycle + saved-state owner so a window-less ComposeView can compose. */
private class OffscreenLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
