package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Motion

/**
 * Dark frosted floating chrome — the round back / share / close affordance that hovers over
 * full-bleed media in the Structural variant. Zero-chrome and border-less: read purely by a
 * translucent dark fill ([StructuralColors.glassButton]) over the blurred [FrMediaFloor], with
 * white-on-media content. The dark translucent counterpart to the light `atoms/FrGlassPill`.
 *
 * Translucency is faked the KMP-safe way (a tinted fill over the already-blurred floor); there is
 * no per-tile backdrop blur. Set [danger] for destructive actions (the glyph turns crimson).
 *
 * Pass [enabled] = false to gate the action (e.g. the Feed day-strip's prev/next arrows at the
 * window edge): [onClick] is not invoked, the content dims, and the disabled accessibility state is
 * announced (TalkBack/VoiceOver say "disabled" instead of an enabled, clickable button) — so callers
 * no longer fake it with an alpha hack + no-op lambda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrGlassCircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    // The inner glyph scales with the button `size` (default 44dp → ~24dp, matching the old `iconMd`).
    // Without this, small callers (the 30dp comment Flag/Block/Delete actions) crammed a fixed 24dp
    // glyph into a 30dp circle → the icon rendered oversized/clipped.
    iconSize: Dp = size * 0.55f,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(Motion.quick, easing = Motion.Standard),
        label = "glassCircleButtonPress",
    )

    val contentColor =
        if (danger) LocalFrSemanticColors.current.danger else StructuralColors.foreground

    Surface(
        onClick = onClick,
        enabled = enabled,
        // minimumInteractiveComponentSize() guarantees a >=48dp touch target (WCAG §2.5.5) without
        // growing the visible `size`-dp silhouette — the extra hit area is transparent and extends
        // beyond the painted circle (mirrors FrGlassPill).
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (!enabled) alpha = 0.38f
            }
            .semantics { if (!enabled) disabled() }
            .minimumInteractiveComponentSize()
            .size(size),
        interactionSource = interaction,
        shape = CircleShape,
        color = StructuralColors.glassButton,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            FrIcon(
                image = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@FrPreview
@Composable
private fun FrGlassCircleButtonPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FrGlassCircleButton(icon = FrIcons.Back, onClick = {}, contentDescription = "Back")
                FrGlassCircleButton(icon = FrIcons.Close, onClick = {}, contentDescription = "Close")
            }
        }
    }
}
