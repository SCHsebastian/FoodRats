package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
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
    val semantic = LocalFrSemanticColors.current
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (failed > 0) {
            FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FrIcon(image = FrIcons.Warning, tint = semantic.danger)
                    FrText(
                        text = resolve(FeedStringKey.QueueFailed, failed),
                        style = StructuralType.body,
                        color = StructuralColors.foreground,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FrGlassButton(
                        label = resolve(FeedStringKey.QueueRetryCta),
                        onClick = onRetry,
                        tone = FrButtonTone.Danger,
                        compact = true,
                    )
                    FrGlassButton(
                        label = resolve(FeedStringKey.QueueDismissCta),
                        onClick = onDismiss,
                        tone = FrButtonTone.Ghost,
                        compact = true,
                    )
                }
            }
        }
        if (uploading || pending > 0) {
            FrGlassTile(
                depth = FrTileDepth.Near,
                shape = RoundedCornerShape(Radius.pill),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FrProgressIndicator(
                        modifier = Modifier.size(Sizes.iconSm),
                        strokeWidth = 2.dp,
                    )
                    FrText(
                        text = if (pending > 0) {
                            resolve(FeedStringKey.QueuePending, pending)
                        } else {
                            resolve(FeedStringKey.QueuePublishing)
                        },
                        style = StructuralType.micro,
                        color = StructuralColors.foreground,
                    )
                }
            }
        }
    }
}
