package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.CollectedIngredient
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

private val SpecimenDisc = 56.dp

/**
 * Structural pokédex specimen cell — the collectible "watched" as a zero-chrome disc. Each cell carries
 * a **dex number** (`#NNN`) and a circular specimen disc over the media floor:
 *
 * - **Caught** → a celebration-tinted disc showing the ingredient's monogram + real name + caught date.
 * - **Locked** → a near-transparent frosted disc with a "?" silhouette and a "???" name (the classic
 *   dex reveal mechanic: the slot exists, but not what fills it).
 *
 * Domain-aware (takes a [CollectedIngredient]). [index] is the 1-based catalog position (dex number).
 */
@Composable
internal fun FrPokedexCell(
    cell: CollectedIngredient,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val collected = cell.collected
    val semantic = LocalFrSemanticColors.current
    // Locked disc was `foreground` @10% — a near-invisible smear on the warm-white light floor; raise
    // it in light so the dex slot still reads as a frosted disc. Dark is unchanged.
    val lockedDiscAlpha = if (StructuralColors.isLight) 0.16f else 0.10f
    val discColor: Color = if (collected) semantic.celebration else StructuralColors.foreground.copy(alpha = lockedDiscAlpha)
    val paddedIndex = index.toString().padStart(3, '0')

    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrText(
            text = resolve(StatsStringKey.BingoIndexFormat, paddedIndex),
            style = StructuralType.microMono,
            color = StructuralColors.foreground.copy(alpha = 0.6f),
        )
        Box(
            modifier = Modifier.size(SpecimenDisc).clip(CircleShape).background(discColor),
            contentAlignment = Alignment.Center,
        ) {
            if (collected) {
                FrText(
                    text = cell.ingredient.displayName.trim().take(1).uppercase(),
                    style = StructuralType.titleLg,
                    color = semantic.onCelebration,
                )
            } else {
                FrText(
                    text = resolve(StatsStringKey.BingoMysteryGlyph),
                    style = StructuralType.titleLg,
                    color = StructuralColors.foreground.copy(alpha = 0.45f),
                )
            }
        }
        FrText(
            text = if (collected) cell.ingredient.displayName else resolve(StatsStringKey.BingoMysteryName),
            style = StructuralType.body.copy(textAlign = TextAlign.Center),
            color = if (collected) StructuralColors.foreground else StructuralColors.foreground.copy(alpha = 0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Two-line caption slot — the collected caption ("Cazado 22/06") is wider than the narrow 4-up
        // cell (the cell fits ~9 chars on one line), so a single line clipped mid-word. Fixed height so
        // every cell stays aligned regardless of caught/locked.
        val captionSlotHeight = rememberCaptionSlotHeight()
        Box(
            modifier = Modifier.fillMaxWidth().height(captionSlotHeight),
            contentAlignment = Alignment.TopCenter,
        ) {
            FrText(
                text = cell.caption(),
                style = StructuralType.micro.copy(textAlign = TextAlign.Center),
                color = captionTextColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CollectedIngredient.caption(): String =
    firstCollectedAt?.let { resolve(StatsStringKey.BingoCollectedOnFormat, formatCollectionDate(it)) }
        ?: resolve(StatsStringKey.BingoLockedLabel)
