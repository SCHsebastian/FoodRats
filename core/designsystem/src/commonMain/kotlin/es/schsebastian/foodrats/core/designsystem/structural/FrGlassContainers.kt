package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.tokens.Radius

/**
 * The frosted **bottom sheet** stratum — zero-chrome, top-rounded, translucent over whatever the
 * caller floats it on (the caller owns the scrim + bottom anchoring). It's read by the `#1c1d16@88%`
 * [StructuralColors.sheet] tint, an upward drop shadow, a 1px inner top-light (the glass edge-catch,
 * never a box outline), and an optional centered grab handle.
 *
 * @param showGrabHandle draws the 40×5dp pill affordance at the top edge.
 */
@Composable
fun FrGlassSheet(
    modifier: Modifier = Modifier,
    showGrabHandle: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val isLight = StructuralColors.isLight
    Column(
        modifier = modifier
            // Upward shadow: a negative Y offset would clip; a large blur reads as a soft lift. Light
            // mode trims the elevation (a 34dp black shadow is a heavy halo on the warm floor — user
            // report 2026-06-23) and pins the fill opaque so the shadow never double-edges.
            .shadow(if (isLight) 16.dp else 34.dp, shape, clip = false)
            .clip(shape)
            .background(if (isLight) StructuralColors.sheet.copy(alpha = 1f) else StructuralColors.sheet, shape)
            .frTopLightEdge(),
    ) {
        if (showGrabHandle) {
            // The handle was `foreground` @22% (≈1.4:1) — invisible on the warm-white light sheet; raise
            // the alpha in light so the grab affordance stays visible.
            val handleAlpha = if (StructuralColors.isLight) 0.40f else 0.22f
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(StructuralColors.foreground.copy(alpha = handleAlpha)),
            )
        }
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * The frosted **centered dialog** stratum — a 300dp-wide floating card. Zero-chrome: read by the
 * `#1d1e17@92%` [StructuralColors.dialog] tint, a large drop shadow, and a 1px inner top-light
 * (never a box outline). The caller owns the dimming scrim + centering; this is just the glass card.
 * Children stack with a 12dp gap.
 */
@Composable
fun FrGlassDialog(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val isLight = StructuralColors.isLight
    Column(
        modifier = modifier
            .width(300.dp)
            // Light mode trims the 40dp halo and pins the fill opaque (see FrGlassSheet — user report
            // 2026-06-23: a shadow over a translucent fill double-edges on the warm floor).
            .shadow(if (isLight) 18.dp else 40.dp, shape, clip = false)
            .clip(shape)
            .background(if (isLight) StructuralColors.dialog.copy(alpha = 1f) else StructuralColors.dialog, shape)
            .frTopLightEdge()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@FrPreview
@Composable
private fun FrGlassContainersPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                FrGlassDialog {
                    FrText(
                        "Delete this meal?",
                        style = StructuralType.titleMd,
                        color = StructuralColors.foreground,
                    )
                    FrText("This cannot be undone.", style = StructuralType.body)
                }
                FrGlassSheet {
                    FrText("Report", color = StructuralColors.foreground)
                    FrText("Block", color = StructuralColors.foreground)
                }
            }
        }
    }
}
