package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrErrorBanner(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(Spacing.md),
    ) {
        FrText(text = text)
    }
}

@FrPreview
@Composable
private fun FrErrorBannerPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FrErrorBanner(text = "Could not load feed. Check your connection.")
            FrErrorBanner(
                text = "Long message: you've already published a meal today — try again tomorrow when the streak rolls over.",
            )
        }
    }
}
