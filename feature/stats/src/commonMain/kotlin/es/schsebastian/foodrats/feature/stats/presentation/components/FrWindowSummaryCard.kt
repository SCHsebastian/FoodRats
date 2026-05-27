package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrSparkline
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.model.WindowStats
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import kotlin.math.round

@Composable
fun FrWindowSummaryCard(
    window: WindowStats,
    modifier: Modifier = Modifier,
) {
    val total by animateIntAsState(
        targetValue = window.totalMeals,
        animationSpec = tween(700),
        label = "total-${window.window.tab}",
    )
    val avg by animateFloatAsState(
        targetValue = window.avgPerDay.toFloat(),
        animationSpec = tween(900),
        label = "avg-${window.window.tab}",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FrText(
                text = total.toString(),
                style = FrTextStyles.statNumber,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FrText(
                text = resolve(StatsStringKey.SummaryTotalPlatesLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FrText(
                text = formatOneDecimal(avg),
                style = FrTextStyles.statNumber,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FrText(
                text = resolve(StatsStringKey.SummaryAvgPerDayLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (window.dailyMeals.size >= 2) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                FrSparkline(
                    data = window.dailyMeals.map { it.toFloat() },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    height = 36.dp,
                )
            }
        }
    }
}

private fun formatOneDecimal(v: Float): String {
    val rounded = round(v * 10f) / 10f
    val whole = rounded.toInt()
    val tenths = ((rounded - whole) * 10f).toInt()
    return "$whole.${if (tenths < 0) -tenths else tenths}"
}
