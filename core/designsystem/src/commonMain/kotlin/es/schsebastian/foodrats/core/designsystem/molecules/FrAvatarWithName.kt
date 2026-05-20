package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Avatar + display-name row. Optionally renders a trailing slot (e.g. score badge,
 * chevron, last-active dot).
 *
 * The whole row is presented as a single accessibility node via `mergeDescendants = true`;
 * TalkBack reads "Sebastián Cardona, 8 out of 10" instead of three separate nodes. The
 * avatar inside is decorative — the name carries the meaning.
 *
 * Defaults:
 *  - 32dp row height floor (touch-target-friendly when `onClick` is set)
 *  - name uses `bodyLarge` and ellipsizes at one line so long names don't break the row
 *  - 12dp gap between avatar and name (Slack/Linear/Discord convention)
 */
@Composable
fun FrAvatarWithName(
    initials: String,
    name: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = Sizes.avatarMd,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rowSemantics = Modifier.semantics(mergeDescendants = true) {
        if (onClick != null) role = Role.Button
    }
    val rowClickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .then(rowClickable)
            .then(rowSemantics),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrAvatar(initials = initials, size = avatarSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@FrPreview
@Composable
private fun FrAvatarWithNamePreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FrAvatarWithName(initials = "SC", name = "Sebastián Cardona")
            FrAvatarWithName(initials = "AN", name = "Anika", subtitle = "Last meal · 2h ago")
            FrAvatarWithName(
                initials = "RK",
                name = "Reggie K. — the long-display-name that has to ellipsize gracefully",
            )
            FrAvatarWithName(
                initials = "SC",
                name = "Tappable row",
                onClick = {},
                trailing = { FrScoreBadge(score = 8) },
            )
        }
    }
}
