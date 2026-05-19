package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

@Composable
fun FrChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        colors = if (selected) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
}

@FrPreview
@Composable
private fun FrChipPreview() {
    FrPreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrChip(label = "Pizza", onClick = {}, selected = true)
            FrChip(label = "Salad", onClick = {}, selected = false)
            FrChip(label = "Off", onClick = {}, enabled = false)
        }
    }
}
