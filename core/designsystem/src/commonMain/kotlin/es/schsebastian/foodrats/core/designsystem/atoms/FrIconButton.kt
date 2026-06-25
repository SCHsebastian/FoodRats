package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

/**
 * Icon-only button. [contentDescription] is required for any actionable button (WCAG 4.1.2);
 * the name is placed on the button node directly (not just the inner icon) so it survives even
 * when a tight parent would otherwise clip the merged semantics. [minimumInteractiveComponentSize]
 * guarantees the 48dp tap target (WCAG 2.5.5 / Material) regardless of how short the parent row is.
 */
@Composable
fun FrIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    role = Role.Button
                }
            },
        enabled = enabled,
    ) {
        Icon(imageVector = icon, contentDescription = null)
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
