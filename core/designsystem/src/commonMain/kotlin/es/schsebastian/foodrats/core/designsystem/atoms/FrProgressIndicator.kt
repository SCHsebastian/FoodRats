package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme

@Composable
fun FrProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 4.dp,
) {
    if (color == Color.Unspecified) {
        CircularProgressIndicator(modifier = modifier, strokeWidth = strokeWidth)
    } else {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    }
}

@FrPreview
@Composable
private fun FrProgressIndicatorPreview() {
    FoodRatsTheme {
        FrProgressIndicator()
    }
}
