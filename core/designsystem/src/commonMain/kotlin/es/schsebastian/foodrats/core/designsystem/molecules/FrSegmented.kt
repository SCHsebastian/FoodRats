package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Elevation
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Segmented control — a rounded pill row of mutually exclusive options. The track is
 * `surfaceVariant`; the active segment lifts onto a `surface` chip with elevation-1. Carries
 * `selectableGroup` + per-segment `Role.Tab` semantics.
 *
 * Labels are caller-supplied strings (resolve `StringKey`s at the call site).
 */
@Composable
fun FrSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                animationSpec = tween(durationMillis = Motion.short, easing = Motion.Standard),
                label = "FrSegmentedBackground",
            )
            FrText(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .shadow(if (active) Elevation.level1 else 0.dp, RoundedCornerShape(Radius.pill))
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(background)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

@FrPreview
@Composable
private fun FrSegmentedPreview() {
    FrPreviewLightDark {
        FrSegmented(
            options = listOf("Day", "Week", "Month"),
            selectedIndex = 1,
            onSelect = {},
        )
    }
}
