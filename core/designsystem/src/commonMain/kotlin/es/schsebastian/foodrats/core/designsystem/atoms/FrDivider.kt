package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme

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
    FoodRatsTheme {
        FrDivider()
    }
}
