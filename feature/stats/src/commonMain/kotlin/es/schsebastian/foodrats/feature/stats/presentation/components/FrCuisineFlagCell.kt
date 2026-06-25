package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrFlags
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.cuisine.CollectedCuisine
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

private val FlagTile = 56.dp

/**
 * The "locked stamp" desaturation. It's a constant — identical for every dimmed cell — so it lives
 * at file scope instead of a per-cell `remember` slot. `ColorFilter` is plain immutable data.
 */
private val GrayscaleFilter: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Structural food-passport tile: a country flag specimen for the cuisine (roadmap §2.2). Collected →
 * full-colour flag over the media floor; locked → the same flag **desaturated and dimmed** (a
 * saturation-0 [ColorMatrix]) — the "stamp not earned yet" read. Zero-chrome: the flag is a clipped
 * specimen framed by a 1px glass edge-light, never a bordered box. Domain-aware (takes a
 * [CollectedCuisine]), so it lives in the feature. Sized for a 4-up grid cell.
 */
@Composable
internal fun FrCuisineFlagCell(
    cell: CollectedCuisine,
    modifier: Modifier = Modifier,
) {
    val collected = cell.collected
    val flag = FrFlags.forCuisine(cell.cuisine.iconKey)
    val tileShape = RoundedCornerShape(Radius.sm)

    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Image(
            imageVector = flag,
            contentDescription = cell.cuisine.displayName,
            modifier = Modifier
                .size(FlagTile)
                .clip(tileShape)
                // `topLight` (black @8% in light) loses the frosted edge on the warm-white floor; raise
                // the frame alpha in light so each specimen keeps a crisp edge. Dark is unchanged.
                .border(
                    1.dp,
                    StructuralColors.foreground.copy(alpha = if (StructuralColors.isLight) 0.18f else 0.10f),
                    tileShape,
                )
                .alpha(if (collected) 1f else 0.4f),
            contentScale = ContentScale.Crop,
            colorFilter = if (collected) null else GrayscaleFilter,
        )
        // Fixed two-line slot so 1-line and 2-line cuisine names never change tile height (the
        // "Estadounidense" / "De Oriente Medio" overflow bug). Derived from the body line metrics
        // (14 sp × 1.5 em × 2 lines) via the current density so it scales with the user's font size
        // instead of a brittle hardcoded dp that clips a second line at large accessibility scales.
        val nameSlotHeight = with(LocalDensity.current) { (StructuralType.body.fontSize * 1.5f * 2).toDp() }
        Box(
            modifier = Modifier.fillMaxWidth().height(nameSlotHeight),
            contentAlignment = Alignment.TopCenter,
        ) {
            FrText(
                text = cell.cuisine.displayName,
                style = StructuralType.body.copy(textAlign = TextAlign.Center),
                color = if (collected) StructuralColors.foreground else StructuralColors.foreground.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Fixed two-line caption slot. The collected caption ("Conseguida 22/06") is wider than the
        // narrow 4-up cell even after shortening the date — the WORD overflows — so a single line clipped
        // to "Conseguid…". Two lines (in a fixed slot so every cell stays the same height) lets it wrap.
        val captionSlotHeight = with(LocalDensity.current) { (StructuralType.micro.fontSize * 1.3f * 2).toDp() }
        Box(
            modifier = Modifier.fillMaxWidth().height(captionSlotHeight),
            contentAlignment = Alignment.TopCenter,
        ) {
            FrText(
                text = cell.caption(),
                style = StructuralType.micro.copy(textAlign = TextAlign.Center),
                color = StructuralColors.foreground.copy(alpha = if (StructuralColors.isLight) 0.65f else 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CollectedCuisine.caption(): String =
    firstCollectedAt?.let { resolve(StatsStringKey.PassportCollectedOnFormat, formatCollectionDate(it)) }
        ?: resolve(StatsStringKey.PassportLockedLabel)
