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
import es.schsebastian.foodrats.core.domain.cuisine.CollectedCuisine
import es.schsebastian.foodrats.core.domain.cuisine.CuisinePassport
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The cuisine-passport section (roadmap §2.2): every catalog cuisine as a collected (vivid) or
 * locked (dimmed) badge cell, with a `collected / total` progress header. Domain-aware (it takes a
 * [CuisinePassport]) so it lives in the feature, not `:core:designsystem` — same rule as `FrMealCard`.
 * Each cell reuses the pure [FrBadge] atom; nothing new lands in the design system, so no catalog
 * entry is required (the feature-owned-Fr* carve-out).
 *
 * A cuisine cell is binary (collected or not), so the badge progress ring is full (earned) or empty
 * (locked) — the "almost there" partial ring is for tiered count criteria, not a single collectible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FrCuisinePassport(
    passport: CuisinePassport,
    modifier: Modifier = Modifier,
) {
    val celebration = LocalFrSemanticColors.current.celebration
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrText(
            text = resolve(StatsStringKey.PassportTitle),
            style = MaterialTheme.typography.titleSmall,
        )
        FrText(
            text = resolve(
                StatsStringKey.PassportProgressFormat,
                passport.collectedCount,
                passport.totalCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            passport.cells.forEach { cell ->
                FrBadge(
                    icon = FrIcons.Public,
                    title = cell.cuisine.displayName,
                    earned = cell.collected,
                    progressFraction = if (cell.collected) 1f else 0f,
                    tint = celebration,
                    caption = cell.caption(),
                    contentDescription = cell.cuisine.displayName,
                )
            }
        }
    }
}

@Composable
private fun CollectedCuisine.caption(): String =
    firstCollectedAt?.let { resolve(StatsStringKey.PassportCollectedOnFormat, formatPassportDate(it)) }
        ?: resolve(StatsStringKey.PassportLockedLabel)

/**
 * ISO `yyyy-MM-dd` in the device zone — same rationale as the achievements `formatEpochDay`:
 * CLDR month names aren't uniformly available in commonMain, ISO renders identically on both
 * platforms; the "Collected %1$s" wrapper is localized.
 */
private fun formatPassportDate(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    @Suppress("DEPRECATION")
    val month = date.monthNumber.toString().padStart(2, '0')
    @Suppress("DEPRECATION")
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}-$month-$day"
}
