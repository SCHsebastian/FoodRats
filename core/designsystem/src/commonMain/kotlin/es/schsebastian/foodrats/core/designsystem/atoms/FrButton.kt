package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme

enum class FrButtonVariant { Primary, Secondary, Ghost }

@Composable
fun FrButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FrButtonVariant = FrButtonVariant.Primary,
    enabled: Boolean = true,
) {
    when (variant) {
        FrButtonVariant.Primary   -> Button(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label) }
        FrButtonVariant.Secondary -> OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label) }
        FrButtonVariant.Ghost     -> TextButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label) }
    }
}

@FrPreview
@Composable
private fun FrButtonPreview() {
    FoodRatsTheme {
        FrButton(label = "Primary", onClick = {})
    }
}
