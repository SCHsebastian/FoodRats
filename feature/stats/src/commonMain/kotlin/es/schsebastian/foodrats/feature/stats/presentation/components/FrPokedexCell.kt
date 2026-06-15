package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.CollectedIngredient
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

private val SpecimenDisc = 56.dp

/**
 * One pokédex specimen cell — the "new way of watching" the ingredient collection, deliberately
 * distinct from the food-passport flag tiles. Each cell carries a **dex number** (`#NNN`) and a
 * circular specimen disc:
 *
 * - **Caught** → a celebration-tinted disc showing the ingredient's monogram, its real name, and the
 *   caught date.
 * - **Locked** → a dim disc with a "?" silhouette and a "???" name (the classic dex reveal mechanic:
 *   you see the slot exists but not what fills it until you catch it).
 *
 * Domain-aware (it takes a [CollectedIngredient]), so it lives in the feature, not
 * `:core:designsystem`. [index] is the 1-based catalog position (the stable dex number).
 */
@Composable
internal fun FrPokedexCell(
    cell: CollectedIngredient,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val collected = cell.collected
    val celebration = LocalFrSemanticColors.current.celebration
    val discColor = if (collected) celebration else MaterialTheme.colorScheme.surfaceVariant
    val paddedIndex = index.toString().padStart(3, '0')

    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrText(
            text = resolve(StatsStringKey.BingoIndexFormat, paddedIndex),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.size(SpecimenDisc),
            shape = CircleShape,
            color = discColor,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (collected) {
                    FrText(
                        text = cell.ingredient.displayName.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                } else {
                    FrText(
                        text = resolve(StatsStringKey.BingoMysteryGlyph),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
        FrText(
            text = if (collected) cell.ingredient.displayName else resolve(StatsStringKey.BingoMysteryName),
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
private fun CollectedIngredient.caption(): String =
    firstCollectedAt?.let { resolve(StatsStringKey.BingoCollectedOnFormat, formatCollectionDate(it)) }
        ?: resolve(StatsStringKey.BingoLockedLabel)
