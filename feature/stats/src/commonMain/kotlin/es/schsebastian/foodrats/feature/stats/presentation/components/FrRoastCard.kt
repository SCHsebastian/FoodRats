package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreStyle
import es.schsebastian.foodrats.core.designsystem.molecules.scoreToEmoji
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.model.MemberAverage
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

@Composable
fun FrRoastCard(
    award: MemberAverage,
    scoreStyle: FrScoreStyle = FrScoreStyle.Stars,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(semantic.warning)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrAvatar(
            initials = award.displayName.take(2),
            imageUrl = award.avatarUrl,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            FrText(
                text = resolve(StatsStringKey.MostCriticizedTitle),
                style = MaterialTheme.typography.labelMedium,
                color = semantic.onWarning.copy(alpha = 0.85f),
            )
            FrText(
                text = award.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = semantic.onWarning,
            )
            // C8b — render the average in the crew's chosen score style.
            val avgStr = formatOneDecimal(award.averageScore.toFloat())
            val avgRoundedInt = kotlin.math.round(award.averageScore).toInt().coerceIn(1, 5)
            val metricText = when (scoreStyle) {
                FrScoreStyle.Stars   -> resolve(StatsStringKey.MostCriticizedMetricFormat, avgStr)
                FrScoreStyle.Emoji   -> resolve(StatsStringKey.MostCriticizedMetricFormatGlyphFree, scoreToEmoji(avgRoundedInt))
                FrScoreStyle.Numeric -> resolve(StatsStringKey.MostCriticizedMetricFormatGlyphFree, avgStr)
            }
            FrText(
                text = metricText,
                style = MaterialTheme.typography.bodyMedium,
                color = semantic.onWarning.copy(alpha = 0.9f),
            )
        }
    }
}
