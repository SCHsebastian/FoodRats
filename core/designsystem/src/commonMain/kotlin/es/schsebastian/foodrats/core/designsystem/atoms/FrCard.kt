package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.theme.LocalMinotaurMode
import es.schsebastian.foodrats.core.designsystem.theme.fur
import es.schsebastian.foodrats.core.designsystem.tokens.Elevation
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * The project's standard rounded `surface` container. Static by default; pass [onClick] to make
 * it interactive, which adds the design-system press feedback — scale to 0.98 and a shadow lift
 * from elevation-1 to 4dp over [Motion.quick] (120ms). No ripple: the press treatment for cards
 * is scale + lift, not a ripple wash (DS README "Cards & elevation").
 *
 * No domain types — content is supplied by the caller. Feature cards that need domain awareness
 * (FrMealCard, FrFeedMealCard) wrap this in their own `presentation/components/`.
 */
@Composable
fun FrCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Radius.lg),
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val interactive = onClick != null && enabled
    val scale by animateFloatAsState(
        targetValue = if (pressed && interactive) 0.98f else 1f,
        animationSpec = tween(durationMillis = Motion.quick, easing = Motion.Standard),
        label = "FrCardScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed && interactive) 4.dp else Elevation.level1,
        animationSpec = tween(durationMillis = Motion.quick, easing = Motion.Standard),
        label = "FrCardElevation",
    )
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    val minotaur = LocalMinotaurMode.current
    val furSemantic = LocalFrSemanticColors.current
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fur(minotaur, shape)
            .then(clickModifier),
        shape = shape,
        color = if (minotaur) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentColor = if (minotaur) furSemantic.onFur else MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (minotaur) 0.dp else elevation,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@FrPreview
@Composable
private fun FrCardPreview() {
    FrPreviewLightDark {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FrCard(modifier = Modifier.fillMaxWidth()) {
                FrText("Static card", style = MaterialTheme.typography.titleMedium)
                FrText("elevation-1, no press feedback", style = MaterialTheme.typography.bodySmall)
            }
            FrCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
                FrText("Clickable card", style = MaterialTheme.typography.titleMedium)
                FrText("press → scale 0.98 + lift to 4dp", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
