package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrFilterChip
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Multi-select tag row. Wraps onto multiple lines on narrow widths (`FlowRow`) so chips never
 * clip. Each chip is a [FrFilterChip], which carries `Role.Checkbox` semantics — TalkBack
 * announces toggled state per chip automatically.
 *
 * No `selectableGroup` wrapper: that's for single-select (radio) groups. Multi-select chip
 * rows leave each toggleable independent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrTagChipRow(
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        tags.forEach { tag ->
            FrFilterChip(
                label = tag,
                selected = tag in selected,
                onClick = { onToggle(tag) },
            )
        }
    }
}

@FrPreview
@Composable
private fun FrTagChipRowPreview() {
    FrPreviewLightDark {
        FrTagChipRow(
            tags = listOf("breakfast", "lunch", "dinner", "snack", "brunch", "dessert", "drink", "other"),
            selected = setOf("lunch", "snack"),
            onToggle = {},
        )
    }
}
