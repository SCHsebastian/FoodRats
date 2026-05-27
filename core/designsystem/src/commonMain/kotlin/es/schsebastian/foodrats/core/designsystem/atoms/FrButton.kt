package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Motion

enum class FrButtonVariant { Primary, Secondary, Ghost, Danger }

@Composable
fun FrButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FrButtonVariant = FrButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scaled = modifier.pressScale(interactionSource)
    when (variant) {
        FrButtonVariant.Primary   -> Button(onClick = onClick, modifier = scaled, enabled = enabled, interactionSource = interactionSource) { Text(label) }
        FrButtonVariant.Secondary -> OutlinedButton(onClick = onClick, modifier = scaled, enabled = enabled, interactionSource = interactionSource) { Text(label) }
        FrButtonVariant.Ghost     -> TextButton(onClick = onClick, modifier = scaled, enabled = enabled, interactionSource = interactionSource) { Text(label) }
        FrButtonVariant.Danger    -> {
            val semantic = LocalFrSemanticColors.current
            Button(
                onClick = onClick,
                modifier = scaled,
                enabled = enabled,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = semantic.danger,
                    contentColor = semantic.onDanger,
                ),
            ) { Text(label) }
        }
    }
}

/**
 * Press feedback per the design system: scale to 0.97 while pressed, over [Motion.quick] (120ms)
 * with the [Motion.Standard] easing. Driven by the button's own [interactionSource] so it tracks
 * real press state (including programmatic and accessibility presses).
 */
@Composable
private fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = Motion.quick, easing = Motion.Standard),
        label = "FrButtonPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@FrPreview
@Composable
private fun FrButtonPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FrButton(label = "Primary", onClick = {})
            FrButton(label = "Secondary", onClick = {}, variant = FrButtonVariant.Secondary)
            FrButton(label = "Ghost", onClick = {}, variant = FrButtonVariant.Ghost)
            FrButton(label = "Danger", onClick = {}, variant = FrButtonVariant.Danger)
            FrButton(label = "Primary (disabled)", onClick = {}, enabled = false)
            FrButton(label = "Secondary (disabled)", onClick = {}, variant = FrButtonVariant.Secondary, enabled = false)
            FrButton(label = "Danger (disabled)", onClick = {}, variant = FrButtonVariant.Danger, enabled = false)
        }
    }
}
