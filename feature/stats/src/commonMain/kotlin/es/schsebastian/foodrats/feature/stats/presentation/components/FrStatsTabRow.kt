package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.molecules.FrSegmented
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.model.Tab as StatsTab
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

@Composable
fun FrStatsTabRow(
    selected: StatsTab,
    onSelect: (StatsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = StatsTab.entries.map { resolve(it.label()) }
    FrSegmented(
        options = labels,
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(StatsTab.entries[it]) },
        modifier = modifier,
    )
}

private fun StatsTab.label(): StatsStringKey = when (this) {
    StatsTab.Week     -> StatsStringKey.TabWeek
    StatsTab.Month    -> StatsStringKey.TabMonth
    StatsTab.Historic -> StatsStringKey.TabHistoric
}
