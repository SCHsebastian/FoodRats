package es.schsebastian.foodrats.feature.feed.presentation.feed

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.designsystem.molecules.FrReportReasonOption
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreStyle
import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.crew.CrewWelcomePort
import es.schsebastian.foodrats.core.domain.meal.FeedSyncStatusPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReactionPort
import es.schsebastian.foodrats.core.domain.meal.OptimisticMealWritePort
import es.schsebastian.foodrats.core.domain.meal.MealReactions
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.meal.QueuedUploadActionsPort
import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.ReactionToggle
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.moderation.ReportPort
import es.schsebastian.foodrats.core.domain.moderation.ReportReason
import es.schsebastian.foodrats.core.domain.moderation.ReportTarget
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
import kotlinx.coroutines.flow.Flow
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
    // UGC compliance §4/§5 — report/block from the feed overflow. Defaults are no-ops so existing
    // tests compile without injecting these ports; Koin always passes the real implementations.
    private val reportPort: ReportPort = NoopFeedReportPort,
    private val blockedAccounts: BlockedAccountsPort = NoopFeedBlockedAccountsPort,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
    // C6 — pinned welcome banner. Default is a no-op so existing tests keep compiling.
    private val welcomePort: CrewWelcomePort = NoopCrewWelcomePort,
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
        observeWelcomeBanner()
        observeWeeklyChallengeBanner()
        observeScoreStyle()
        observeBannerImageUrl()
        observeBannerFocalY()
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

    /**
     * Observes the active crew's welcome message combined with the viewer's per-crew dismissal state
     * (C6). The `welcomeMessage` in state is non-null only when the message is set AND not dismissed.
     * Re-subscribes on crew switch via [flatMapLatest]; a null crew hides the banner.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeWelcomeBanner() {
        activeCrew.current
            .distinctUntilChanged()
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf(null)
                else combine(
                    welcomePort.observeWelcomeMessage(crewId),
                    welcomePort.isWelcomeDismissed(crewId),
                ) { msg, dismissed -> if (dismissed || msg.isNullOrBlank()) null else msg }
            }
            .distinctUntilChanged()
            .onEach { msg -> update { it.copy(welcomeMessage = msg) } }
            .launchIn(viewModelScope)
    }

    /**
     * Observes the active crew's weekly challenge (C5). The `weeklyChallenge` in state is non-null
     * only when the text is set AND `now - setAt < 7 days` (client-side expiry check).
     * Re-subscribes on crew switch via [flatMapLatest]; a null crew hides the chip.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeWeeklyChallengeBanner() {
        activeCrew.current
            .distinctUntilChanged()
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf(null)
                else welcomePort.observeWeeklyChallenge(crewId)
            }
            .map { snapshot ->
                if (snapshot == null) null
                else {
                    val sevenDaysMs = 7L * 24 * 60 * 60 * 1_000
                    val ageMs = clock.now().toEpochMilliseconds() - snapshot.setAtMillis
                    if (ageMs >= sevenDaysMs) null else snapshot.text
                }
            }
            .distinctUntilChanged()
            .onEach { text -> update { it.copy(weeklyChallenge = text) } }
            .launchIn(viewModelScope)
    }

    /**
     * Observes the active crew's Score display vocabulary (C8). Re-subscribes on crew switch via
     * [flatMapLatest]; a null crew defaults to [FrScoreStyle.Stars] (no active crew = safe default).
     * Maps domain [CrewScoreStyle] → presentation [FrScoreStyle] here so feature/feed stays free
     * of a direct domain-to-DS-type dependency in the VM (the mapping is a single-line when).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeScoreStyle() {
        activeCrew.current
            .distinctUntilChanged()
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf(FrScoreStyle.Stars)
                else welcomePort.observeScoreStyle(crewId).map { domain ->
                    when (domain) {
                        CrewScoreStyle.Stars   -> FrScoreStyle.Stars
                        CrewScoreStyle.Emoji   -> FrScoreStyle.Emoji
                        CrewScoreStyle.Numeric -> FrScoreStyle.Numeric
                    }
                }
            }
            .distinctUntilChanged()
            .onEach { style -> update { it.copy(scoreStyle = style) } }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: FeedIntent) = when (intent) {
        FeedIntent.PrevDay      -> { navigatePrev(); Unit }
        FeedIntent.NextDay      -> { navigateNext(); Unit }
        FeedIntent.DismissError -> update { it.copy(error = null, rateError = null, reactError = null, feedBlockError = null) }
        is FeedIntent.RateMeal  -> rate(intent.mealId, intent.score)
        is FeedIntent.ReactMeal -> react(intent.mealId)
        FeedIntent.RetryQueuedDrafts   -> queuedUploadActions.retryFailed()
        FeedIntent.DismissQueuedDrafts -> queuedUploadActions.dismissFailed()
        FeedIntent.RetrySyncOutbox     -> retrySyncOutbox()
        FeedIntent.DismissSyncOutbox   -> dismissSyncOutbox()
        FeedIntent.Refresh             -> refresh()
        // UGC compliance §4/§5 — overflow report/block from the feed card.
        is FeedIntent.OpenFeedReport         -> update { it.copy(feedReportTarget = intent.target) }
        is FeedIntent.SubmitFeedReport       -> submitFeedReport(intent.reason)
        FeedIntent.DismissFeedReport         -> update { it.copy(feedReportTarget = null, feedReportSubmitting = false) }
        is FeedIntent.BlockFeedAuthor        -> blockFeedAuthor(intent.authorId)
        FeedIntent.DismissFeedReportSuccess  -> update { it.copy(feedReportSuccess = false) }
        FeedIntent.DismissFeedBlockSuccess   -> update { it.copy(feedBlockSuccess = false) }
        FeedIntent.DismissFeedBlockError     -> update { it.copy(feedBlockError = null) }
        FeedIntent.DismissWelcomeBanner      -> dismissWelcomeBanner()
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

    /**
     * Observes the active crew's hero/banner image URL (C9). Re-subscribes on crew switch via
     * [flatMapLatest]; a null crew or absent bannerPath emits `null` → banner hidden.
     * Signed URL resolution runs inside the port binding (data layer); the VM sees a plain URL string.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeBannerImageUrl() {
        activeCrew.current
            .distinctUntilChanged()
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf(null)
                else welcomePort.observeBannerImageUrl(crewId)
            }
            .distinctUntilChanged()
            .onEach { url -> update { it.copy(bannerImageUrl = url) } }
            .launchIn(viewModelScope)
    }

    /**
     * Observes the active crew's banner focal point (C9). Re-subscribes on crew switch; a null crew
     * defaults to `0.5f` (center). Feeds [FeedState.bannerFocalY], which steers the hero crop's
     * vertical alignment so it shows the slice the owner chose in crew settings.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeBannerFocalY() {
        activeCrew.current
            .distinctUntilChanged()
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf(0.5f)
                else welcomePort.observeBannerFocalY(crewId)
            }
            .distinctUntilChanged()
            .onEach { focal -> update { it.copy(bannerFocalY = focal) } }
            .launchIn(viewModelScope)
    }

    // ── Welcome banner (C6) ─────────────────────────────────────────────────────────────────────

    /**
     * Persists the per-crew dismissal to DataStore. The [observeWelcomeBanner] flow will re-emit
     * `null` as soon as [CrewWelcomePort.isWelcomeDismissed] flips to `true`, hiding the banner
     * without an explicit `update { it.copy(welcomeMessage = null) }`.
     */
    private suspend fun dismissWelcomeBanner() {
        val crewId = activeCrew.current.first() ?: return
        welcomePort.dismissWelcome(crewId)
    }

    // ── Feed overflow UGC actions ────────────────────────────────────────────────────────────────

    /** Maps the presentation reason option to the domain [ReportReason]. */
    private fun FrReportReasonOption.toReason(): ReportReason = when (this) {
        FrReportReasonOption.SPAM       -> ReportReason.Spam
        FrReportReasonOption.HARASSMENT -> ReportReason.Harassment
        FrReportReasonOption.HATE       -> ReportReason.Hate
        FrReportReasonOption.SEXUAL     -> ReportReason.Sexual
        FrReportReasonOption.VIOLENCE   -> ReportReason.Violence
        FrReportReasonOption.OTHER      -> ReportReason.Other
    }

    /** Submits the feed report against the pending [FeedState.feedReportTarget] (UGC compliance §4). */
    private suspend fun submitFeedReport(reasonOption: FrReportReasonOption) {
        val target = currentState.feedReportTarget ?: return
        // A4: session/crew lost mid-use → close the sheet instead of leaving it frozen open. (This
        // report path surfaces only success today; full failure surfacing on the feed overflow is a
        // pre-existing gap — the detail screen's report DOES show reportError.)
        val crewId = activeCrew.current.first()
            ?: return update { it.copy(feedReportTarget = null) }
        val reporter = session.current.first()?.accountId
            ?: return update { it.copy(feedReportTarget = null) }
        val parsedMealId = MealId.of(target.mealId).getOrElse { return }
        val domainTarget: ReportTarget = when (target) {
            is FeedReportTarget.Meal   -> ReportTarget.Meal(parsedMealId, crewId)
            is FeedReportTarget.Author -> ReportTarget.Account(
                AccountId.of(target.authorId).getOrElse { return },
            )
        }
        update { it.copy(feedReportSubmitting = true) }
        when (reportPort.report(reporter, domainTarget, reasonOption.toReason())) {
            is Result.Ok  -> update {
                it.copy(feedReportSubmitting = false, feedReportTarget = null, feedReportSuccess = true)
            }
            is Result.Err -> update { it.copy(feedReportSubmitting = false, feedReportTarget = null) }
        }
    }

    /**
     * Blocks the meal's author directly from the feed overflow (UGC compliance §5). The confirm
     * dialog is shown by the screen before this intent fires, so no guard is needed here.
     */
    private suspend fun blockFeedAuthor(rawAuthorId: String) {
        // A4: don't silently no-op when the session is lost mid-use — surface a typed error so the
        // user sees the tap failed (the root nav routes to SignIn once current goes null).
        val owner = session.current.first()?.accountId
            ?: return update { it.copy(feedBlockError = BlockError.Write.Unavailable) }
        val target = AccountId.of(rawAuthorId).getOrElse { return }
        update { it.copy(feedBlockError = null) }
        when (val r = blockedAccounts.block(owner, target)) {
            is Result.Ok  -> update { it.copy(feedBlockSuccess = true) }
            is Result.Err -> update { it.copy(feedBlockError = r.error) }
        }
    }

    private suspend fun rate(mealIdRaw: String, scoreRaw: Int) {
        // A4: session/crew lost mid-use → surface a typed error instead of a silent dead tap.
        val crewId = activeCrew.current.first()
            ?: return update { it.copy(rateError = RateError.Unauthorized) }
        val raterId = session.current.first()?.accountId
            ?: return update { it.copy(rateError = RateError.Unauthorized) }
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
        // A4: session/crew lost mid-use → surface a typed error instead of a silent dead tap.
        val crewId = activeCrew.current.first()
            ?: return update { it.copy(reactError = ReactionError.Toggle.Unauthorized) }
        val reactorId = session.current.first()?.accountId
            ?: return update { it.copy(reactError = ReactionError.Toggle.Unauthorized) }
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

/**
 * No-op [ReportPort] used as the constructor default so the existing test surface keeps compiling.
 * The Koin binding always passes the real Firestore-backed port; production never sees this.
 */
private object NoopFeedReportPort : ReportPort {
    override suspend fun report(
        reporter: AccountId,
        target: ReportTarget,
        reason: ReportReason,
    ): Result<Unit, es.schsebastian.foodrats.core.domain.moderation.ReportError> = Result.success(Unit)
}

/** No-op [BlockedAccountsPort] default for [FeedViewModel] (see [NoopFeedReportPort]). */
private object NoopFeedBlockedAccountsPort : BlockedAccountsPort {
    override fun observeBlocked(owner: AccountId): Flow<Set<AccountId>> = flowOf(emptySet())
    override suspend fun block(
        owner: AccountId,
        target: AccountId,
    ): Result<Unit, es.schsebastian.foodrats.core.domain.account.BlockError> = Result.success(Unit)
    override suspend fun unblock(
        owner: AccountId,
        target: AccountId,
    ): Result<Unit, es.schsebastian.foodrats.core.domain.account.BlockError> = Result.success(Unit)
}

/** No-op [CrewWelcomePort] default for [FeedViewModel] — see [NoopFeedReportPort] for rationale. */
private object NoopCrewWelcomePort : CrewWelcomePort {
    override fun observeWelcomeMessage(crewId: es.schsebastian.foodrats.core.domain.model.CrewId): Flow<String?> = flowOf(null)
    override fun isWelcomeDismissed(crewId: es.schsebastian.foodrats.core.domain.model.CrewId): Flow<Boolean> = flowOf(false)
    override suspend fun dismissWelcome(crewId: es.schsebastian.foodrats.core.domain.model.CrewId) = Unit
    override fun observeWeeklyChallenge(crewId: es.schsebastian.foodrats.core.domain.model.CrewId): Flow<es.schsebastian.foodrats.core.domain.crew.WeeklyChallengeSnapshot?> = flowOf(null)
    override fun observeScoreStyle(crewId: es.schsebastian.foodrats.core.domain.model.CrewId): Flow<CrewScoreStyle> = flowOf(CrewScoreStyle.Stars)
    override fun observeBannerImageUrl(crewId: es.schsebastian.foodrats.core.domain.model.CrewId): Flow<String?> = flowOf(null)
    override fun observeBannerFocalY(crewId: es.schsebastian.foodrats.core.domain.model.CrewId): Flow<Float> = flowOf(0.5f)
}
