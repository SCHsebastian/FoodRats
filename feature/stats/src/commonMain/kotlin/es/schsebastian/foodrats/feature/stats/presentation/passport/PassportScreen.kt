package es.schsebastian.foodrats.feature.stats.presentation.passport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
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

/** Space reserved so the last row clears the floating dock. */
private val DOCK_CLEARANCE = 104.dp

/**
 * Structural "Passport" tab: the cuisine passport (country-flag tiles) + the ingredient pokédex over a
 * warm Iron & Ember media floor, rendered as a single [LazyVerticalGrid] so cells compose/dispose as
 * they scroll. Zero-chrome: oversized "Collection" title, olive section eyebrows, frosted specimen
 * cells. Reads the same [StatsViewModel] snapshot (no new read path).
 */
@Composable
fun PassportScreen(vm: StatsViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val error = state.error
    Box(modifier = Modifier.fillMaxSize()) {
        FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.Soft, scrim = FrScrimStyle.Even)
        when {
            snapshot == null && error == null -> LoadingSkeleton()
            error != null -> Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(Spacing.lg)) {
                FrText(
                    text = resolve(error.toStringKey()),
                    style = StructuralType.body,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                val passport = snapshot?.cuisinePassport?.takeIf { it.totalCount > 0 }
                val bingo = snapshot?.ingredientBingo?.takeIf { it.totalCount > 0 }
                if (passport == null && bingo == null) {
                    EmptyState()
                } else {
                    CollectionGrid(passport = passport, bingo = bingo)
                }
            }
        }
    }
}

@Composable
private fun CollectionGrid(passport: CuisinePassport?, bingo: IngredientBingo?) {
    // Stable 1-based dex number per ingredient (catalog order), assigned BEFORE grouping. Memoized on
    // the bingo reference (the screen reads the shared StatsViewModel state, so it recomposes on
    // unrelated changes — without this the ~200-element groupBy would re-run each time).
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
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.lg, end = Spacing.lg, bottom = DOCK_CLEARANCE),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        fullSpan(key = "screen-title") {
            FrText(
                text = resolve(StatsStringKey.CollectionTitle),
                style = StructuralType.titleXl,
                color = StructuralColors.foreground,
            )
        }

        if (passport != null) {
            fullSpan(key = "passport-header") {
                SectionHeader(
                    title = resolve(StatsStringKey.PassportTitle),
                    progress = resolve(StatsStringKey.PassportProgressFormat, passport.collectedCount, passport.totalCount),
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
                    progress = resolve(StatsStringKey.BingoProgressFormat, bingo.collectedCount, bingo.totalCount),
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
                    FrPokedexCell(cell = specimen.cell, index = specimen.index, modifier = Modifier.stampIn(order = index))
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
 * Bespoke "stamp landing" entrance — each collectible tilts up from a back-leaning 3-D plane while
 * scaling from 80 % with a soft overshoot and fading in, as if a passport stamp is pressed onto the
 * page. Staggered left-to-right per row.
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
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrEyebrow(text = title.uppercase())
        FrText(text = progress, style = StructuralType.microMono, color = StructuralColors.foreground.copy(alpha = 0.6f))
    }
}

@Composable
private fun CategoryHeader(text: String) {
    FrEyebrow(
        text = text.uppercase(),
        color = StructuralColors.foreground.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
    )
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(Spacing.lg), contentAlignment = androidx.compose.ui.Alignment.Center) {
        FrGlassTile(depth = FrTileDepth.Near) {
            FrText(text = resolve(StatsStringKey.EmptyHeadline), style = StructuralType.titleLg, color = StructuralColors.foreground)
            androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.xs))
            FrText(text = resolve(StatsStringKey.EmptySubtext), style = StructuralType.body, color = StructuralColors.foreground.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.xs))
        FrGlassTile(depth = FrTileDepth.Deep, modifier = Modifier.fillMaxWidth().height(120.dp)) {}
        FrGlassTile(depth = FrTileDepth.Deep, modifier = Modifier.fillMaxWidth().height(220.dp)) {}
    }
}
