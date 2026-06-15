package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

private val SegmentHeight = 3.dp

/**
 * Instagram-Stories-style segmented progress header: one rounded pill per scene, laid out in a row.
 * The pill at [currentIndex] fills left-to-right by [currentProgress] (0f..1f); pills before it are
 * full, pills after it are empty.
 *
 * A pure design-system atom (spec §4.1) — takes **primitives only**, no domain types, no timer. The
 * caller owns the auto-advance clock and feeds the animated [currentProgress]; this atom only draws.
 * That separation is what lets Wave 3 render a static frame (pass a fixed progress) to a share card.
 *
 * @param segmentCount total number of scenes (≥ 1).
 * @param currentIndex the active scene index in `0 until segmentCount`.
 * @param currentProgress fill fraction of the active segment, coerced to 0f..1f.
 * @param trackColor the unfilled segment color (defaults to a translucent white, since stories run
 *   over photo/dark backgrounds).
 * @param fillColor the filled segment color.
 */
@Composable
fun FrStoryProgressBar(
    segmentCount: Int,
    currentIndex: Int,
    currentProgress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.30f),
    fillColor: Color = Color.White,
    contentDescription: String? = null,
) {
    val count = segmentCount.coerceAtLeast(1)
    val active = currentIndex.coerceIn(0, count - 1)
    val progress = currentProgress.coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SegmentHeight)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        // Overall completion across all segments, for accessibility.
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = (active + progress) / count,
                            range = 0f..1f,
                        )
                    }
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        for (i in 0 until count) {
            val fraction = when {
                i < active -> 1f
                i == active -> progress
                else -> 0f
            }
            StorySegment(
                fraction = fraction,
                trackColor = trackColor,
                fillColor = fillColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StorySegment(
    fraction: Float,
    trackColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(SegmentHeight / 2))
            .background(trackColor),
    ) {
        // A child that occupies `fraction` of the parent width, drawn in the fill color.
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(fillColor)
                .layout { measurable, constraints ->
                    val width = (constraints.maxWidth * fraction).toInt()
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = width, maxWidth = width),
                    )
                    layout(width, placeable.height) { placeable.place(0, 0) }
                },
    ) {}
    }
}

@FrPreview
@Composable
private fun FrStoryProgressBarPreview() {
    FrPreviewLightDark {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            FrStoryProgressBar(
                segmentCount = 5,
                currentIndex = 2,
                currentProgress = 0.4f,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                fillColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
