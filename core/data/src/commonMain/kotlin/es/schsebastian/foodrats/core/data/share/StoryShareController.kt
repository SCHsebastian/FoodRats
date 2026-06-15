package es.schsebastian.foodrats.core.data.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat

/**
 * The single testable seam a ViewModel depends on to share a story card (spec §8.2). It composes the
 * three platform adapters — [PlateImageDecoder] (pre-decode the signed plate URL off-screen),
 * [StoryCardRenderer] (rasterize the `Fr*ShareCard` to a PNG), [StoryShareLauncher] (hand the PNG to
 * Instagram Stories / the system sheet) — into one suspend call.
 *
 * Why an interface rather than injecting the three [StoryCardRenderer]/[StoryShareLauncher]/
 * [PlateImageDecoder] directly: those are `expect class` (final typealiases) / concrete classes, so a
 * ViewModel that depends on them cannot be unit-tested with a fake. This interface lives in the
 * adapter layer (`:core:data`) — vendor-free, Compose-only — and lets feed/stats inject a
 * [RecordingStoryShareController] test double in `commonTest`. The consent gate / analytics live in
 * the ViewModel, never here.
 */
interface StoryShareController {
    /**
     * Decodes [plateUrl] (null → no photo, e.g. the streak card), renders [card] off-screen at the
     * fixed export size for [format], and hands the PNG to Instagram Stories with a system-sheet
     * fallback. [card] receives the decoded plate (`null` on decode failure → the card paints a
     * branded placeholder). Never throws — always returns a [StoryShareOutcome].
     */
    suspend fun share(
        plateUrl: String?,
        format: ShareCardFormat,
        card: @Composable (plate: ImageBitmap?) -> Unit,
    ): StoryShareOutcome
}

/**
 * Default [StoryShareController]: decode → render → launch, wired to the platform adapters.
 * Bound per platform in Koin (Android `FoodRatsApplication.androidShareModule()`, iOS
 * `storyShareIosModule`).
 */
class StoryShareControllerImpl(
    private val decoder: PlateImageDecoder,
    private val renderer: StoryCardRenderer,
    private val launcher: StoryShareLauncher,
) : StoryShareController {
    override suspend fun share(
        plateUrl: String?,
        format: ShareCardFormat,
        card: @Composable (plate: ImageBitmap?) -> Unit,
    ): StoryShareOutcome {
        val plate: ImageBitmap? = decoder.decode(plateUrl)
        val png: ByteArray = when (format) {
            ShareCardFormat.Story -> renderer.renderStory { card(plate) }
            ShareCardFormat.Square -> renderer.renderSquare { card(plate) }
        }
        return launcher.shareToStories(png)
    }
}
