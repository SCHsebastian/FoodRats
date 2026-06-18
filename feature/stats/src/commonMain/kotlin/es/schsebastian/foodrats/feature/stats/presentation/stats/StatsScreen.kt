package es.schsebastian.foodrats.feature.stats.presentation.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrUploadProgressBar
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.ShareCardStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import es.schsebastian.foodrats.feature.stats.domain.model.WindowStats
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import es.schsebastian.foodrats.feature.stats.presentation.components.FrBestPlatePodium
import es.schsebastian.foodrats.feature.stats.presentation.components.FrCookAwardCard
import es.schsebastian.foodrats.feature.stats.presentation.components.FrMemberIngredientRow
import es.schsebastian.foodrats.feature.stats.presentation.components.FrMostUsedIngredientCard
import es.schsebastian.foodrats.feature.stats.presentation.components.FrMostVotedPlate
import es.schsebastian.foodrats.feature.stats.presentation.components.FrRoastCard
import es.schsebastian.foodrats.feature.stats.presentation.components.FrStatsTabRow
import es.schsebastian.foodrats.feature.stats.presentation.components.FrStreakHeroCard
import es.schsebastian.foodrats.feature.stats.presentation.components.FrTodayStripe
import es.schsebastian.foodrats.feature.stats.presentation.components.FrWindowSummaryCard
import es.schsebastian.foodrats.feature.stats.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StatsScreen(
    vm: StatsViewModel = koinViewModel(),
    onOpenRecap: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    FrScreenScaffold(contentWindowInsets = WindowInsets(0)) {
        Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            FrUploadProgressBar(visible = state.isUploadActive)
            when {
                state.snapshot == null && state.error == null -> LoadingSkeleton()
                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
                        FrErrorBanner(text = resolve(state.error!!.toStringKey()))
                    }
                }
                state.snapshot != null -> StatsContent(
                    state = state,
                    onSelectTab = { vm.onIntent(StatsIntent.SelectTab(it)) },
                    onOpenRecap = onOpenRecap,
                    onShareStreak = { vm.onIntent(StatsIntent.ShareStreakTapped) },
                    onShareAward = { mealId -> vm.onIntent(StatsIntent.ShareAwardTapped(mealId)) },
                )
                else -> FrEmptyState(
                    icon = FrIcons.Stats,
                    headline = resolve(StatsStringKey.EmptyHeadline),
                    subtext = resolve(StatsStringKey.EmptySubtext),
                )
            }
        }
        // Share-outcome toast (spec §10).
        state.shareOutcome?.let { outcome ->
            val message = resolve(
                when (outcome) {
                    ShareOutcomeUi.Succeeded  -> ShareCardStringKey.ShareSucceeded
                    ShareOutcomeUi.OpenedSheet -> ShareCardStringKey.ShareOpenedSheet
                    ShareOutcomeUi.Failed     -> ShareCardStringKey.ShareFailed
                },
            )
            ShareOutcomeToast(
                message = message,
                onDismiss = { vm.onIntent(StatsIntent.DismissShareOutcome) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        }
    }
}

/** Brief auto-dismissing bottom toast for a share outcome (spec §10). */
@Composable
private fun ShareOutcomeToast(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.runtime.LaunchedEffect(message) {
        kotlinx.coroutines.delay(2500)
        onDismiss()
    }
    Box(modifier = modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.BottomCenter) {
        es.schsebastian.foodrats.core.designsystem.atoms.FrCard {
            FrText(text = message, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatsContent(
    state: StatsState,
    onSelectTab: (Tab) -> Unit,
    onOpenRecap: () -> Unit,
    onShareStreak: () -> Unit,
    onShareAward: (String) -> Unit,
) {
    val snap = state.snapshot!!
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Tab-root header. The crew-name title from the mock needs a cross-context crew read
        // that stats (a MealReadPort consumer) doesn't have, so only the section title shows.
        item {
            FrText(
                text = resolve(StatsStringKey.Title),
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            )
        }
        // In-app entry into the weekly-recap story (roadmap §2.4) — opens the swipeable player for
        // the current week. The notification deep link reaches the same player via Route.WeeklyStory.
        item {
            FrButton(
                label = resolve(StatsStringKey.WeeklyRecapCta),
                onClick = onOpenRecap,
                variant = FrButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { FrStreakHeroCard(hero = snap.hero) }
        // Share the personal streak to Stories (spec §8.2). Shown only when there is a streak worth
        // celebrating; the button shows a spinner while the card rasterizes.
        if (snap.hero.personalStreak.days > 0) {
            item {
                FrButton(
                    label = resolve(StatsStringKey.ShareAward),
                    onClick = onShareStreak,
                    variant = FrButtonVariant.Secondary,
                    enabled = !state.isPreparingShare,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { FrTodayStripe(hero = snap.hero) }
        // The cuisine passport + ingredient bingo collection grids moved to their own "Passport" tab
        // (they each render a full-width badge grid that crowded the competitive stats below).
        stickyHeader {
            FrStatsTabRow(selected = state.selectedTab, onSelect = onSelectTab)
        }
        item {
            AnimatedContent(
                targetState = state.selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val direction = if (forward) 1 else -1
                    (slideInHorizontally { it * direction } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it * direction } + fadeOut())
                },
                label = "tabContent",
            ) { tab ->
                val window: WindowStats? = when (tab) {
                    Tab.Week     -> snap.week
                    Tab.Month    -> snap.month
                    Tab.Historic -> snap.historic
                }
                if (window == null) {
                    val historicError = state.historicError
                    if (tab == Tab.Historic && historicError != null) {
                        Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
                            FrErrorBanner(text = resolve(historicError.toStringKey()))
                        }
                    } else {
                        HistoricLoading()
                    }
                } else {
                    TabBody(
                        window = window,
                        sharePreparing = state.isPreparingShare,
                        onShareAward = onShareAward,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBody(
    window: WindowStats,
    sharePreparing: Boolean,
    onShareAward: (String) -> Unit,
) {
    if (window.totalMeals == 0) {
        FrEmptyState(
            icon = FrIcons.Stats,
            headline = resolve(emptyKeyFor(window.window.tab)),
            subtext = resolve(StatsStringKey.EmptySubtext),
        )
        return
    }
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrWindowSummaryCard(window = window)
        window.bestMeal?.let { award ->
            FrBestPlatePodium(award = award)
            // Share the best plate to Stories (spec §8.2).
            FrButton(
                label = resolve(StatsStringKey.ShareAward),
                onClick = { onShareAward(award.mealId.value) },
                variant = FrButtonVariant.Secondary,
                enabled = !sharePreparing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        window.mostVotedMeal?.let { FrMostVotedPlate(award = it) }
        if (window.bestCook != null || window.mostProlific != null) {
            FrText(
                text = resolve(StatsStringKey.CooksSectionTitle),
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            )
            window.bestCook?.let { FrCookAwardCard(award = it) }
            window.mostProlific?.let { FrCookAwardCard(award = it) }
        }
        window.mostCriticized?.let {
            FrText(
                text = resolve(StatsStringKey.RoastSectionTitle),
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            )
            FrRoastCard(award = it)
        }
        window.mostUsedIngredient?.let { FrMostUsedIngredientCard(usage = it) }
        if (window.topByMember.isNotEmpty()) {
            FrText(
                text = resolve(StatsStringKey.TopIngredientByMemberTitle),
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            )
            window.topByMember.forEach { FrMemberIngredientRow(member = it) }
        }
    }
}

@Composable
private fun HistoricLoading() {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(Radius.lg),
        )
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.6f),
            shape = RoundedCornerShape(Radius.lg),
        )
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(Radius.md),
        )
    }
}

@Composable
private fun LoadingSkeleton() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(Radius.lg),
        )
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            shape = RoundedCornerShape(Radius.lg),
        )
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().height(Sizes.touchTarget),
            shape = RoundedCornerShape(Radius.sm),
        )
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.6f),
            shape = RoundedCornerShape(Radius.lg),
        )
    }
}

private fun emptyKeyFor(tab: Tab): StatsStringKey = when (tab) {
    Tab.Week     -> StatsStringKey.WindowEmptyWeek
    Tab.Month    -> StatsStringKey.WindowEmptyMonth
    Tab.Historic -> StatsStringKey.WindowEmptyHistoric
}
