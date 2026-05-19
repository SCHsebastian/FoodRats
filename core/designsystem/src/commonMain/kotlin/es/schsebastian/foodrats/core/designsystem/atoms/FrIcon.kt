package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes

@Composable
fun FrIcon(
    image: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = image,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@FrPreview
@Composable
private fun FrIconPreview() {
    FrPreviewLightDark {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrIcon(image = FrIcons.Home, contentDescription = "Home", modifier = Modifier.size(Sizes.iconSm))
            FrIcon(image = FrIcons.Stats, contentDescription = "Stats", modifier = Modifier.size(Sizes.iconMd))
            FrIcon(image = FrIcons.Camera, contentDescription = "Camera", modifier = Modifier.size(Sizes.iconLg))
            FrIcon(
                image = FrIcons.Settings,
                contentDescription = "Settings tinted",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.iconMd),
            )
        }
    }
}
