package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.FrSemanticColors
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Compact achievement badge chip rendered next to a display name on [ProfileScreen].
 *
 * Domain-free: callers pass the resolved [label] string and a stable [badgeId] token
 * so the icon selection stays outside the domain layer. The chip is hidden when
 * [badgeId] is null (caller is responsible for the null-check; this composable never
 * renders an empty placeholder).
 *
 * Accessibility: the chip is annotated with [label] as its [contentDescription] so
 * TalkBack reads "Gold Chef badge" instead of announcing the trophy icon and the label
 * text separately. The icon is therefore marked decorative.
 *
 * Tiers and their glyphs:
 *  - "first"   → Restaurant (fork + knife) — first plate cooked
 *  - "ten"     → Trophy — 10 plates cooked
 *  - "fifty"   → Trophy + celebration colour — 50 plates cooked
 *  - "hundred" → Trophy + streakHot colour   — 100 plates cooked
 *
 * Displaying this badge on member rows / feed author rows is deferred to the U5b
 * identity-display pass.
 */
@Composable
fun FrProfileBadge(
    badgeId: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val semanticColors: FrSemanticColors = LocalFrSemanticColors.current
    val (icon, tint) = when (badgeId) {
        "first"   -> FrIcons.Restaurant   to MaterialTheme.colorScheme.primary
        "ten"     -> FrIcons.Trophy        to MaterialTheme.colorScheme.primary
        "fifty"   -> FrIcons.Trophy        to semanticColors.celebration
        "hundred" -> FrIcons.Trophy        to semanticColors.streakHot
        // Unknown tier — fall back to trophy + primary; forwards-compatible with future tiers.
        else      -> FrIcons.Trophy        to MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = Spacing.sm, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // decorative — parent semantics carry the label
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        FrText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@FrPreview
@Composable
private fun FrProfileBadgePreview() {
    FrPreviewLightDark {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.md),
        ) {
            FrProfileBadge(badgeId = "first",   label = "First Plate")
            FrProfileBadge(badgeId = "ten",     label = "10 Plates")
            FrProfileBadge(badgeId = "fifty",   label = "50 Plates")
            FrProfileBadge(badgeId = "hundred", label = "100 Plates")
        }
    }
}
