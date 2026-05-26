package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Elevation
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/** One navigation destination in [FrBottomBar]. Primitives only — no domain/nav types. */
data class FrBottomBarItem(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String,
)

/**
 * The optional ember "capture" action that sits in the visual center of [FrBottomBar].
 * [highlightRing] draws a pulsing attention ring (e.g. when the user hasn't posted today).
 */
data class FrBottomBarCapture(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val highlightRing: Boolean = false,
)

/**
 * Floating navigation capsule — rounded `xl`, near-opaque `surface` with a hairline outline and
 * an elevation-4 shadow, meant to float above the bottom inset. [tabs] split evenly around the
 * optional [capture] action in the center.
 *
 * Per the DS README the recipe specifies a backdrop blur; Compose has no dependency-free backdrop
 * blur, so the 94%-opaque surface stands in — visually near-identical against the concrete/charcoal
 * background. Wrap the call in your own inset padding (the bar itself adds none).
 */
@Composable
fun FrBottomBar(
    tabs: List<FrBottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    capture: FrBottomBarCapture? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shadowElevation = Elevation.level4,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val mid = tabs.size / 2
            tabs.take(mid).forEachIndexed { i, item ->
                BottomTab(item = item, selected = i == selectedIndex, onClick = { onSelect(i) })
            }
            if (capture != null) {
                CaptureButton(capture)
            }
            tabs.drop(mid).forEachIndexed { j, item ->
                val index = mid + j
                BottomTab(item = item, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
    }
}

@Composable
private fun BottomTab(item: FrBottomBarItem, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = Motion.short, easing = Motion.Standard),
        label = "FrBottomTabColor",
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.md))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrIcon(
            image = item.icon,
            tint = color,
            contentDescription = item.contentDescription,
            modifier = Modifier.size(Sizes.iconMd),
        )
        FrText(text = item.label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CaptureButton(capture: FrBottomBarCapture) {
    val semantic = LocalFrSemanticColors.current
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.secondary, semantic.streakHot),
    )
    val interaction = remember { MutableInteractionSource() }
    val ringSize = Sizes.touchTarget + Spacing.sm
    Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        if (capture.highlightRing) {
            val transition = rememberInfiniteTransition(label = "FrCaptureRing")
            val ringScale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
                label = "FrCaptureRingScale",
            )
            val ringAlpha by transition.animateFloat(
                initialValue = 0.7f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
                label = "FrCaptureRingAlpha",
            )
            Box(
                modifier = Modifier
                    .size(ringSize)
                    .graphicsLayer {
                        scaleX = ringScale
                        scaleY = ringScale
                        alpha = ringAlpha
                    }
                    .border(2.dp, semantic.streakHot, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(Sizes.touchTarget)
                .clip(CircleShape)
                .background(gradient)
                .clickable(interactionSource = interaction, indication = null, onClick = capture.onClick)
                .semantics {
                    contentDescription = capture.contentDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            FrIcon(
                image = capture.icon,
                tint = MaterialTheme.colorScheme.onSecondary,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconMd),
            )
        }
    }
}

@FrPreview
@Composable
private fun FrBottomBarPreview() {
    FrPreviewLightDark {
        FrBottomBar(
            tabs = listOf(
                FrBottomBarItem(FrIcons.Home, "Feed", "Feed"),
                FrBottomBarItem(FrIcons.Stats, "Stats", "Stats"),
            ),
            selectedIndex = 0,
            onSelect = {},
            capture = FrBottomBarCapture(FrIcons.Camera, "Capture meal", onClick = {}, highlightRing = true),
        )
    }
}
