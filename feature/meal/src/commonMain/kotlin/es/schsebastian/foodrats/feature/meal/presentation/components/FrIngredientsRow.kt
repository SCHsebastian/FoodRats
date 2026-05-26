package es.schsebastian.foodrats.feature.meal.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey

/**
 * Composer row summarising the AI-detected / user-edited ingredient selection.
 * Three states, all advisory — the row never gates publishing:
 *  - **classifying** → "Analyzing ingredients…", not tappable
 *  - **empty**       → "Add ingredients" CTA
 *  - **populated**   → "N ingredients" + chevron into the picker
 *
 * Tapping opens the ingredient picker via [onTap]. All labels resolve through
 * [MealStringKey] so the row carries no cross-feature dependency.
 */
@Composable
fun FrIngredientsRow(
    classifying: Boolean,
    count: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when {
        classifying -> resolve(MealStringKey.IngredientsClassifying)
        count == 0 -> resolve(MealStringKey.IngredientsRowAdd)
        else -> resolve(MealStringKey.IngredientsRowSummary, count)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = !classifying, onClick = onTap)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        FrText(text = label, modifier = Modifier.weight(1f))
        if (!classifying) {
            FrIcon(
                image = FrIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
