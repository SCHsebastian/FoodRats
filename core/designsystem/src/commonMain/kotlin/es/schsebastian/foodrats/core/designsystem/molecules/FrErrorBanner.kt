package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Tinted banner used to surface a recoverable error.
 *
 * Carries `liveRegion = Polite` so when the banner appears or its text changes TalkBack
 * announces the new message without the user moving focus. Leading warning icon makes the
 * banner readable at a glance and works as a non-color affordance for color-blind users.
 *
 * Uses Material 3 `errorContainer` / `onErrorContainer` tokens — both refreshed in the
 * design-system v2 palette and exist in light + dark schemes.
 */
@Composable
fun FrErrorBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.colorScheme.errorContainer)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrIcon(
            image = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        FrText(
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@FrPreview
@Composable
private fun FrErrorBannerPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FrErrorBanner(text = "Could not load feed. Check your connection.")
            FrErrorBanner(
                text = "You've already published a meal today — try again tomorrow when the streak rolls over.",
            )
        }
    }
}

