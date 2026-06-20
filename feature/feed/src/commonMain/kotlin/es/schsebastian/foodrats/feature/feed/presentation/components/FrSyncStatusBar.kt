package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

/**
 * Feed top-bar indicator for the durable write outbox (P2 §1 T8).
 *
 * Sibling of [FrUploadQueueBar]: that bar surfaces the meal-publish queue; this one
 * surfaces the write outbox (rate / comment / reaction / crew-admin mutations
 * parked while offline). Domain-aware feed component (resolves [FeedStringKey]); it
 * lives in the feature rather than `:core:designsystem` for the same reason
 * `FrFeedMealRow` does — it speaks the feature's i18n. Built from
 * `:core:designsystem` atoms only (never raw Material3 chrome).
 *
 * Renders nothing when nothing is queued (both counts zero). When work exists it
 * shows at most two stacked rows:
 *  - **terminal-failed** (`failed > 0`): a danger-tinted banner "N failed" with
 *    Retry + Dismiss affordances — these entries won't resolve on their own.
 *  - **pending** (`pending > 0`): an info row "N waiting" with a small spinner —
 *    these drain automatically once connectivity returns.
 *
 * The component is purely a reflection of state: as the `OutboxRunner` drains the
 * outbox (a replayed command is removed → its count drops to 0), the matching row
 * disappears on its own. There is no per-entry detail — the aggregate count is all
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
    val semantic = LocalFrSemanticColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (failed > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(semantic.danger)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FrIcon(image = FrIcons.Warning, tint = semantic.onDanger)
                FrText(
                    text = resolve(FeedStringKey.SyncFailed, failed),
                    color = semantic.onDanger,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                FrButton(
                    label = resolve(FeedStringKey.QueueRetryCta),
                    onClick = onRetry,
                    variant = FrButtonVariant.Ghost,
                )
                FrButton(
                    label = resolve(FeedStringKey.QueueDismissCta),
                    onClick = onDismiss,
                    variant = FrButtonVariant.Ghost,
                )
            }
        }
        if (pending > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(semantic.info)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FrProgressIndicator(
                    modifier = Modifier.size(Sizes.iconSm),
                    color = semantic.onInfo,
                )
                FrText(
                    text = resolve(FeedStringKey.SyncPending, pending),
                    color = semantic.onInfo,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
