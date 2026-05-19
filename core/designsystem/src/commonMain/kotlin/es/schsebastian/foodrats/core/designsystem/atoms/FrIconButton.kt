package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

@Composable
fun FrIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@FrPreview
@Composable
private fun FrIconButtonPreview() {
    FrPreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FrIconButton(icon = FrIcons.Back, onClick = {}, contentDescription = "Back")
            FrIconButton(icon = FrIcons.Settings, onClick = {}, contentDescription = "Settings")
            FrIconButton(icon = FrIcons.ChevronLeft, onClick = {}, contentDescription = "Prev")
            FrIconButton(icon = FrIcons.ChevronRight, onClick = {}, contentDescription = "Next")
            FrIconButton(icon = FrIcons.Settings, onClick = {}, contentDescription = "Disabled", enabled = false)
        }
    }
}
