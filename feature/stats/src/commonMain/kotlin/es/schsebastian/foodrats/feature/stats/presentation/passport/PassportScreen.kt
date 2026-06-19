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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
    val snapshot = state.snapshot
    val error = state.error
    FrScreenScaffold(contentWindowInsets = WindowInsets(0)) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                snapshot == null && error == null -> LoadingSkeleton()
                error != null -> Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
                    FrErrorBanner(text = resolve(error.toStringKey()))
                }
                else -> {
                    val passport = snapshot?.cuisinePassport?.takeIf { it.totalCount > 0 }
                    val bingo = snapshot?.ingredientBingo?.takeIf { it.totalCount > 0 }
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
    // specimen keeps its number regardless of which category section it lands in. Memoized on the
    // bingo reference: PassportScreen reads the shared StatsViewModel's whole state, so it
    // recomposes on unrelated changes (upload ticks, refresh, leaderboard) — without this the
    // ~200-element groupBy would re-run on every one of those.
    val pokedexByCategory: List<Pair<IngredientCategory, List<IndexedSpecimen>>> =
        remember(bingo) {
            bingo?.cells
                ?.mapIndexed { i, cell -> IndexedSpecimen(i + 1, cell) }
                ?.groupBy { it.cell.ingredient.category }
                ?.toList()
                .orEmpty()
        }

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
            itemsIndexed(
                items = passport.cells,
                key = { _, it -> "cuisine:${it.cuisine.slug.value}" },
            ) { index, cell ->
                FrCuisineFlagCell(cell = cell, modifier = Modifier.stampIn(order = index))
            }
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
                itemsIndexed(
                    items = specimens,
                    key = { _, it -> "ingredient:${it.cell.ingredient.slug.value}" },
                ) { index, specimen ->
                    FrPokedexCell(
                        cell = specimen.cell,
                        index = specimen.index,
                        modifier = Modifier.stampIn(order = index),
                    )
                }
            }
        }
    }
}

private data class IndexedSpecimen(val index: Int, val cell: CollectedIngredient)

private fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/**
 * Bespoke "stamp landing" entrance — deliberately distinct from the achievements badge pop. Each
 * collectible tilts up from a back-leaning 3-D plane ([rotationX]) while scaling from 80 % with a soft
 * overshoot and fading in, as if a passport stamp is being pressed onto the page. `cameraDistance`
 * keeps the perspective shallow so the flip reads without distorting the flag/disc. Staggered
 * left-to-right per row (delay = `order` modulo the column count) and bounded so later cells pop
 * promptly on scroll-in.
 */
@Composable
private fun Modifier.stampIn(order: Int): Modifier {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((order % GRID_COLUMNS) * 55L)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    return graphicsLayer {
        val p = anim.value
        alpha = p.coerceIn(0f, 1f)
        val s = 0.8f + 0.2f * p
        scaleX = s
        scaleY = s
        rotationX = (1f - p.coerceIn(0f, 1f)) * -55f
        cameraDistance = 14f * density
    }
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
