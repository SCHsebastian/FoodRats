package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes

/**
 * Circular 1..10 score badge. Width is locked to [Sizes.scoreBadge] so single-digit and
 * double-digit values share the same footprint — the original implementation used
 * padding-driven width and "10" was visibly wider than "1".
 *
 * Numbers render in [FrTextStyles.statNumberSmall] (14sp tabular numerals) so digits don't
 * jitter when values change in animated lists.
 *
 * Strings: [contentDescription] is caller-supplied so the design system stays string-free.
 * Pass `resolve(FeedStringKey.ScoreOutOfTen, score)` (or whatever your feature defines).
 * When `null` the badge is treated as decorative — the surrounding row is expected to carry
 * the meaning (e.g. avatar + score badge inside one mergeDescendants row).
 */
@Composable
fun FrScoreBadge(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.scoreBadge,
    contentDescription: String? = null,
) {
    require(score in 1..10) { "Score must be 1..10, got $score" }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    // No contentDescription override: the inner Text node speaks for itself
                    // (TalkBack will read the digit). When the badge sits inside a
                    // mergeDescendants row, the parent groups it correctly.
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        FrText(
            text = score.toString(),
            style = FrTextStyles.statNumberSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@FrPreview
@Composable
private fun FrScoreBadgePreview() {
    FrPreviewLightDark {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrScoreBadge(score = 1)
            FrScoreBadge(score = 5)
            FrScoreBadge(score = 7)
            FrScoreBadge(score = 8)
            FrScoreBadge(score = 10)
        }
    }
}
