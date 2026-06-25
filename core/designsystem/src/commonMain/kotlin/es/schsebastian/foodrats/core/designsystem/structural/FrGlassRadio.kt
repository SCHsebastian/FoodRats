package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.tokens.Motion

/**
 * The structural radio control — zero-chrome, frosted-picker selection mark. A bare 22.dp ring on
 * the media floor (no Material `RadioButton`, no box): unselected reads as a faint white edge-light,
 * selected snaps to an olive ring filled with an inset olive dot. Pressed scales to 0.92.
 *
 * Pass a non-null [onClick] to make this a self-contained, focusable radio: the 22.dp glyph sits
 * inside a >=48.dp invisible [Modifier.selectable] hit area (with [Role.RadioButton]) for
 * thumb-friendly stand-alone pickers. Pass `onClick = null` to make it **purely decorative** — it
 * then renders only the visual ring and adds NO semantics node, so when it's nested inside an
 * already-labelled selectable row the row owns the single radio announcement (avoids the
 * double-announce / unlabelled-control WCAG 4.1.2 violation).
 */
@Composable
fun FrGlassRadio(
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    // The unselected ring was `foreground` @45% (≈2.3:1) — fine white-on-dark, but a faint dark hairline
    // on the warm-white light floor. Raise it in light so an unselected option still reads as a ring.
    val unselectedAlpha = if (StructuralColors.isLight) 0.70f else 0.45f
    val ringColor = if (selected) scheme.primary else StructuralColors.foreground.copy(alpha = unselectedAlpha)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.92f else 1f,
        animationSpec = tween(Motion.quick, easing = Motion.Standard),
        label = "radioPress",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            // Decorative when onClick == null: render only the ring, add no selection semantics so a
            // labelled parent row stays the single radio node (WCAG 4.1.2). A non-null onClick makes
            // this a stand-alone, focusable radio with its own >=48.dp hit area.
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 0.4f
                }
                .border(2.dp, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
        }
    }
}

@FrPreview
@Composable
private fun FrGlassRadioPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FrGlassRadio(selected = true, onClick = {})
                FrGlassRadio(selected = false, onClick = {})
            }
        }
    }
}
