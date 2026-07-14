package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

/**
 * Structural feed indicator for the offline-first publish queue (roadmap §5.2) — the user's only
 * feedback that a just-published plate is still uploading in the background.
 *
 * Domain-aware feed component (resolves [FeedStringKey]); it lives in the feature rather than
 * `:core:designsystem` because it speaks the feature's i18n. Restyled onto the structural language
 * (frosted [FrGlassTile] strata over the media floor — no matte banners).
 *
 * Renders nothing when the queue is idle (both counts zero and no upload in flight). Otherwise it
 * shows at most two stacked strata:
 *  - **terminal-failed** (`failed > 0`): a danger-accented glass tile "N failed to post" with
 *    Retry + Dismiss pills — these entries won't resolve on their own.
 *  - **in-flight** (`uploading || pending > 0`): a small glass "publishing" pill with a spinner —
 *    these drain automatically as the runner uploads/reconciles.
 *
 * The component is purely a reflection of state: as the runner drains/reconciles the queue (a
 * published draft is removed → its count drops to 0, the upload flag clears), the matching row
 * disappears on its own. There is no per-entry detail — the aggregate count is all the cross-feature
 * [es.schsebastian.foodrats.core.domain.meal.MealUploadQueueSnapshot] exposes.
 *
 * @param uploading true while a publish upload is actively in flight ([FeedState.isUploadActive]);
 *   shows the publishing pill even before the queue aggregate reflects the new draft, so feedback is
 *   immediate after tapping publish.
 */
@Composable
fun FrUploadQueueBar(
    pending: Int,
    failed: Int,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    uploading: Boolean = false,
) {
    if (!uploading && pending <= 0 && failed <= 0) return
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (failed > 0) {
            FrQueueFailedTile(
                message = resolve(FeedStringKey.QueueFailed, failed),
                onRetry = onRetry,
                onDismiss = onDismiss,
            )
        }
        if (uploading || pending > 0) {
            FrQueuePendingPill(
                message = if (pending > 0) {
                    resolve(FeedStringKey.QueuePending, pending)
                } else {
                    resolve(FeedStringKey.QueuePublishing)
                },
            )
        }
    }
}
