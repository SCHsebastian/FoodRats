package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrFlags
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.cuisine.CollectedCuisine
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

private val FlagTile = 56.dp

/**
 * One food-passport tile: a country flag for the cuisine (roadmap §2.2). Collected → full-colour
 * flag; locked → the same flag **desaturated and dimmed** (a saturation-0 [ColorMatrix]) under a
 * "Locked" caption — the "stamp not earned yet" read. Domain-aware (it takes a [CollectedCuisine]),
 * so it lives in the feature, not `:core:designsystem`, like `FrMealCard`. Sized for a 4-up
 * [androidx.compose.foundation.lazy.grid.LazyVerticalGrid] cell.
 */
@Composable
internal fun FrCuisineFlagCell(
    cell: CollectedCuisine,
    modifier: Modifier = Modifier,
) {
    val collected = cell.collected
    val flag = FrFlags.forCuisine(cell.cuisine.iconKey)
    val tileShape = RoundedCornerShape(Radius.sm)
    val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

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
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, tileShape),
            contentScale = ContentScale.Crop,
            alpha = if (collected) 1f else 0.35f,
            colorFilter = if (collected) null else grayscale,
        )
        FrText(
            text = cell.cuisine.displayName,
            style = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center),
            color = if (collected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FrText(
            text = cell.caption(),
            style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CollectedCuisine.caption(): String =
    firstCollectedAt?.let { resolve(StatsStringKey.PassportCollectedOnFormat, formatCollectionDate(it)) }
        ?: resolve(StatsStringKey.PassportLockedLabel)
