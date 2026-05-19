package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrChip
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrTagChipRow(
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        tags.forEach { tag ->
            FrChip(label = tag, onClick = { onToggle(tag) }, selected = tag in selected)
        }
    }
}

@FrPreview
@Composable
private fun FrTagChipRowPreview() {
    FrPreviewLightDark {
        FrTagChipRow(
            tags = listOf("Pizza", "Salad", "Pasta", "Vegan"),
            selected = setOf("Salad", "Vegan"),
            onToggle = {},
        )
    }
}
