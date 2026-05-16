package es.schsebastian.foodrats.feature.meal.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreBadge
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

data class MealUi(
    val dish: String,
    val score: Int,
    val tags: List<String>,
    val photoBytes: ByteArray?,
)

@Composable
fun FrMealCard(ui: MealUi, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            FrText(text = ui.dish)
            FrScoreBadge(score = ui.score.coerceIn(1, 10), modifier = Modifier.padding(top = Spacing.xs))
            FrText(text = ui.tags.joinToString(" • "))
        }
    }
}
