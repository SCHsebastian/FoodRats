package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import kotlin.math.max

/**
 * Vote-distribution histogram for the meal-detail screen. One bar per score 1..[maxScore]; bar
 * heights are relative to the busiest bucket. Scores at/above [hotThreshold] use [barColor]; the
 * rest use a muted outline tint. Empty buckets show a faint 2dp stub. Heights animate in on the
 * decelerated curve.
 *
 * [votes] maps score → count; missing scores are treated as zero.
 */
@Composable
fun FrVoteBars(
    votes: Map<Int, Int>,
    modifier: Modifier = Modifier,
    maxScore: Int = 10,
    hotThreshold: Int = 8,
    barColor: Color = MaterialTheme.colorScheme.primary,
    maxBarHeight: Dp = 40.dp,
) {
    val maxCount = max(1, votes.values.maxOrNull() ?: 0)
    val mutedColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        (1..maxScore).forEach { score ->
            val count = votes[score] ?: 0
            val fraction = count.toFloat() / maxCount
            val barHeight by animateDpAsState(
                targetValue = (maxBarHeight * fraction).coerceAtLeast(2.dp),
                animationSpec = tween(durationMillis = Motion.medium, easing = Motion.Decelerated),
                label = "FrVoteBarHeight",
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (count > 0 && score >= hotThreshold) {
                                barColor
                            } else {
                                mutedColor.copy(alpha = if (count > 0) 1f else 0.4f)
                            },
                        ),
                )
                FrText(
                    text = score.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@FrPreview
@Composable
private fun FrVoteBarsPreview() {
    FrPreviewLightDark {
        FrVoteBars(votes = mapOf(6 to 1, 7 to 2, 8 to 4, 9 to 2, 10 to 1))
    }
}
