package es.schsebastian.foodrats.feature.stats.presentation.stats

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.data.share.StoryShareController
import es.schsebastian.foodrats.core.data.share.StoryShareOutcome
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreStyle
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.stats.domain.model.MealAward
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
import es.schsebastian.foodrats.feature.stats.presentation.components.AwardShareCardContent
import es.schsebastian.foodrats.feature.stats.presentation.components.StreakShareCardContent
import es.schsebastian.foodrats.feature.stats.presentation.components.toAwardCard
import es.schsebastian.foodrats.feature.stats.presentation.components.toStreakCard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

class StatsViewModel(
    observeStats: ObserveStatsUseCase,
    uploadProgress: MealUploadProgressPort,
    private val storyShareController: StoryShareController,
    private val clock: Clock,
    private val zone: TimeZone,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<StatsState, StatsIntent, StatsEffect>(StatsState()) {

    /** Leaderboard tabs (Month/Historic) already announced this VM lifetime; gates duplicate fires. */
    private val leaderboardTabsViewed = mutableSetOf<Tab>()

    init {
        // The streak hero is the landing surface (default Week tab) — one view per VM lifetime.
        analytics.track(AnalyticsEvent.StreakViewed)

        uploadProgress.status
            .map { it is MealUploadStatus.Uploading }
            .distinctUntilChanged()
            .onEach { active -> update { it.copy(isUploadActive = active) } }
            .launchIn(viewModelScope)
        // Sticky historic toggle: once the user opens the Historic tab, the historic
        // observer stays subscribed for the VM lifetime so re-visits don't refetch.
        val historicEnabledFlow = state
            .map { it.selectedTab == Tab.Historic }
            .scan(false) { acc, current -> acc || current }
            .distinctUntilChanged()

        val epochFlow = state.map { it.epoch }.distinctUntilChanged()

        viewModelScope.launch {
            observeStats(historicEnabledFlow, epochFlow).collect { r ->
                when (r) {
                    is Result.Ok -> update {
                        val historicFailed = r.value.historicError != null
                        it.copy(
                            snapshot = r.value,
                            error = null,
                            historicError = r.value.historicError,
                            // Stop spinning once Historic resolves either way — a populated window
                            // OR a surfaced error both end the load; only keep spinning while the
                            // tab is open and we have neither yet.
                            historicLoading = it.selectedTab == Tab.Historic &&
                                r.value.historic == null &&
                                !historicFailed,
                            isRefreshing = false,
                            // C8b — map the domain score style to the presentation enum so all
                            // leaderboard cards can read it from state without importing :core:domain.
                            scoreStyle = r.value.scoreStyle.toFrScoreStyle(),
                        )
                    }
                    is Result.Err -> update { it.copy(error = r.error, isRefreshing = false) }
                }
            }
        }
    }

    override suspend fun handle(intent: StatsIntent) = when (intent) {
        is StatsIntent.SelectTab -> {
            // Leaderboard surfaces (Month/Historic) — first open of each tab per VM lifetime.
            if (intent.tab != Tab.Week && leaderboardTabsViewed.add(intent.tab)) {
                analytics.track(AnalyticsEvent.LeaderboardViewed)
            }
            update {
                it.copy(
                    selectedTab = intent.tab,
                    historicLoading = intent.tab == Tab.Historic && it.snapshot?.historic == null,
                )
            }
        }
        StatsIntent.Refresh      -> update { it.copy(isRefreshing = true, epoch = it.epoch + 1) }
        StatsIntent.DismissError -> update { it.copy(error = null, historicError = null) }
        is StatsIntent.ShareAwardTapped  -> shareAward(intent.mealId)
        StatsIntent.ShareStreakTapped    -> shareStreak()
        StatsIntent.DismissShareOutcome  -> update { it.copy(shareOutcome = null) }
    }

    /**
     * Shares an award plate to Instagram Stories (spec §8.2). The `share` event (content_type=award)
     * fires ONLY when the launcher opened Instagram/the fallback sheet — never on Failed, never in a
     * use case. The renderer/decoder own their IO (no `withContext` here).
     */
    private suspend fun shareAward(mealId: String) {
        if (currentState.isPreparingShare) return
        val award = currentState.snapshot?.let { findAward(it, mealId) } ?: return
        val model = award.toAwardCard()
        update { it.copy(isPreparingShare = true, shareOutcome = null) }
        val outcome = storyShareController.share(
            plateUrl = model.photoUrl,
            format = ShareCardFormat.Story,
        ) { plate -> AwardShareCardContent(model, plate) }
        if (outcome != StoryShareOutcome.Failed) {
            MealId.of(model.mealId).getOrNull()?.let { analytics.track(AnalyticsEvent.AwardShared(it)) }
        }
        update { it.copy(isPreparingShare = false, shareOutcome = outcome.toUi()) }
    }

    /**
     * Shares the member's personal streak to Instagram Stories (spec §8.2). TRACK B: the card renders
     * full-bleed over the week's best plate (falling back to the most-voted plate) when one exists —
     * advisory only, `null` keeps the original solid-surface card.
     */
    private suspend fun shareStreak() {
        if (currentState.isPreparingShare) return
        val snapshot = currentState.snapshot ?: return
        val todayEmote = DailyEmote.forDay(MealDay.today(clock, zone))
        val plateUrl = snapshot.week.bestMeal?.photoUrl ?: snapshot.week.mostVotedMeal?.photoUrl
        val model = snapshot.hero.toStreakCard(todayEmote, plateUrl)
        update { it.copy(isPreparingShare = true, shareOutcome = null) }
        val outcome = storyShareController.share(
            plateUrl = plateUrl,
            format = ShareCardFormat.Story,
        ) { plate -> StreakShareCardContent(model, plate) }
        if (outcome != StoryShareOutcome.Failed) {
            analytics.track(AnalyticsEvent.StreakShared(model.streakDays))
        }
        update { it.copy(isPreparingShare = false, shareOutcome = outcome.toUi()) }
    }

    /** Finds the award (best or most-voted plate) with [mealId] in any window of [snapshot]. */
    private fun findAward(snapshot: StatsSnapshot, mealId: String): MealAward? =
        listOfNotNull(snapshot.week, snapshot.month, snapshot.historic)
            .flatMap { listOfNotNull(it.bestMeal, it.mostVotedMeal) }
            .firstOrNull { it.mealId.value == mealId }

    private fun StoryShareOutcome.toUi(): ShareOutcomeUi = when (this) {
        StoryShareOutcome.OpenedInstagram     -> ShareOutcomeUi.Succeeded
        StoryShareOutcome.OpenedFallbackSheet -> ShareOutcomeUi.OpenedSheet
        StoryShareOutcome.Failed              -> ShareOutcomeUi.Failed
    }
}

/**
 * C8b — maps the domain [CrewScoreStyle] to the presentation [FrScoreStyle] (the same arms that
 * [FeedViewModel.observeScoreStyle] and [MealDetailViewModel.scoreStyleFlow] use).
 */
private fun CrewScoreStyle.toFrScoreStyle(): FrScoreStyle = when (this) {
    CrewScoreStyle.Stars   -> FrScoreStyle.Stars
    CrewScoreStyle.Emoji   -> FrScoreStyle.Emoji
    CrewScoreStyle.Numeric -> FrScoreStyle.Numeric
}
