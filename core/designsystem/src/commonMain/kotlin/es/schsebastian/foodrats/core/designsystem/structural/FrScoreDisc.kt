package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/** Fill role of a [FrScoreDisc] — olive default, "hot" streak ember, or a muted translucent stratum. */
enum class FrScoreTone { Olive, Hot, Muted }

/**
 * Structural circular score badge: a width-locked disc of tabular numerals reading a single 1..10
 * score. Zero chrome — no border, just a filled [CircleShape] over the media floor — with extreme
 * 800-weight contrast so the number sits as a hard chip against the frosted strata.
 */
@Composable
fun FrScoreDisc(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tone: FrScoreTone = FrScoreTone.Olive,
    contentDescription: String? = null,
) {
    require(score in 1..10) { "score must be in 1..10, was $score" }

    val semantic = LocalFrSemanticColors.current
    val background: Color
    val numberColor: Color
    when (tone) {
        FrScoreTone.Olive -> {
            background = MaterialTheme.colorScheme.primary
            numberColor = MaterialTheme.colorScheme.onPrimary
        }
        FrScoreTone.Hot -> {
            background = semantic.streakHot
            numberColor = semantic.onStreakHot
        }
        FrScoreTone.Muted -> {
            background = StructuralColors.tileNear
            numberColor = StructuralColors.foreground
        }
    }

    Box(
        modifier = modifier
            .then(
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .size(size)
            .background(color = background, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FrText(
            text = score.toString(),
            color = numberColor,
            style = if (size >= 44.dp) FrTextStyles.statNumber else FrTextStyles.statNumberSmall,
        )
    }
}

@FrPreview
@Composable
private fun FrScoreDiscPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FrScoreDisc(9, tone = FrScoreTone.Olive)
                FrScoreDisc(10, tone = FrScoreTone.Hot)
                FrScoreDisc(7, tone = FrScoreTone.Muted)
            }
        }
    }
}
