package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius

/**
 * The structural switch — zero-chrome, no outline. A frosted off-track (white @ 16%) lights to the
 * olive `colorScheme.primary` when on, with a 22dp white knob gliding 3dp -> 21dp. Both the track
 * fill and the knob offset animate; the whole pill reserves a >=48dp interactive hit area.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackShape = RoundedCornerShape(Radius.pill)
    val offBg = StructuralColors.foreground.copy(alpha = 0.16f)
    val onBg = MaterialTheme.colorScheme.primary

    val trackColor by animateColorAsState(
        targetValue = if (checked) onBg else offBg,
        animationSpec = tween(Motion.short, easing = Motion.Standard),
        label = "toggleTrack",
    )
    val knobX by animateDpAsState(
        targetValue = if (checked) 21.dp else 3.dp,
        animationSpec = tween(Motion.short, easing = Motion.Standard),
        label = "toggleKnob",
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                .size(width = 46.dp, height = 28.dp)
                .clip(trackShape)
                .background(trackColor, trackShape),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = knobX)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(StructuralColors.foreground, CircleShape),
            )
        }
    }
}

@FrPreview
@Composable
private fun FrGlassTogglePreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FrGlassToggle(checked = false, onCheckedChange = {}, contentDescription = "Off")
                FrGlassToggle(checked = true, onCheckedChange = {}, contentDescription = "On")
            }
        }
    }
}
