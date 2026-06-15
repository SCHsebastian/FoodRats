package es.schsebastian.foodrats.feature.stats.presentation.passport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.cuisine.CuisinePassport
import es.schsebastian.foodrats.core.domain.meal.CollectedIngredient
import es.schsebastian.foodrats.core.domain.meal.IngredientBingo
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import es.schsebastian.foodrats.feature.stats.presentation.components.FrCuisineFlagCell
import es.schsebastian.foodrats.feature.stats.presentation.components.FrPokedexCell
import es.schsebastian.foodrats.feature.stats.presentation.components.labelStringKey
import es.schsebastian.foodrats.feature.stats.presentation.stats.StatsViewModel
import es.schsebastian.foodrats.feature.stats.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

private const val GRID_COLUMNS = 4

/**
 * "Passport" tab: the cuisine passport (country-flag tiles) + the ingredient pokédex (specimen
 * reveal cells), rendered as a **single [LazyVerticalGrid]** so cells compose/dispose as they scroll
 * instead of all ~240 collectibles staying alive at once (the old `FlowRow`-in-`LazyColumn` cost).
 * Fixed [GRID_COLUMNS]-per-row; full-span items carry the screen title and section/category headers.
 * Reads the same [StatsViewModel] snapshot (no new read path).
 */
@Composable
fun PassportScreen(vm: StatsViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    FrScreenScaffold(contentWindowInsets = WindowInsets(0)) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.snapshot == null && state.error == null -> LoadingSkeleton()
                state.error != null -> Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
                    FrErrorBanner(text = resolve(state.error!!.toStringKey()))
                }
                else -> {
                    val snap = state.snapshot
                    val passport = snap?.cuisinePassport?.takeIf { it.totalCount > 0 }
                    val bingo = snap?.ingredientBingo?.takeIf { it.totalCount > 0 }
                    if (passport == null && bingo == null) {
                        FrEmptyState(
                            icon = FrIcons.Public,
                            headline = resolve(StatsStringKey.EmptyHeadline),
                            subtext = resolve(StatsStringKey.EmptySubtext),
                        )
                    } else {
                        CollectionGrid(passport = passport, bingo = bingo)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionGrid(passport: CuisinePassport?, bingo: IngredientBingo?) {
    // Stable 1-based dex number per ingredient (catalog order), assigned BEFORE grouping so a
    // specimen keeps its number regardless of which category section it lands in.
    val pokedexByCategory: List<Pair<IngredientCategory, List<IndexedSpecimen>>> =
        bingo?.cells
            ?.mapIndexed { i, cell -> IndexedSpecimen(i + 1, cell) }
            ?.groupBy { it.cell.ingredient.category }
            ?.toList()
            .orEmpty()

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        fullSpan(key = "screen-title") {
            FrText(
                text = resolve(StatsStringKey.CollectionTitle),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        if (passport != null) {
            fullSpan(key = "passport-header") {
                SectionHeader(
                    title = resolve(StatsStringKey.PassportTitle),
                    progress = resolve(
                        StatsStringKey.PassportProgressFormat,
                        passport.collectedCount,
                        passport.totalCount,
                    ),
                )
            }
            items(
                items = passport.cells,
                key = { "cuisine:${it.cuisine.slug.value}" },
            ) { cell -> FrCuisineFlagCell(cell = cell) }
        }

        if (bingo != null) {
            fullSpan(key = "pokedex-header") {
                SectionHeader(
                    title = resolve(StatsStringKey.BingoTitle),
                    progress = resolve(
                        StatsStringKey.BingoProgressFormat,
                        bingo.collectedCount,
                        bingo.totalCount,
                    ),
                )
            }
            pokedexByCategory.forEach { (category, specimens) ->
                fullSpan(key = "cat:${category.labelStringKey().name}") {
                    CategoryHeader(resolve(category.labelStringKey()))
                }
                items(
                    items = specimens,
                    key = { "ingredient:${it.cell.ingredient.slug.value}" },
                ) { specimen -> FrPokedexCell(cell = specimen.cell, index = specimen.index) }
            }
        }
    }
}

private data class IndexedSpecimen(val index: Int, val cell: CollectedIngredient)

private fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionHeader(title: String, progress: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrText(text = title, style = MaterialTheme.typography.titleMedium)
        FrText(
            text = progress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryHeader(text: String) {
    FrText(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
    )
}

@Composable
private fun LoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(Radius.lg),
        )
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            shape = RoundedCornerShape(Radius.lg),
        )
    }
}
