package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Tap-to-rate 1..6 stars. Position 6 is the **Minotauro** tier — a top-of-the-house
 * rating above the regular 1..5 scale, tinted with `streakHot` (forge orange) so it
 * reads as a distinct accolade rather than just "one more star". Renders actual star
 * glyphs — `material-icons-extended` has no KMP-iOS publication; `Icons.Filled.Star`
 * is in `material-icons-core`.
 *
 * Filled 1..5 stars use [LocalFrSemanticColors.current.celebration] (honey/citrus tone)
 * per Apple/Letterboxd convention. The Minotauro star uses `streakHot`. Empty stars use
 * `outlineVariant` so the unselected affordance reads at a glance.
 *
 * Each star is wrapped in a 48dp Box so the tap target meets WCAG 2.2 / Material 3 floor
 * even though the glyph is 32dp. The container declares `selectableGroup()` and each star
 * carries `Role.RadioButton` with `selected = i <= value`, which gives TalkBack "N stars,
 * selected, N of 5" announcements out of the box.
 *
 * Strings are caller-supplied via [starLabel] (e.g. `"$it stars"`) and [groupContentDescription].
 */
@Composable
fun FrStarRatingPicker(
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    value: Int = 0,
    enabled: Boolean = true,
    starLabel: (Int) -> String = { it.toString() },
    groupContentDescription: String? = null,
) {
    val filled = LocalFrSemanticColors.current.celebration
    val minotauro = LocalFrSemanticColors.current.streakHot
    val empty = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .selectableGroup()
            .then(
                if (groupContentDescription != null) {
                    Modifier.semantics { contentDescription = groupContentDescription }
                } else Modifier,
            ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..6).forEach { i ->
            val selected = i <= value
            val isMinotauro = i == 6
            val label = starLabel(i)
            Box(
                modifier = Modifier
                    .size(Sizes.touchTarget)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(i) },
                    )
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                FrIcon(
                    image = Icons.Filled.Star,
                    tint = when {
                        !selected -> empty
                        isMinotauro -> minotauro
                        else -> filled
                    },
                    modifier = Modifier.size(Sizes.starIcon),
                )
            }
        }
    }
}

@FrPreview
@Composable
private fun FrStarRatingPickerPreview() {
    FrPreviewLightDark {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FrStarRatingPicker(onSelect = {}, value = 0)
            FrStarRatingPicker(onSelect = {}, value = 3)
            FrStarRatingPicker(onSelect = {}, value = 5)
            FrStarRatingPicker(onSelect = {}, value = 6)
        }
    }
}
