package es.schsebastian.foodrats.feature.ingredient.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * A single selectable ingredient. Whole row is the tappable surface (spec §7.2).
 *
 * `iconKey` is reserved for the future per-ingredient drawable lookup
 * (spec §7.4); there is no resolver or bundled ingredient art yet, so it is not
 * rendered. Uses Material3 [Checkbox] directly — the established convention for
 * domain-aware feature components (cf. `CrewSettingsScreen`).
 */
@Composable
fun FrIngredientRow(
    name: String,
    iconKey: String?,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Checkbox(checked = selected, enabled = enabled, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.width(Spacing.sm))
        FrText(text = name)
    }
}
