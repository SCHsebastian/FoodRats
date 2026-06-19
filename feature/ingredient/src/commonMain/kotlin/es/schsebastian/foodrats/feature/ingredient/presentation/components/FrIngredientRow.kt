package es.schsebastian.foodrats.feature.ingredient.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * A single selectable ingredient. Whole row is the tappable surface (spec §7.2).
 *
 * Selection is shown with a soft animated `primaryContainer` fill + a medium-weight
 * `onPrimaryContainer` label — a stronger affordance than the checkbox alone, while
 * keeping AA contrast (Material container/on-container pair). Idle rows stay flat.
 *
 * `iconKey` is reserved for the future per-ingredient drawable lookup
 * (spec §7.4); there is no resolver or bundled ingredient art yet, so it is not
 * rendered. Uses Material3 [Checkbox] directly — the established convention for
 * domain-aware feature components (cf. `CrewSettingsScreen`).
 */
@Composable
fun FrIngredientRow(
    name: String,
    iconKey: String?,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            .clip(RoundedCornerShape(Radius.sm))
            .background(fill)
            // One labelled checkbox node: the row owns the toggle (role + merged name) and a 48dp
            // target; the inner Checkbox is decorative (onCheckedChange = null) so TalkBack announces
            // "<name>, checkbox, checked" rather than a bare, unlabelled control (WCAG 4.1.2 / 2.5.5).
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        Checkbox(checked = selected, enabled = enabled, onCheckedChange = null)
        Spacer(modifier = Modifier.width(Spacing.sm))
        FrText(
            text = name,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}
