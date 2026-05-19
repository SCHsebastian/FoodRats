package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

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
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FrButton(label = "Primary", onClick = {})
            FrButton(label = "Secondary", onClick = {}, variant = FrButtonVariant.Secondary)
            FrButton(label = "Ghost", onClick = {}, variant = FrButtonVariant.Ghost)
            FrButton(label = "Primary (disabled)", onClick = {}, enabled = false)
            FrButton(label = "Secondary (disabled)", onClick = {}, variant = FrButtonVariant.Secondary, enabled = false)
        }
    }
}
