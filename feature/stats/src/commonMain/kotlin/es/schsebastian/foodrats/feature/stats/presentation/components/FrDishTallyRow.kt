package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.model.DishTally
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

@Composable
fun FrDishTallyRow(tally: DishTally, modifier: Modifier = Modifier) {
    FrText(
        text = resolve(StatsStringKey.DishTallyRow, tally.dish, tally.count),
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    )
}
