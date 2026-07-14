package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.layout.Arrangement
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
 * Shared terminal-failed strata rendered by both [FrUploadQueueBar] and [FrSyncStatusBar]: a
 * danger-accented glass tile with [message] plus Retry/Dismiss pills.
 */
@Composable
internal fun FrQueueFailedTile(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val semantic = LocalFrSemanticColors.current
    FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FrIcon(image = FrIcons.Warning, tint = semantic.danger)
            FrText(
                text = message,
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

/**
 * Shared pending/in-flight strata rendered by both [FrUploadQueueBar] and [FrSyncStatusBar]: a
 * small glass pill with a spinner and [message].
 */
@Composable
internal fun FrQueuePendingPill(message: String) {
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
                text = message,
                style = StructuralType.micro,
                color = StructuralColors.foreground,
            )
        }
    }
}
