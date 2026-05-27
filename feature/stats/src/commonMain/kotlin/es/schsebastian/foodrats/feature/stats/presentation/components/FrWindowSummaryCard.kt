package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.model.WindowStats
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import kotlin.math.round

/**
 * Window summary as a 2-column tile grid: total plates (with a meals-per-day trend sparkline)
 * and average plates per day. Only tiles backed by [WindowStats] data are surfaced — trend
 * deltas, avg-score and show-up-rate tiles from the mock need cross-window computation that
 * does not exist yet, so they are intentionally omitted.
 */
@Composable
fun FrWindowSummaryCard(
    window: WindowStats,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
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
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrStatTile(
            icon = FrIcons.Camera,
            value = total.toString(),
            label = resolve(StatsStringKey.SummaryTotalPlatesLabel),
            tint = MaterialTheme.colorScheme.primary,
            spark = window.dailyMeals.takeIf { it.size >= 2 }?.map { it.toFloat() },
            modifier = Modifier.weight(1f),
        )
        FrStatTile(
            icon = FrIcons.Stats,
            value = formatOneDecimal(avg),
            label = resolve(StatsStringKey.SummaryAvgPerDayLabel),
            tint = semantic.celebration,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatOneDecimal(v: Float): String {
    val rounded = round(v * 10f) / 10f
    val whole = rounded.toInt()
    val tenths = ((rounded - whole) * 10f).toInt()
    return "$whole.${if (tenths < 0) -tenths else tenths}"
}
