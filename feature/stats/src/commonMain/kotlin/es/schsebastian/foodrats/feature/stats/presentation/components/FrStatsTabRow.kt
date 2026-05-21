package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.model.Tab as StatsTab
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

@Composable
fun FrStatsTabRow(
    selected: StatsTab,
    onSelect: (StatsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        StatsTab.entries.forEach { tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                text = { Text(resolve(tab.label())) },
            )
        }
    }
}

private fun StatsTab.label(): StatsStringKey = when (this) {
    StatsTab.Week     -> StatsStringKey.TabWeek
    StatsTab.Month    -> StatsStringKey.TabMonth
    StatsTab.Historic -> StatsStringKey.TabHistoric
}
