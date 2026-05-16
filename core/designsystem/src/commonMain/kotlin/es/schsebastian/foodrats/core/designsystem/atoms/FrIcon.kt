package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme

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
    FoodRatsTheme {
        FrIcon(image = FrIcons.Camera, contentDescription = "Camera")
    }
}
