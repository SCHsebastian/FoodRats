package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrEmptyState(
    icon: ImageVector,
    headline: String,
    subtext: String,
    modifier: Modifier = Modifier,
    cta: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FrIcon(image = icon)
        FrText(text = headline, modifier = Modifier.padding(top = Spacing.md))
        FrText(text = subtext, modifier = Modifier.padding(top = Spacing.xs))
        cta?.let {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = Spacing.md))
            it()
        }
    }
}

@FrPreview
@Composable
private fun FrEmptyStatePreview() {
    FrPreviewLightDark {
        FrEmptyState(
            icon = FrIcons.AddPhoto,
            headline = "No meals yet today",
            subtext = "Capture your first plate to start a streak.",
            cta = { FrButton(label = "Capture meal", onClick = {}) },
        )
    }
}

@FrPreview
@Composable
private fun FrEmptyStateNoCtaPreview() {
    FrPreviewLightDark {
        FrEmptyState(
            icon = FrIcons.CameraOff,
            headline = "Camera unavailable",
            subtext = "Pick a photo from the gallery instead.",
        )
    }
}
