package es.schsebastian.foodrats.feature.feed.presentation.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrUploadProgressBar
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrFeedLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import es.schsebastian.foodrats.feature.feed.presentation.components.FrFeedDayHeader
import es.schsebastian.foodrats.feature.feed.presentation.components.FrFeedMealRow
import es.schsebastian.foodrats.feature.feed.presentation.components.FrUploadQueueBar
import es.schsebastian.foodrats.feature.feed.presentation.toStringKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FeedScreen(
    onPickCrewClick: () -> Unit,
    onMealClick: (mealId: String, dayIso: String) -> Unit,
    vm: FeedViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    FrScreenScaffold(contentWindowInsets = WindowInsets(0)) {
        // FrFeedLayout slots are `dayHeader` and `list` (not header/body per plan).
        FrFeedLayout(
            dayHeader = {
                val date = state.day?.day?.date
                val today = state.today
                val iso = date?.toString().orEmpty()
                val isToday = date != null && date == today
                val isYesterday = date != null && today != null &&
                    date == today.minus(DatePeriod(days = 1))
                val primary = when {
                    isToday -> resolve(FeedStringKey.Title)
                    isYesterday -> resolve(FeedStringKey.Yesterday)
                    else -> iso
                }
                // Secondary date line only when the primary is a relative word.
                val secondary = if (isToday || isYesterday) iso else ""
                Column(modifier = Modifier.fillMaxWidth()) {
                    FrUploadProgressBar(visible = state.isUploadActive)
                    // Offline-first publish queue indicator (roadmap §5.2): pending +
                    // terminal-failed counts with retry/dismiss. Hides itself when empty.
                    FrUploadQueueBar(
                        pending = state.queuedPending,
                        failed = state.queuedFailed,
                        onRetry = { vm.onIntent(FeedIntent.RetryQueuedDrafts) },
                        onDismiss = { vm.onIntent(FeedIntent.DismissQueuedDrafts) },
                    )
                    FrFeedDayHeader(
                        primaryLabel = primary,
                        secondaryLabel = secondary,
                        sortKey = iso,
                        canGoPrev = state.canGoPrev,
                        canGoNext = state.canGoNext,
                        onPrev = { vm.onIntent(FeedIntent.PrevDay) },
                        onNext = { vm.onIntent(FeedIntent.NextDay) },
                    )
                }
            },
            list = {
                Column(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.error is FeedError.Session.NoActiveCrew -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                FrEmptyState(
                                    icon = FrIcons.Group,
                                    headline = resolve(FeedStringKey.NoActiveCrewHeadline),
                                    subtext = resolve(FeedStringKey.NoActiveCrewSubtext),
                                    cta = {
                                        FrButton(
                                            label = resolve(FeedStringKey.PickCrewCta),
                                            onClick = onPickCrewClick,
                                            variant = FrButtonVariant.Primary,
                                        )
                                    },
                                )
                            }
                        }
                        state.isLoading && state.meals.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                FrProgressIndicator()
                            }
                        }
                        state.meals.isEmpty() && state.error == null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                FrEmptyState(
                                    icon = FrIcons.GalleryImport,
                                    headline = resolve(FeedStringKey.EmptyHeadline),
                                    subtext = resolve(FeedStringKey.EmptySubtext),
                                )
                            }
                        }
                        else -> {
                            val dayIso = state.day?.day?.date?.toString().orEmpty()
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    horizontal = Spacing.md,
                                    vertical = Spacing.sm,
                                ),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                item(key = "plates-count") {
                                    FrText(
                                        text = resolve(FeedStringKey.PlatesCount, state.meals.size),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = Spacing.xs),
                                    )
                                }
                                items(state.meals, key = { it.mealId }) { ui ->
                                    FrFeedMealRow(
                                        ui = ui,
                                        onClick = { onMealClick(ui.mealId, dayIso) },
                                        onReact = { vm.onIntent(FeedIntent.ReactMeal(ui.mealId)) },
                                        modifier = Modifier.animateItem(),
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
                    state.reactError?.let { err ->
                        FrErrorBanner(text = resolve(err.toStringKey()))
                    }
                }
            },
        )
    }
}
