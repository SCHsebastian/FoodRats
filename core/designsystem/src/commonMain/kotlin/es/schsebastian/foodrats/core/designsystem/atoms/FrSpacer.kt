package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrSpacer(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Spacer(modifier = modifier.size(size))
}

@FrPreview
@Composable
private fun FrSpacerPreview() {
    FoodRatsTheme {
        FrSpacer(size = Spacing.md)
    }
}
