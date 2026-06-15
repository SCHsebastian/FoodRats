package es.schsebastian.foodrats.core.data.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat

/**
 * In-`commonMain` [StoryShareController] test double (mirrors `RecordingAnalyticsTracker`): records
 * each share call and returns a configurable [outcome] without composing/rendering (the real Compose
 * rasterization is platform-only and verified on device — spec §12). Lets feed/stats ViewModel tests
 * assert the share sequence (outcome handling, `isPreparingShare` toggling, analytics fired only on a
 * non-Failed outcome) without a live renderer. The `card` lambda is intentionally NOT invoked —
 * composables cannot run outside composition; the mapper that builds its props is tested separately.
 */
class RecordingStoryShareController(
    var outcome: StoryShareOutcome = StoryShareOutcome.OpenedInstagram,
) : StoryShareController {

    data class Call(val plateUrl: String?, val format: ShareCardFormat)

    val calls: MutableList<Call> = mutableListOf()
    val callCount: Int get() = calls.size
    val lastCall: Call? get() = calls.lastOrNull()

    override suspend fun share(
        plateUrl: String?,
        format: ShareCardFormat,
        card: @Composable (plate: ImageBitmap?) -> Unit,
    ): StoryShareOutcome {
        calls += Call(plateUrl, format)
        return outcome
    }
}
