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
 * Structural feed indicator for the durable write outbox (P2 §1 T8).
 *
 * Sibling of [FrUploadQueueBar]: that bar surfaces the meal-publish queue; this one surfaces the
 * write outbox (rate / comment / reaction / crew-admin mutations parked while offline). Domain-aware
 * feed component (resolves [FeedStringKey]); it lives in the feature rather than
 * `:core:designsystem` because it speaks the feature's i18n. Restyled onto the structural language
 * (frosted [FrGlassTile] strata over the media floor — no matte banners).
 *
 * Renders nothing when nothing is queued (both counts zero). When work exists it shows at most two
 * stacked strata:
 *  - **terminal-failed** (`failed > 0`): a danger-accented glass tile "N failed to sync" with
 *    Retry + Dismiss pills — these entries won't resolve on their own.
 *  - **pending** (`pending > 0`): a small glass pill "N waiting to sync" with a spinner — these
 *    drain automatically once connectivity returns.
 *
 * The component is purely a reflection of state: as the `OutboxRunner` drains the outbox (a replayed
 * command is removed → its count drops to 0), the matching row disappears on its own. There is no
 * per-entry detail — the aggregate count is all
 * [es.schsebastian.foodrats.core.domain.outbox.OutboxPendingSnapshot] exposes.
 */
@Composable
fun FrSyncStatusBar(
    pending: Int,
    failed: Int,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pending <= 0 && failed <= 0) return
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (failed > 0) {
            FrQueueFailedTile(
                message = resolve(FeedStringKey.SyncFailed, failed),
                onRetry = onRetry,
                onDismiss = onDismiss,
            )
        }
        if (pending > 0) {
            FrQueuePendingPill(message = resolve(FeedStringKey.SyncPending, pending))
        }
    }
}
