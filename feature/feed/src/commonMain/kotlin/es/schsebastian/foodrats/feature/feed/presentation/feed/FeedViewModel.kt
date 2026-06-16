package es.schsebastian.foodrats.feature.feed.presentation.feed

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReactionPort
import es.schsebastian.foodrats.core.domain.meal.MealReactions
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.meal.QueuedUploadActionsPort
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.ReactionToggle
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
import es.schsebastian.foodrats.feature.feed.presentation.components.toFeedUi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import es.schsebastian.foodrats.core.domain.meal.MealDay as DomainMealDay

class FeedViewModel(
    observeFeed: ObserveFeedUseCase,
    private val rateMeal: RateMealUseCase,
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val clock: Clock,
    private val zone: TimeZone,
    uploadProgress: MealUploadProgressPort,
    blindVoting: CrewBlindVotingPort,
    private val reactions: MealReactionPort,
    private val queuedUploadActions: QueuedUploadActionsPort,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<FeedState, FeedIntent, FeedEffect>(
    FeedState(
        day = FeedDay.today(clock.now().toLocalDateTime(zone).date, zone),
        today = clock.now().toLocalDateTime(zone).date,
        canGoPrev = true,
    ),
) {

    private val today = clock.now().toLocalDateTime(zone).date

    /** Deduped per-meal reaction flows: at most one active listener per unique mealId. */
    private val reactionFlows = mutableMapOf<MealId, SharedFlow<MealReactions?>>()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val blindVotingFlow =
        activeCrew.current
            .distinctUntilChanged()
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf(false)
                else blindVoting.observeBlindVoting(crewId)
            }
            .distinctUntilChanged()

    init {
        viewModelScope.launch {
            combine(
                observeFeed(
                    state.map { it.day }
                        .filterNotNull()
                        .distinctUntilChanged(),
                ),
                blindVotingFlow,
            ) { r, blind -> r to blind }.collect { (r, blind) ->
                when (r) {
                    is Result.Ok -> {
                        val viewerId = session.current.first()?.accountId
                        val todayMealDay = DomainMealDay.today(clock, zone)
                        val uis = if (viewerId == null) {
                            emptyList()
                        } else {
                            // De-dup by mealId (idempotency reconcile, roadmap §5.2): the
                            // deterministic MealId.forDaySlot means a queued draft that has
                            // actually published is the SAME meal as its eventual feed row —
                            // it must render once, not twice. Also guards the LazyColumn's
                            // `key = { it.mealId }` against a duplicate-key crash.
                            r.value
                                .map { it.toFeedUi(viewerId, todayMealDay, blindVoting = blind) }
                                .distinctBy { it.mealId }
                        }
                        update { it.copy(isLoading = false, meals = uis, error = null, blindVoting = blind) }
                    }
                    is Result.Err -> update { it.copy(isLoading = false, error = r.error, blindVoting = blind) }
                }
            }
        }
        uploadProgress.status
            .map { it is MealUploadStatus.Uploading }
            .distinctUntilChanged()
            .onEach { active -> update { it.copy(isUploadActive = active) } }
            .launchIn(viewModelScope)

        // Offline-first publish queue (roadmap §5.2). The aggregate snapshot drives
        // the feed top-bar indicator; it clears on its own as the runner drains /
        // reconciles (a published queued draft is removed from the queue, so its
        // count goes to 0 — never double-counted against the now-authoritative meal).
        // StateFlow already conflates equal values, so no distinctUntilChanged() here
        // (applying it to a StateFlow is a no-op and a deprecation warning).
        uploadProgress.queue
            .onEach { snap ->
                update { it.copy(queuedPending = snap.pending, queuedFailed = snap.terminalFailed) }
            }
            .launchIn(viewModelScope)

        observeReactions()
    }

    /**
     * Multiplexes a live reaction listener per visible meal (deduped by id, like
     * [es.schsebastian.foodrats.feature.feed.presentation.detail.MealDetailViewModel]'s author
     * flows) and folds the counts + viewer-reacted flag back into [FeedState.meals]. Derived
     * purely from state (MVI single source of truth) — no parallel `MutableStateFlow`.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeReactions() = viewModelScope.launch {
        combine(
            activeCrew.current.distinctUntilChanged(),
            state.map { s -> s.meals.map { it.mealId } }.distinctUntilChanged(),
        ) { crewId, mealIds -> crewId to mealIds }
            .flatMapLatest { (crewId, mealIds) ->
                val viewerId = session.current.first()?.accountId
                val parsed = mealIds.mapNotNull { MealId.of(it).getOrElse { null } }
                if (crewId == null || viewerId == null || parsed.isEmpty()) {
                    flowOf(emptyMap<String, ReactionUi>())
                } else {
                    val perMealFlows = parsed.map { mealId ->
                        reactionFlows.getOrPut(mealId) {
                            reactions.observe(crewId, mealId)
                                .map { r -> (r as? Result.Ok)?.value }
                                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
                        }
                    }
                    combine(perMealFlows) { snapshots ->
                        parsed.zip(snapshots.toList()).associate { (mealId, reactionsForMeal) ->
                            mealId.value to ReactionUi(
                                count = reactionsForMeal?.count ?: 0,
                                viewerReacted = reactionsForMeal?.hasReacted(viewerId) ?: false,
                            )
                        }
                    }
                }
            }
            .collect { byMealId ->
                update { s ->
                    s.copy(
                        meals = s.meals.map { meal ->
                            val r = byMealId[meal.mealId]
                            if (r == null) meal
                            else meal.withReactions(count = r.count, viewerReacted = r.viewerReacted)
                        },
                    )
                }
            }
    }

    /** The reaction read-model values folded into a [es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi]. */
    private data class ReactionUi(val count: Int, val viewerReacted: Boolean)

    override suspend fun handle(intent: FeedIntent) = when (intent) {
        FeedIntent.PrevDay      -> { navigatePrev(); Unit }
        FeedIntent.NextDay      -> { navigateNext(); Unit }
        FeedIntent.DismissError -> update { it.copy(error = null, rateError = null, reactError = null) }
        is FeedIntent.RateMeal  -> rate(intent.mealId, intent.score)
        is FeedIntent.ReactMeal -> react(intent.mealId)
        FeedIntent.RetryQueuedDrafts   -> queuedUploadActions.retryFailed()
        FeedIntent.DismissQueuedDrafts -> queuedUploadActions.dismissFailed()
    }

    private suspend fun rate(mealIdRaw: String, scoreRaw: Int) {
        val crewId = activeCrew.current.first() ?: return
        val raterId = session.current.first()?.accountId ?: return
        val mealId = MealId.of(mealIdRaw).getOrElse { return }
        val score = Score.of(scoreRaw).getOrElse { return }
        update { it.copy(pendingRateMealId = mealIdRaw, rateError = null) }
        val r = rateMeal(crewId, mealId, raterId, score)
        if (r is Result.Ok) analytics.track(AnalyticsEvent.MealRated(mealId, scoreRaw))
        update {
            when (r) {
                is Result.Ok  -> it.copy(pendingRateMealId = null)
                is Result.Err -> it.copy(pendingRateMealId = null, rateError = r.error)
            }
        }
    }

    private suspend fun react(mealIdRaw: String) {
        val crewId = activeCrew.current.first() ?: return
        val reactorId = session.current.first()?.accountId ?: return
        val mealId = MealId.of(mealIdRaw).getOrElse { return }
        val kind = ReactionKind.DailyGlyph
        update { it.copy(reactError = null) }
        when (val r = reactions.toggle(crewId, mealId, reactorId, kind)) {
            is Result.Ok -> {
                // Analytics fires ONLY when the reaction is added — never on removal (CHARTER rule 9).
                if (r.value is ReactionToggle.Added) {
                    analytics.track(AnalyticsEvent.MealReacted(mealId, kind.key))
                }
                // No optimistic state mutation: the live observe() stream re-emits the new count.
            }
            is Result.Err -> update { it.copy(reactError = r.error) }
        }
    }

    private fun navigatePrev() {
        val currentDay = currentState.day ?: return
        val candidate = currentDay.previous()
        if (!FeedDay.isWithinWindow(candidate.day.date, today)) {
            update { it.copy(canGoPrev = false) }
            return
        }
        update { it.copy(
            day = candidate, isLoading = true,
            canGoNext = candidate.day.date < today,
            canGoPrev = FeedDay.isWithinWindow(candidate.previous().day.date, today),
        ) }
    }

    private fun navigateNext() {
        val currentDay = currentState.day ?: return
        val candidate = currentDay.next()
        if (candidate.day.date > today) { update { it.copy(canGoNext = false) }; return }
        update { it.copy(
            day = candidate, isLoading = true,
            canGoNext = candidate.day.date < today,
            canGoPrev = FeedDay.isWithinWindow(candidate.previous().day.date, today),
        ) }
    }
}
