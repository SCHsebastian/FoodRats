package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme

/**
 * Structural section eyebrow — the olive ultra-wide-tracked label that hangs above a content plane
 * with no card, rule, or chrome around it. Zero-chrome: the type *is* the section marker. Callers pass
 * already-uppercased text (the DS is string-free, so no transform happens here).
 */
@Composable
fun FrEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    // Olive `primary` (#4F6E2B) is only ≈3.5:1 on the warm-concrete light floor — below AA for small
    // uppercase. In light fall back to a high-contrast neutral foreground; dark keeps the moss primary
    // (which clears AA on the charcoal floor), so the dark eyebrow look is unchanged.
    color: Color = if (StructuralColors.isLight) {
        StructuralColors.foreground.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.primary
    },
) {
    FrText(
        text = text,
        modifier = modifier,
        color = color,
        style = StructuralType.eyebrow,
    )
}

/**
 * Structural microscopic metadata array — a single dense row of 10sp uppercase facts (author · slot ·
 * time · votes) joined by faint 3dp dot separators, no borders or dividers. Extreme-contrast pairing
 * for the oversized metrics. Callers pass already-uppercased items; empty lists render nothing.
 */
@Composable
fun FrMicroRow(
    items: List<String>,
    modifier: Modifier = Modifier,
    color: Color = StructuralColors.foreground.copy(alpha = 0.72f),
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.4f)),
                )
            }
            FrText(
                text = item,
                color = color,
                style = StructuralType.micro,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@FrPreview
@Composable
private fun FrMicroPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FrEyebrow("TODAY - SATURDAY BRUNCH")
                FrMicroRow(listOf("ANIKA", "LUNCH", "12:30", "4 VOTES"))
            }
        }
    }
}
