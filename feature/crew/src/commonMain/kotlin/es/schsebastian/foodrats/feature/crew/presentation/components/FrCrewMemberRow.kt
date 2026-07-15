package es.schsebastian.foodrats.feature.crew.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrProfileBadge
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey

/**
 * One crew member: avatar + display name, an optional [subtitle] meta line (e.g. role), an
 * optional bio line (U5b — shown when the member has set a bio), an optional achievement badge
 * chip (U5b — shown when the member has earned a badge), and an optional [trailing] affordance
 * (owner-only remove). Rendered inside the members [FrCard] in `CrewSettingsScreen`, so the
 * row owns no card chrome of its own — only its row padding.
 */
@Composable
fun FrCrewMemberRow(
    account: Account?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val displayName = memberDisplayName(
        account = account,
        deletedFallback = resolve(CrewStringKey.MemberDeleted),
        unnamedFallback = resolve(CrewStringKey.MemberUnnamed),
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // decorative — the adjacent display-name label carries the identity for screen readers.
        FrAvatar(initials = displayName.take(2), imageUrl = account?.avatarUrl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            // Name row: display name + optional achievement badge chip (U5b).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FrText(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Badge chip (U5b) — unknown badgeId resolves to null and renders nothing.
                val badgeId = account?.badgeId
                if (badgeId != null) {
                    val badgeLabel = crewBadgeLabel(badgeId)
                    if (badgeLabel != null) {
                        FrProfileBadge(badgeId = badgeId, label = badgeLabel)
                    }
                }
            }
            // Role subtitle line (e.g. Owner / Member).
            if (subtitle != null) {
                FrText(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Bio line (U5b) — shown UNDER the role subtitle when the member has set a bio.
            val bio = account?.bio?.value
            if (!bio.isNullOrBlank()) {
                FrText(
                    text = bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * Maps a [badgeId] to its crew-scoped i18n label. Returns `null` for unrecognised ids so
 * the chip is silently hidden rather than crashing. Crew cannot import `:feature:auth`'s
 * `resolveBadgeLabel` — each feature owns its `<Feature>StringKey` (i18n rule).
 */
@Composable
private fun crewBadgeLabel(badgeId: String): String? = when (badgeId) {
    "first"   -> resolve(CrewStringKey.BadgeFirst)
    "ten"     -> resolve(CrewStringKey.BadgeTen)
    "fifty"   -> resolve(CrewStringKey.BadgeFifty)
    "hundred" -> resolve(CrewStringKey.BadgeHundred)
    else      -> null
}

/**
 * The one display-name-fallback rule for the crew feature: a deleted account falls back to
 * [deletedFallback], a blank display name falls back to the member's handle, and a blank handle
 * falls back to [unnamedFallback]. Shared by [FrCrewMemberRow] and `CrewSettingsScreen`'s confirm
 * dialogs so a member with only a handle set reads the same way everywhere.
 */
internal fun memberDisplayName(
    account: Account?,
    deletedFallback: String,
    unnamedFallback: String,
): String = when {
    account == null -> deletedFallback
    account.displayName.isNotBlank() -> account.displayName
    account.handle.isNotBlank() -> account.handle
    else -> unnamedFallback
}
