package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.feature.stats.domain.model.DishTally

@Composable
fun FrDishTallyRow(tally: DishTally, modifier: Modifier = Modifier) {
    FrText(
        text = "${tally.dish} · ${tally.count}",
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    )
}
