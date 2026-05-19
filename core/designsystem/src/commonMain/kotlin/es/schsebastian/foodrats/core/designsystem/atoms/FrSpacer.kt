package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrSpacer(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Spacer(modifier = modifier.size(size))
}

@FrPreview
@Composable
private fun FrSpacerPreview() {
    FrPreviewLightDark {
        Column(modifier = Modifier.fillMaxWidth()) {
            listOf(
                "xxs (2dp)" to Spacing.xxs,
                "xs (4dp)"  to Spacing.xs,
                "sm (8dp)"  to Spacing.sm,
                "md (16dp)" to Spacing.md,
                "lg (24dp)" to Spacing.lg,
                "xl (32dp)" to Spacing.xl,
            ).forEach { (label, size) ->
                Text(label, style = MaterialTheme.typography.labelSmall)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(size)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
                FrSpacer(size = Spacing.xs)
            }
        }
    }
}
