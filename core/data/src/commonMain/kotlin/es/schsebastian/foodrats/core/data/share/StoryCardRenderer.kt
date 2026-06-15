package es.schsebastian.foodrats.core.data.share

import androidx.compose.runtime.Composable

/**
 * Rasterizes an `Fr*ShareCard` composable to a flat PNG, off-screen, at a fixed export resolution
 * (spec §4.2 / §5). The card itself is ratio-locked (`fillMaxWidth().aspectRatio(...)`), so it fills
 * the measured container exactly; the layout is deterministic (no scroll, no animation), so a
 * one-shot off-screen capture renders the whole tree.
 *
 * The renderer does **no I/O**: the plate must already be decoded into an
 * `androidx.compose.ui.graphics.ImageBitmap` and passed into the card's `plate` slot before
 * [renderToPng] is called (use [PlateImageDecoder] for that). On decode failure the caller passes
 * `plate = null` and the card renders a branded placeholder — never a broken image.
 *
 * This is a platform adapter (it lives in `:core:data`, the adapter layer), so the single
 * `withContext(main)` it needs for the Compose capture step is the one I/O boundary permitted by the
 * dispatcher rule — there is zero `withContext` in any use case or ViewModel that calls it.
 *
 * actuals:
 *  - androidMain — composes into a window-less `ComposeView` measured at the export size, draws into
 *    a `GraphicsLayer`, `layer.toImageBitmap()` → `Bitmap.compress(PNG)`.
 *  - iosMain — Compose Multiplatform's `ImageComposeScene` → Skia `Image.encodeToData(PNG)`.
 */
expect class StoryCardRenderer {
    /**
     * Renders [content] off-screen at [widthPx] × [heightPx] and returns a PNG byte array.
     * Must be called with the plate bitmap already decoded (the renderer does no I/O).
     *
     * No default sizes here — an `expect` actualized via typealias may not carry default argument
     * values. Use the [renderStory] / [renderSquare] extensions for the standard export sizes.
     */
    suspend fun renderToPng(
        widthPx: Int,
        heightPx: Int,
        content: @Composable () -> Unit,
    ): ByteArray
}

/** Renders [content] at the 1080×1920 Story export size (spec §5). */
suspend fun StoryCardRenderer.renderStory(content: @Composable () -> Unit): ByteArray =
    renderToPng(STORY_WIDTH_PX, STORY_HEIGHT_PX, content)

/** Renders [content] at the 1080×1080 square export size (spec §5). */
suspend fun StoryCardRenderer.renderSquare(content: @Composable () -> Unit): ByteArray =
    renderToPng(SQUARE_SIDE_PX, SQUARE_SIDE_PX, content)
