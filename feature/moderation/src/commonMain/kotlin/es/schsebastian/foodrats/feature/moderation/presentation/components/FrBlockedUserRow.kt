package es.schsebastian.foodrats.feature.moderation.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassAvatar
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.moderation.i18n.ModerationStringKey

/**
 * One blocked account: a frosted glass avatar + display name (or the deleted-user fallback), with a
 * trailing glass Unblock affordance. Domain-aware (takes an [Account]) so it lives in the feature,
 * not `:core:designsystem`. Structural-styled: rendered inside the blocked-users list glass tile, so
 * it owns only its row padding.
 */
@Composable
fun FrBlockedUserRow(
    account: Account?,
    unblocking: Boolean,
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = account?.displayName?.takeIf { it.isNotBlank() }
        ?: account?.handle?.takeIf { it.isNotBlank() }
        ?: resolve(ModerationStringKey.BlockedMemberDeleted)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // decorative — the adjacent display-name label carries the identity for screen readers.
        FrGlassAvatar(
            initials = displayName.take(2),
            image = account?.avatarUrl?.let { rememberAsyncImagePainter(it) },
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            FrText(
                text = displayName,
                style = StructuralType.titleMd,
                color = StructuralColors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (unblocking) {
            FrProgressIndicator(modifier = Modifier.size(Sizes.iconMd), strokeWidth = 2.dp)
        } else {
            FrGlassButton(
                label = resolve(ModerationStringKey.BlockedUnblockCta),
                onClick = onUnblock,
                tone = FrButtonTone.Glass,
                compact = true,
            )
        }
    }
}
