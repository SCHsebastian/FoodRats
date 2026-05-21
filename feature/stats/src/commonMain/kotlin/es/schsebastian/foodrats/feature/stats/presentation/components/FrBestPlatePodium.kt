package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.atoms.FrCrownBadge
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.model.MealAward
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import kotlin.math.round

@Composable
fun FrBestPlatePodium(
    award: MealAward,
    modifier: Modifier = Modifier,
) {
    val score by animateFloatAsState(
        targetValue = award.score.toFloat(),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "best-plate-score",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = award.photoUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Bottom-gradient overlay for legibility.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FrCrownBadge()
            FrText(
                text = resolve(StatsStringKey.BestPlateTitle),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            FrText(
                text = resolve(StatsStringKey.BestPlateScoreFormat, formatScore(score)),
                color = Color.White,
                style = FrTextStyles.statNumber,
            )
            FrText(
                text = award.dish.value,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            FrText(
                text = resolve(StatsStringKey.BestPlateAuthorFormat, award.author.displayName),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatScore(v: Float): String {
    val r = round(v * 10f) / 10f
    val whole = r.toInt()
    val tenths = ((r - whole) * 10f).toInt()
    return "$whole.${if (tenths < 0) -tenths else tenths}"
}
