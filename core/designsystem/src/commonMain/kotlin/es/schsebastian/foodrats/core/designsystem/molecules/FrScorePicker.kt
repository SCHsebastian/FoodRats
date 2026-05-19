package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrChip
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrScorePicker(value: Int?, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        (1..10).forEach { i ->
            FrChip(label = i.toString(), onClick = { onChange(i) }, selected = value == i)
        }
    }
}

@FrPreview
@Composable
private fun FrScorePickerPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FrScorePicker(value = null, onChange = {})
            FrScorePicker(value = 7, onChange = {})
            FrScorePicker(value = 10, onChange = {})
        }
    }
}
