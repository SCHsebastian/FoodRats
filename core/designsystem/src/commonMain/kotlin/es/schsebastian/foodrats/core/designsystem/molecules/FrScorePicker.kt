package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Single-select picker over an integer range (default 1..10). Used for plate scoring.
 *
 * Design choices, informed by NPS-style 10-segment selectors and Material 3 patterns:
 *  - the current value is rendered large with tabular numerals so the number doesn't jitter
 *    as the user taps between values;
 *  - the cells are uniform fixed-size squares (`Sizes.scoreCell`) so "1" and "10" share the
 *    same footprint — no padding-driven width variance;
 *  - the cells live inside a `FlowRow` so the picker wraps to 5×2 on widths < ~360dp instead
 *    of clipping;
 *  - the container declares `selectableGroup()` and each cell uses `selectable(role = RadioButton)`
 *    so TalkBack/VoiceOver announce "N of M, selected" group-style.
 *
 * Strings are caller-supplied so the design system stays free of localizable text:
 *  - [groupContentDescription] for the whole picker (e.g. "Score, 1 to 10")
 *  - [cellContentDescriptionFor] for each cell (e.g. "Score 7 of 10")
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrScorePicker(
    value: Int?,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..10,
    enabled: Boolean = true,
    showHeadline: Boolean = true,
    groupContentDescription: String? = null,
    cellContentDescriptionFor: ((Int) -> String)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (groupContentDescription != null) {
                    Modifier.semantics { contentDescription = groupContentDescription }
                } else Modifier,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (showHeadline) {
            FrText(
                text = value?.toString() ?: "—",
                style = FrTextStyles.statNumber,
                color = if (value != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            maxItemsInEachRow = range.count(),
        ) {
            range.forEach { i ->
                ScoreCell(
                    label = i.toString(),
                    selected = value == i,
                    enabled = enabled,
                    contentDescription = cellContentDescriptionFor?.invoke(i),
                    onClick = { onChange(i) },
                )
            }
        }
    }
}

@Composable
private fun ScoreCell(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurface
    val border = if (selected) null
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = container,
        contentColor = onContainer,
        border = border,
        modifier = Modifier
            // Keep the 40dp visual cell but guarantee a 48dp tap target (WCAG 2.5.5 / Material) —
            // .selectable alone does not expand the touch area the way M3 RadioButton/Checkbox do.
            .minimumInteractiveComponentSize()
            .size(Sizes.scoreCell)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            FrText(
                text = label,
                style = FrTextStyles.statNumberSmall,
                color = onContainer,
            )
        }
    }
}

@FrPreview
@Composable
private fun FrScorePickerPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FrScorePicker(value = null, onChange = {})
            FrScorePicker(value = 7, onChange = {})
        }
    }
}
