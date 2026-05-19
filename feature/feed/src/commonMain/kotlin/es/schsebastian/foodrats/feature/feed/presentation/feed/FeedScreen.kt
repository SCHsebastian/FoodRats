package es.schsebastian.foodrats.feature.feed.presentation.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrFeedLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import es.schsebastian.foodrats.feature.feed.presentation.components.FrFeedDayHeader
import es.schsebastian.foodrats.feature.feed.presentation.components.FrFeedMealCard
import es.schsebastian.foodrats.feature.feed.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FeedScreen(
    onPickCrewClick: () -> Unit,
    vm: FeedViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    FrScreenScaffold {
        // FrFeedLayout slots are `dayHeader` and `list` (not header/body per plan).
        FrFeedLayout(
            dayHeader = {
                FrFeedDayHeader(
                    label = state.day?.day?.toKey() ?: "",
                    canGoPrev = state.canGoPrev,
                    canGoNext = state.canGoNext,
                    onPrev = { vm.onIntent(FeedIntent.PrevDay) },
                    onNext = { vm.onIntent(FeedIntent.NextDay) },
                )
            },
            list = {
                Column(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.error is FeedError.Session.NoActiveCrew -> {
                            // FrEmptyState requires non-null ImageVector; use Settings icon as placeholder.
                            FrEmptyState(
                                icon = FrIcons.Settings,
                                headline = resolve(FeedStringKey.NoActiveCrewHeadline),
                                subtext = resolve(FeedStringKey.NoActiveCrewSubtext),
                                cta = {
                                    FrButton(
                                        label = resolve(FeedStringKey.NoActiveCrewHeadline),
                                        onClick = onPickCrewClick,
                                        variant = FrButtonVariant.Primary,
                                    )
                                },
                            )
                        }
                        state.isLoading && state.meals.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                FrProgressIndicator()
                            }
                        }
                        state.meals.isEmpty() && state.error == null -> {
                            FrEmptyState(
                                icon = FrIcons.GalleryImport,
                                headline = resolve(FeedStringKey.EmptyHeadline),
                                subtext = resolve(FeedStringKey.EmptySubtext),
                            )
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.meals, key = { it.mealId }) { ui ->
                                    FrFeedMealCard(
                                        ui = ui,
                                        onRate = { mealId, score -> vm.onIntent(FeedIntent.RateMeal(mealId, score)) },
                                    )
                                }
                            }
                        }
                    }
                    state.error?.let { err ->
                        if (err !is FeedError.Session.NoActiveCrew) {
                            FrErrorBanner(text = resolve(err.toStringKey()))
                        }
                    }
                    state.rateError?.let { err ->
                        FrErrorBanner(text = resolve(err.toStringKey()))
                    }
                }
            },
        )
    }
}
