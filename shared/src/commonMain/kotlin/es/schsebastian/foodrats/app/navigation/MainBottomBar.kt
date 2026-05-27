package es.schsebastian.foodrats.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * App-specific bottom navigation: a floating, near-opaque capsule (Feed · ember capture · Stats)
 * that clears the system navigation inset. This is navigation *chrome*, not a reusable design-system
 * component — it knows the app's tabs and resolves its own [SharedStringKey] labels. It assembles
 * design-system atoms (FrIcon/FrText) + tokens, but isn't published in `:core:designsystem`/the catalog.
 *
 * (The DS README's recipe calls for a backdrop blur; Compose has no dependency-free backdrop blur,
 * so the 94%-opaque surface stands in — visually near-identical over the concrete/charcoal background.)
 */
@Composable
internal fun MainBottomBar(
    isStats: Boolean,
    onFeedClick: () -> Unit,
    onStatsClick: () -> Unit,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                BottomTab(
                    icon = FrIcons.Home,
                    label = resolve(SharedStringKey.NavTabFeed),
                    selected = !isStats,
                    onClick = onFeedClick,
                )
                CaptureButton(
                    contentDescription = resolve(SharedStringKey.NavCaptureCta),
                    onClick = onCaptureClick,
                )
                BottomTab(
                    icon = FrIcons.Stats,
                    label = resolve(SharedStringKey.NavTabStats),
                    selected = isStats,
                    onClick = onStatsClick,
                )
            }
        }
    }
}

@Composable
private fun BottomTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = Motion.short, easing = Motion.Standard),
        label = "MainBottomTabColor",
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.md))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrIcon(image = icon, tint = color, contentDescription = label, modifier = Modifier.size(Sizes.iconMd))
        FrText(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CaptureButton(contentDescription: String, onClick: () -> Unit) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.secondary, LocalFrSemanticColors.current.streakHot),
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(Sizes.touchTarget)
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
