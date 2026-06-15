package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrBadge
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.CollectedIngredient
import es.schsebastian.foodrats.core.domain.meal.IngredientBingo
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The ingredient-bingo section (roadmap §2.3 — the "Pokédex"): every catalog ingredient as a
 * collected (vivid) or locked (dimmed) badge cell, with a `collected / total` progress header
 * ("142 / 226"). Domain-aware (it takes an [IngredientBingo]) so it lives in the feature, not
 * `:core:designsystem` — same rule as `FrMealCard` / `FrCuisinePassport`. Each cell reuses the pure
 * [FrBadge] atom; nothing new lands in the design system, so no catalog entry is required (the
 * feature-owned-Fr* carve-out).
 *
 * An ingredient cell is binary (collected or not), so the badge progress ring is full (earned) or
 * empty (locked) — the partial ring is for tiered count criteria, not a single collectible. The
 * grid is a `FlowRow` over the catalog order; category sectioning (a §2.3 nice-to-have) is deferred
 * — the flat grid scrolls fine for the ~226-cell catalog inside the Stats `LazyColumn`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FrIngredientBingo(
    bingo: IngredientBingo,
    modifier: Modifier = Modifier,
) {
    val celebration = LocalFrSemanticColors.current.celebration
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrText(
            text = resolve(StatsStringKey.BingoTitle),
            style = MaterialTheme.typography.titleSmall,
        )
        FrText(
            text = resolve(
                StatsStringKey.BingoProgressFormat,
                bingo.collectedCount,
                bingo.totalCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            bingo.cells.forEach { cell ->
                FrBadge(
                    icon = FrIcons.Restaurant,
                    title = cell.ingredient.displayName,
                    earned = cell.collected,
                    progressFraction = if (cell.collected) 1f else 0f,
                    tint = celebration,
                    caption = cell.caption(),
                    contentDescription = cell.ingredient.displayName,
                )
            }
        }
    }
}

@Composable
private fun CollectedIngredient.caption(): String =
    firstCollectedAt?.let { resolve(StatsStringKey.BingoCollectedOnFormat, formatBingoDate(it)) }
        ?: resolve(StatsStringKey.BingoLockedLabel)

/**
 * ISO `yyyy-MM-dd` in the device zone — same rationale as `FrCuisinePassport.formatPassportDate`:
 * CLDR month names aren't uniformly available in commonMain, ISO renders identically on both
 * platforms; the "Caught %1$s" wrapper is localized.
 */
private fun formatBingoDate(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    @Suppress("DEPRECATION")
    val month = date.monthNumber.toString().padStart(2, '0')
    @Suppress("DEPRECATION")
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}-$month-$day"
}
