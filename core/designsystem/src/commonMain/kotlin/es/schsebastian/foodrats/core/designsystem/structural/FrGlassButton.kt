package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius

/** Action intent. `Primary`/`Ember` are loud CTAs; `Glass`/`Ghost` are quiet on-media chrome; `Danger` is destructive. */
enum class FrButtonTone { Primary, Ember, Glass, Ghost, Danger }

/**
 * The structural pill action button — zero-chrome, built as a clickable [Box] (not a Material
 * `Button`) so the pill silhouette and the [FrButtonTone.Ember] forge-gradient fill are exact.
 *
 * Border-less by default: tones read by fill + extreme-contrast label, never by an outline (the
 * `Glass`/`Ghost` hairline is the only edge, and it's a 1px light, not a box). Press physics scale
 * to 0.97; `enabled = false` dims the whole pill and severs the click.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrGlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: FrButtonTone = FrButtonTone.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fillWidth: Boolean = false,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = LocalFrSemanticColors.current
    val shape = RoundedCornerShape(Radius.pill)

    val solidFill: Color = when (tone) {
        FrButtonTone.Primary -> scheme.primary
        FrButtonTone.Danger -> semantic.danger
        FrButtonTone.Glass -> StructuralColors.glassButton
        FrButtonTone.Ember, FrButtonTone.Ghost -> Color.Transparent
    }
    val gradientFill: Brush? = when (tone) {
        FrButtonTone.Ember -> Brush.linearGradient(listOf(scheme.secondary, semantic.streakHot))
        else -> null
    }
    val content: Color = when (tone) {
        FrButtonTone.Primary -> scheme.onPrimary
        FrButtonTone.Ember -> StructuralColors.foreground
        FrButtonTone.Glass -> StructuralColors.foreground
        FrButtonTone.Ghost -> scheme.primary
        FrButtonTone.Danger -> semantic.onDanger
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.97f else 1f,
        animationSpec = tween(Motion.quick, easing = Motion.Standard),
        label = "buttonPress",
    )

    val height = if (compact) 38.dp else 50.dp
    val horizontalPadding = if (compact) 16.dp else 24.dp
    val labelSize = if (compact) 13.sp else 15.sp

    // Outer hit area: `minimumInteractiveComponentSize()` guarantees a >=48x48dp touch target on BOTH
    // axes (WCAG §2.5.5) without growing the visible silhouette — the extra hit area is transparent
    // and extends beyond the painted pill (mirrors FrGlassCircleButton / FrGlassPill). It sits above
    // `.clickable`, so the clickable node's pointer-input bounds reach the minimum on both width and
    // height; a compact, non-fillWidth pill with a short label can no longer lay out under 48dp wide.
    // The painted pill (inner Box) keeps its `height`-dp silhouette and is centered inside the hit area.
    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(height)
                .clip(shape)
                .then(if (gradientFill != null) Modifier.background(gradientFill, shape) else Modifier.background(solidFill, shape))
                .then(
                    when (tone) {
                        FrButtonTone.Glass -> Modifier.border(1.dp, StructuralColors.foreground.copy(alpha = 0.10f), shape)
                        FrButtonTone.Ghost -> Modifier.border(1.dp, scheme.outline, shape)
                        else -> Modifier
                    },
                )
                .padding(horizontal = horizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(20.dp),
                    )
                }
                FrText(
                    text = label,
                    color = content,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = labelSize,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@FrPreview
@Composable
private fun FrGlassButtonPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FrGlassButton(label = "Primary", onClick = {}, tone = FrButtonTone.Primary)
                FrGlassButton(label = "Ember", onClick = {}, tone = FrButtonTone.Ember)
                FrGlassButton(label = "Glass", onClick = {}, tone = FrButtonTone.Glass)
                FrGlassButton(label = "Ghost", onClick = {}, tone = FrButtonTone.Ghost)
                FrGlassButton(label = "Danger", onClick = {}, tone = FrButtonTone.Danger, fillWidth = true)
            }
        }
    }
}
