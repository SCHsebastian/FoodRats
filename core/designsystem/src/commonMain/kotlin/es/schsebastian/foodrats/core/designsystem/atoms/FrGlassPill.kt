package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes

/**
 * Circular translucent "glass" affordance for overlaying on full-bleed photos — back / close /
 * share. Uses a 92%-opaque `surface` + hairline outline (the documented in-photo recipe). True
 * backdrop blur needs a platform shader; the near-opaque surface reproduces the look without one.
 *
 * Carries `Role.Button` via [Surface]'s clickable overload; [contentDescription] is required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrGlassPill(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Surface(
        onClick = onClick,
        // minimumInteractiveComponentSize() ensures at least 48×48dp touch target (WCAG §2.5.5 /
        // a11y audit fix) without changing the visible 40dp pill silhouette — the extra tap area is
        // transparent and extends outside the painted circle (mirrors FrIconButton.kt).
        modifier = modifier.minimumInteractiveComponentSize().size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            FrIcon(
                image = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(Sizes.iconMd),
            )
        }
    }
}

@FrPreview
@Composable
private fun FrGlassPillPreview() {
    FrPreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FrGlassPill(icon = FrIcons.Back, onClick = {}, contentDescription = "Back")
            FrGlassPill(icon = FrIcons.Close, onClick = {}, contentDescription = "Close")
        }
    }
}
