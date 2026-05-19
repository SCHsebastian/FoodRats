package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

@Composable
fun FrDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = Color.Unspecified,
) {
    if (color == Color.Unspecified) {
        HorizontalDivider(modifier = modifier, thickness = thickness)
    } else {
        HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
    }
}

@FrPreview
@Composable
private fun FrDividerPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Default", style = MaterialTheme.typography.labelSmall)
            FrDivider()
            Text("Thick", style = MaterialTheme.typography.labelSmall)
            FrDivider(thickness = 3.dp)
            Text("Tinted", style = MaterialTheme.typography.labelSmall)
            FrDivider(color = MaterialTheme.colorScheme.primary)
        }
    }
}
