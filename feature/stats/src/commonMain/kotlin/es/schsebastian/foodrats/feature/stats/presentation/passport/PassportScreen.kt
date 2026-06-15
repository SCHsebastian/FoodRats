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
import androidx.compose.foundation.lazy.LazyColumn
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
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import es.schsebastian.foodrats.feature.stats.presentation.components.FrCuisinePassport
import es.schsebastian.foodrats.feature.stats.presentation.components.FrIngredientBingo
import es.schsebastian.foodrats.feature.stats.presentation.stats.StatsViewModel
import es.schsebastian.foodrats.feature.stats.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Passport" tab: the two collection grids (cuisine passport + ingredient bingo) split out of the
 * Stats screen — they each render a full-width badge grid that otherwise crowded the competitive
 * stats below them. Reads the same [StatsViewModel] snapshot (no new read path); the collections
 * are derived alongside the rest of the stats and just live in their own tab now.
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            item {
                                FrText(
                                    text = resolve(StatsStringKey.PassportTitle),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                            passport?.let { item { FrCuisinePassport(passport = it) } }
                            bingo?.let { item { FrIngredientBingo(bingo = it) } }
                        }
                    }
                }
            }
        }
    }
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
