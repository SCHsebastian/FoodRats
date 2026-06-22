package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
                .border(1.dp, StructuralColors.topLight, tileShape)
                .alpha(if (collected) 1f else 0.4f),
            contentScale = ContentScale.Crop,
            colorFilter = if (collected) null else GrayscaleFilter,
        )
        FrText(
            text = cell.cuisine.displayName,
            style = StructuralType.body.copy(textAlign = TextAlign.Center),
            color = if (collected) StructuralColors.foreground else StructuralColors.foreground.copy(alpha = 0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FrText(
            text = cell.caption(),
            style = StructuralType.micro.copy(textAlign = TextAlign.Center),
            color = StructuralColors.foreground.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CollectedCuisine.caption(): String =
    firstCollectedAt?.let { resolve(StatsStringKey.PassportCollectedOnFormat, formatCollectionDate(it)) }
        ?: resolve(StatsStringKey.PassportLockedLabel)
