package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Small streak pill — 🔥 + day count on the forge-orange `streakHot` semantic color. Renders
 * nothing when [days] <= 0. The 🔥 glyph is on-spec *content* (DS README "Emoji as content"),
 * and digits use [FrTextStyles.statNumberSmall] tabular numerals so the pill doesn't jitter when
 * the streak ticks up.
 *
 * Pass [contentDescription] (resolved from a feature StringKey) so screen readers announce the
 * streak; left null the row is decorative and its digits read literally.
 */
@Composable
fun FrFlameBadge(
    days: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (days <= 0) return
    val semantic = LocalFrSemanticColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(semantic.streakHot)
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrText(text = "🔥")
        FrText(
            text = days.toString(),
            style = FrTextStyles.statNumberSmall,
            color = semantic.onStreakHot,
        )
    }
}

@FrPreview
@Composable
private fun FrFlameBadgePreview() {
    FrPreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FrFlameBadge(days = 1)
            FrFlameBadge(days = 7)
            FrFlameBadge(days = 42)
        }
    }
}
