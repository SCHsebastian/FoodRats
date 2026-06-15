package es.schsebastian.foodrats.app.navigation

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Elevation
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve

/**
 * App-specific bottom navigation: a floating, near-opaque capsule (Feed · Stats) with a *raised*
 * ember capture button straddling its top edge — a pulsing ring nudges the user while they
 * haven't posted today ([hasPostedToday] = false). This is navigation *chrome*, not a reusable
 * design-system component — it knows the app's tabs and resolves its own [SharedStringKey] labels.
 * It assembles design-system atoms (FrIcon/FrText) + tokens, but isn't published in
 * `:core:designsystem`/the catalog.
 *
 * (The DS README's recipe calls for a backdrop blur; Compose has no dependency-free backdrop blur,
 * so the 94%-opaque surface stands in — visually near-identical over the concrete/charcoal background.)
 */
@Composable
internal fun MainBottomBar(
    selected: MainTab,
    hasPostedToday: Boolean,
    onFeedClick: () -> Unit,
    onPassportClick: () -> Unit,
    onStatsClick: () -> Unit,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.sm),
    ) {
        // top padding reserves the overhang so the raised capture button stays within bounds.
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
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
                // Two equal-weight halves flank a fixed center gap so the raised capture button
                // (pinned to TopCenter below) stays perfectly centered. The longest label
                // (Stats / "Estadísticas") sits ALONE on the right half so it doesn't wrap; the two
                // shorter labels (Feed · Passport) share the left half.
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomTab(
                        icon = FrIcons.Home,
                        label = resolve(SharedStringKey.NavTabFeed),
                        selected = selected == MainTab.Feed,
                        onClick = onFeedClick,
                    )
                    BottomTab(
                        icon = FrIcons.Public,
                        label = resolve(SharedStringKey.NavTabPassport),
                        selected = selected == MainTab.Passport,
                        onClick = onPassportClick,
                    )
                }
                // Center gap for the raised capture button (overlaid below).
                Spacer(Modifier.size(Sizes.captureButton))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomTab(
                        icon = FrIcons.Stats,
                        label = resolve(SharedStringKey.NavTabStats),
                        selected = selected == MainTab.Stats,
                        onClick = onStatsClick,
                    )
                }
            }
        }
        CaptureButton(
            contentDescription = resolve(SharedStringKey.NavCaptureCta),
            pulsing = !hasPostedToday,
            onClick = onCaptureClick,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun BottomTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = Motion.short, easing = Motion.Standard),
        label = "MainBottomTabColor",
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrIcon(image = icon, tint = color, contentDescription = label, modifier = Modifier.size(Sizes.iconMd))
        FrText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/**
 * Raised ember capture button. When [pulsing] (the user hasn't posted today) a streak-hot ring
 * grows + fades around it on a slow [Motion.pulse] loop.
 */
@Composable
private fun CaptureButton(
    contentDescription: String,
    pulsing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.secondary, semantic.streakHot),
    )
    val interaction = remember { MutableInteractionSource() }
    // +Spacing.sm leaves room for the pulse ring to grow without clipping.
    Box(
        modifier = modifier.size(Sizes.captureButton + Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "capturePulse")
            val scale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = Motion.pulse, easing = Motion.Standard),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "capturePulseScale",
            )
            val alpha by transition.animateFloat(
                initialValue = 0.8f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = Motion.pulse, easing = Motion.Standard),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "capturePulseAlpha",
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .border(2.dp, semantic.streakHot, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(Sizes.captureButton)
                .clip(CircleShape)
                .background(gradient)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            FrIcon(
                image = FrIcons.Camera,
                tint = MaterialTheme.colorScheme.onSecondary,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconMd),
            )
        }
    }
}
