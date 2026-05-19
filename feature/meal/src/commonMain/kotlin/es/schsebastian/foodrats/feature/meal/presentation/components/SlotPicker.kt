package es.schsebastian.foodrats.feature.meal.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey

@Composable
fun SlotPicker(
    selected: MealSlot,
    taken: Set<MealSlot>,
    onSelect: (MealSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        MealSlot.entries.forEach { slot ->
            val isTaken = slot in taken
            FrButton(
                label = resolve(slot.toStringKey()),
                onClick = { onSelect(slot) },
                variant = if (slot == selected) FrButtonVariant.Primary else FrButtonVariant.Secondary,
                enabled = !isTaken,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun MealSlot.toStringKey(): MealStringKey = when (this) {
    MealSlot.Breakfast -> MealStringKey.SlotBreakfast
    MealSlot.Lunch     -> MealStringKey.SlotLunch
    MealSlot.Dinner    -> MealStringKey.SlotDinner
}
