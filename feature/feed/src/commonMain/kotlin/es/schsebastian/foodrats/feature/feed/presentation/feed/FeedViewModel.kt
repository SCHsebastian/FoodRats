package es.schsebastian.foodrats.feature.feed.presentation.feed

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort
import es.schsebastian.foodrats.core.domain.meal.FeedSyncStatusPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReactionPort
import es.schsebastian.foodrats.core.domain.meal.OptimisticMealWritePort
import es.schsebastian.foodrats.core.domain.meal.MealReactions
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.meal.QueuedUploadActionsPort
import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.ReactionToggle
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.OutboxPendingSnapshot
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
import es.schsebastian.foodrats.feature.feed.presentation.components.toFeedUi
import es.schsebastian.foodrats.feature.feed.presentation.components.toRelative
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
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
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
    private val syncStatus: FeedSyncStatusPort,
    private val optimistic: OptimisticMealWritePort,
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

    /**
     * Last day for which [AnalyticsEvent.FeedDayViewed] was emitted. The feed Ok branch re-emits on
     * every rate/react read-model change while the cursor sits on the same day; this guards so the
     * event fires once per *distinct* loaded day (initial / prev / next), never on those re-emissions.
     */
    private var lastTrackedFeedDay: LocalDate? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val blindVotingFlow =
        activeCrew.current
            .distinctUntilChanged()
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf(false)
                else blindVoting.observeBlindVoting(crewId)
            }
            .distinctUntilChanged()

    /**
     * The active crew's last-synced stamp (P4-T2): re-subscribes to the new crew's stamp on a crew
     * switch via [flatMapLatest]; a null selection emits `null` (no crew → nothing synced).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val lastSyncedAtFlow =
        activeCrew.current
            .distinctUntilChanged()
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf(null) else syncStatus.lastSyncedAt(crewId)
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
                        // Once per distinct loaded day (initial / prev / next) — not on the rate/react
                        // re-emission of the same day. day_offset = days before today (0 = today).
                        val loadedDay = currentState.day?.day?.date
                        if (loadedDay != null && loadedDay != lastTrackedFeedDay) {
                            lastTrackedFeedDay = loadedDay
                            analytics.track(
                                AnalyticsEvent.FeedDayViewed(
                                    mealCount = uis.size,
                                    dayOffset = loadedDay.daysUntil(today),
                                ),
                            )
                        }
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

        // Write-outbox sync indicator (P2 §1 T8). observePending() is the single
        // source of truth: fold the raw entry list into the pending/terminal-failed
        // count pair and mirror it into state. It clears on its own as the runner
        // drains the outbox (a replayed command is removed → its count goes to 0).
        outbox.observePending()
            .map { OutboxPendingSnapshot.of(it) }
            .distinctUntilChanged()
            .onEach { snap ->
                update { it.copy(syncPending = snap.pending, syncFailed = snap.terminalFailed) }
            }
            .launchIn(viewModelScope)

        // Feed freshness (offline-first P4-T2): follow the active crew's last-synced stamp, resolve a
        // relative "synced X ago" timestamp against the clock (the VM owns the clock, mirroring
        // MealDetailViewModel's comment relatives), and mirror it into state. A null selection clears
        // the stamp. The arriving stamp also clears the user's pull-to-refresh spinner — re-pull landed.
        lastSyncedAtFlow
            .onEach { stamp ->
                val relative = stamp?.toRelative(clock.now())
                update { it.copy(syncedRelative = relative, isRefreshing = false) }
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
                    // Evict listeners for meals no longer visible so the cache can't grow
                    // unbounded across day navigation / crew switches.
                    reactionFlows.keys.retainAll(parsed.toSet())
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
        FeedIntent.RetrySyncOutbox     -> retrySyncOutbox()
        FeedIntent.DismissSyncOutbox   -> dismissSyncOutbox()
        FeedIntent.Refresh             -> refresh()
    }

    /**
     * Pull-to-refresh (P4-T2): forces a re-pull of the active crew's window via [FeedSyncStatusPort].
     * Sets the refreshing flag; it normally clears when the fresh last-synced stamp lands (the re-pull's
     * snapshot folded into the local store). A bounded safety-clear backs that up so the spinner can't
     * stick when the re-pull emits no stamp (offline) or an unchanged one (swallowed upstream). No-op
     * with no active crew.
     */
    private suspend fun refresh() {
        val crewId = activeCrew.current.first() ?: return
        update { it.copy(isRefreshing = true) }
        syncStatus.refresh(crewId)
        viewModelScope.launch {
            delay(REFRESH_SPINNER_TIMEOUT_MS)
            if (currentState.isRefreshing) update { it.copy(isRefreshing = false) }
        }
    }

    private companion object {
        /** Hard ceiling on the pull-to-refresh spinner (offline re-pull never lands a fresh stamp). */
        const val REFRESH_SPINNER_TIMEOUT_MS = 8_000L
    }

    /**
     * Re-arm every terminally-failed outbox entry (`Failed(retryable = false)`) by
     * requeueing it with a fresh attempt budget, so the runner grants a full backoff
     * cycle on the next drain. Uses [OutboxPort.requeue] (not [OutboxPort.updateStatus])
     * so `attemptCount` is reset to 0 — a terminal entry has `attemptCount == maxAttempts`,
     * meaning the automatic path would see attempt N+1 and instantly land terminal again.
     */
    private suspend fun retrySyncOutbox() {
        val entries = outbox.observePending().first()
        entries.filter { it.status.let { s -> s is OutboxEntryStatus.Failed && !s.retryable } }
            .forEach { outbox.requeue(it.id) }
    }

    /**
     * Drop every terminally-failed outbox entry. For [PendingCommand.RateMeal] entries,
     * also rolls back the phantom optimistic star via [OptimisticMealWritePort.clearPending]
     * before removal — the runner's [OutboxCommandHandler.onTerminal] only fires on
     * runner-detected terminal transitions, not on user-initiated dismissal.
     * [OptimisticMealWritePort.clearPending] is idempotent: safe if the server snapshot
     * already reconciled the row.
     */
    private suspend fun dismissSyncOutbox() {
        val entries = outbox.observePending().first()
        entries.filter { it.status.let { s -> s is OutboxEntryStatus.Failed && !s.retryable } }
            .forEach { entry ->
                if (entry.command is PendingCommand.RateMeal) {
                    optimistic.clearPending(entry.command.idempotencyKey)
                }
                outbox.remove(entry.id)
            }
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
        // The reaction is a relative flip online, but the outbox command is modelled as an
        // absolute target (converge-to, idempotent on replay): the target is the OPPOSITE of
        // the currently-reacted state read from the single-source-of-truth state.
        val currentlyReacted =
            currentState.meals.firstOrNull { it.mealId == mealIdRaw }?.viewerReacted ?: false
        update { it.copy(reactError = null) }
        // OFFLINE-FIRST (P2 §0.5): offline durably parks the toggle as a converge-to-target
        // command. No analytics here — the actual add/remove outcome is only known on replay.
        if (!connectivity.isOnline().first()) {
            enqueueReaction(crewId, mealId, reactorId, kind, desiredPresent = !currentlyReacted)
            return
        }
        when (val r = reactions.toggle(crewId, mealId, reactorId, kind)) {
            is Result.Ok -> {
                // Analytics fires ONLY when the reaction is added — never on removal (CHARTER rule 9).
                if (r.value is ReactionToggle.Added) {
                    analytics.track(AnalyticsEvent.MealReacted(mealId, kind.key))
                }
                // No optimistic state mutation: the live observe() stream re-emits the new count.
            }
            // A connectivity-class failure of the direct write also falls back to the outbox.
            is Result.Err -> when (r.error) {
                // Both connectivity-class failures fall back to the outbox (Unavailable = backend
                // unreachable/transient, same as Offline for queuing purposes).
                ReactionError.Toggle.Offline, ReactionError.Toggle.Unavailable ->
                    enqueueReaction(crewId, mealId, reactorId, kind, desiredPresent = !currentlyReacted)
                else -> update { it.copy(reactError = r.error) }
            }
        }
    }

    /**
     * Durably parks a [PendingCommand.ToggleReaction] in the outbox. Checks the
     * [OutboxPort.enqueue] result: a [es.schsebastian.foodrats.core.domain.outbox.OutboxError.PersistenceUnavailable]
     * means the toggle was NOT durably parked, so the user must be told the reaction
     * did not land (rather than silently claiming success). The reaction has no
     * optimistic local state, so there is nothing to roll back on failure.
     *
     * **DELIBERATE asymmetry vs. offline rate (L5):** offline ratings write an optimistic local row
     * so the star appears instantly before the network write. Reactions intentionally do NOT: there
     * is no optimistic UI update here, so an offline reaction shows no immediate visual feedback
     * until the outbox drains. This is a known trade-off — reactions are rare and less critical than
     * ratings to feel instant; full optimistic reactions would require a local reaction store with
     * rollback on failure, which is out of scope for the P1–P4 offline-first work. Do NOT treat
     * the missing UI feedback as an oversight.
     */
    private suspend fun enqueueReaction(
        crewId: CrewId,
        mealId: MealId,
        reactorId: AccountId,
        kind: ReactionKind,
        desiredPresent: Boolean,
    ) {
        val result = outbox.enqueue(
            PendingCommand.ToggleReaction(
                crewId = crewId,
                mealId = mealId,
                reactorId = reactorId,
                reactionKindKey = kind.key,
                desiredPresent = desiredPresent,
            ),
        )
        if (result is Result.Err) {
            // Persistence failure: the toggle was not durably saved. Surface the offline
            // error so the user knows the reaction did not land (no optimistic state to roll back).
            update { it.copy(reactError = ReactionError.Toggle.Offline) }
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
