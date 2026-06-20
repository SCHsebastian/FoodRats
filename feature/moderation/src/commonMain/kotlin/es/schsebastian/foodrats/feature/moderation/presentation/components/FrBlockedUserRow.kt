package es.schsebastian.foodrats.feature.moderation.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.moderation.i18n.ModerationStringKey

/**
 * One blocked account: avatar + display name (or the deleted-user fallback), with a trailing Unblock
 * affordance. Domain-aware (takes an [Account]) so it lives in the feature, not `:core:designsystem`.
 * Rendered inside the blocked-users list card, so it owns only its row padding.
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
        FrAvatar(initials = displayName.take(2), imageUrl = account?.avatarUrl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            FrText(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (unblocking) {
            FrProgressIndicator(modifier = Modifier.size(Sizes.iconMd), strokeWidth = 2.dp)
        } else {
            FrButton(
                label = resolve(ModerationStringKey.BlockedUnblockCta),
                onClick = onUnblock,
                variant = FrButtonVariant.Secondary,
            )
        }
    }
}
