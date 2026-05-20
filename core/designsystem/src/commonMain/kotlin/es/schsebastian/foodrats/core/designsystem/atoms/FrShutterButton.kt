package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes

/**
 * Camera-style capture button: 72dp circle, ringed border, no fill. Carries [Role.Button]
 * semantics and requires a caller-supplied [contentDescription] (typically resolved to
 * "Take photo" / "Capturar foto" via the feature's StringKey).
 */
@Composable
fun FrShutterButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .size(Sizes.shutter)
            .clip(CircleShape)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
    ) {
        Box(contentAlignment = Alignment.Center) {}
    }
}

@FrPreview
@Composable
private fun FrShutterButtonPreview() {
    FrPreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FrShutterButton(onClick = {}, contentDescription = "Take photo")
            FrShutterButton(onClick = {}, contentDescription = "Take photo", enabled = false)
        }
    }
}
