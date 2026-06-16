package es.schsebastian.foodrats.core.data.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Android [StoryCardRenderer] actual. Rasterizes an `Fr*ShareCard` to a PNG at a fixed export size
 * (spec §5, Android actual).
 *
 * A Compose tree only composes once its host `View` is attached to a window — an unattached
 * `ComposeView` never sets its `ViewTreeOwners` and so never runs `setContent` (the earlier
 * "off-screen measure" approach produced a fully-transparent bitmap for exactly this reason). So the
 * card is hosted in a real window: the [ComposeView] is added **behind** the foreground Activity's
 * content (index 0 of `android.R.id.content`, sized to the export resolution), where the opaque app
 * UI fully occludes it — no visible flash. There it composes; we then measure/lay it out at the
 * export size and draw it into a software [Bitmap]-backed [Canvas] (Compose's layers fall back to
 * software rasterization when the canvas is not hardware-accelerated, so the pixels come out real).
 *
 * The card layout is deterministic (no scroll / animation) and the plate is already decoded, so one
 * draw pass renders the whole tree. View work runs on the main dispatcher; PNG encoding runs on the
 * IO dispatcher — the single, justified `withContext` boundary for a platform adapter.
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
        // View operations must run on the main thread; immediate avoids a needless re-dispatch when
        // the caller is already there.
        val bitmap = withContext(Dispatchers.Main.immediate) {
            captureToBitmap(widthPx, heightPx, content)
        } ?: return ByteArray(0)

        return withContext(dispatchers.io) {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }
    }

    /** Returns the captured bitmap, or null if there is no foreground Activity to host the capture. */
    private suspend fun captureToBitmap(
        widthPx: Int,
        heightPx: Int,
        content: @Composable () -> Unit,
    ): Bitmap? {
        val activity = ForegroundActivityHolder.current() ?: return null
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null

        var composed = false
        val composeView = ComposeView(activity).apply {
            setContent {
                composed = true
                content()
            }
        }

        // index 0 → drawn first, behind the opaque app content that fills the window: invisible.
        root.addView(composeView, 0, FrameLayout.LayoutParams(widthPx, heightPx))
        return try {
            // Attaching to the window composes the tree (synchronously during attach for an attached
            // parent). Guard with a few frame waits in case the deferred composition lands a frame late.
            var attempts = 0
            while (!composed && attempts < MAX_LAYOUT_WAITS) {
                awaitLayout(composeView)
                attempts++
            }

            // Measure + lay out at the exact export size so the composed tree has real bounds, then
            // draw into a software canvas — Compose rasterizes its layers directly when the canvas is
            // not hardware-accelerated (the plate is decoded as a software bitmap for the same reason).
            composeView.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
            )
            composeView.layout(0, 0, widthPx, heightPx)

            Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also { bitmap ->
                composeView.draw(Canvas(bitmap))
            }
        } finally {
            root.removeView(composeView)
        }
    }

    /** Suspends until the view tree completes one layout pass (so composition + measure have run). */
    private suspend fun awaitLayout(view: View): Unit = suspendCancellableCoroutine { cont ->
        val observer = view.viewTreeObserver
        val handler = view.handler ?: Handler(Looper.getMainLooper())
        fun liveObserver() = if (observer.isAlive) observer else view.viewTreeObserver
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                runCatching { liveObserver().removeOnGlobalLayoutListener(this) }
                handler.post { if (cont.isActive) cont.resume(Unit) }
            }
        }
        observer.addOnGlobalLayoutListener(listener)
        cont.invokeOnCancellation {
            handler.post { runCatching { liveObserver().removeOnGlobalLayoutListener(listener) } }
        }
        view.requestLayout()
    }

    private companion object {
        const val MAX_LAYOUT_WAITS = 5
    }
}
