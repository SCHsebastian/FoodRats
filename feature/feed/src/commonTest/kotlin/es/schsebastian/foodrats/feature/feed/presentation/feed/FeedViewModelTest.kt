package es.schsebastian.foodrats.feature.feed.presentation.feed

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort
import es.schsebastian.foodrats.core.domain.meal.FeedSyncStatusPort
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealReaction
import es.schsebastian.foodrats.core.domain.meal.MealReactionPort
import es.schsebastian.foodrats.core.domain.meal.MealReactions
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadQueueSnapshot
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.QueuedUploadActionsPort
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.ReactionToggle
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeActiveCrewProvider
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeConnectivityPort
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeMealReadPort
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RecordingOptimisticMealWritePort
import es.schsebastian.foodrats.feature.feed.domain.usecase.RecordingOutboxPort
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixedClockTest(private val instant: Instant) : Clock {
    override fun now() = instant
}

class FakeSessionProvider(session: Session?) : SessionProvider {
    private val flow = MutableStateFlow(session)
    override val current: Flow<Session?> = flow
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        flow.value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
}

class FakeCrewBlindVotingPort(blindVoting: Boolean = false) : CrewBlindVotingPort {
    val flow = MutableStateFlow(blindVoting)
    override fun observeBlindVoting(crewId: CrewId): Flow<Boolean> = flow
}

class FakeMealReactionPort(
    initial: Map<String, MealReactions> = emptyMap(),
) : MealReactionPort {
    data class ToggleCall(val crewId: String, val mealId: String, val reactorId: String, val kind: String)
    val toggleCalls = mutableListOf<ToggleCall>()
    var nextToggle: Result<ReactionToggle, ReactionError.Toggle> = Result.success(ReactionToggle.Added)
    val byMeal = MutableStateFlow(initial)

    override fun observe(crewId: CrewId, mealId: MealId): Flow<Result<MealReactions, ReactionError.Read>> =
        byMeal.map { Result.success(it[mealId.value] ?: MealReactions.empty(mealId)) }

    override suspend fun toggle(
        crewId: CrewId,
        mealId: MealId,
        reactorId: AccountId,
        kind: ReactionKind,
    ): Result<ReactionToggle, ReactionError.Toggle> {
        toggleCalls += ToggleCall(crewId.value, mealId.value, reactorId.value, kind.key)
        val r = nextToggle
        if (r is Result.Ok) {
            // Mirror the data layer so the live observe() stream reflects the toggle.
            val current = byMeal.value[mealId.value] ?: MealReactions.empty(mealId)
            val updated = when (r.value) {
                ReactionToggle.Added -> current.copy(
                    reactions = current.reactions + MealReaction(mealId, crewId, reactorId, kind, nowReactedAt),
                )
                ReactionToggle.Removed -> current.copy(
                    reactions = current.reactions.filterNot { it.reactorId == reactorId },
                )
            }
            byMeal.value = byMeal.value + (mealId.value to updated)
        }
        return r
    }

    companion object {
        val nowReactedAt: Instant = Instant.parse("2026-05-16T12:30:00Z")
    }
}

/** Upload-progress fake whose [queue] snapshot can be driven by the test (roadmap §5.2). */
class FakeUploadProgressPort(
    status: MealUploadStatus = MealUploadStatus.Idle,
    queue: MealUploadQueueSnapshot = MealUploadQueueSnapshot.EMPTY,
) : MealUploadProgressPort {
    override val status = MutableStateFlow(status)
    override val queue = MutableStateFlow(queue)
}

/** Records retry/dismiss calls so the queue-action intents can be asserted. */
class FakeQueuedUploadActionsPort : QueuedUploadActionsPort {
    var retryCount = 0
    var dismissCount = 0
    override suspend fun retryFailed() { retryCount++ }
    override suspend fun dismissFailed() { dismissCount++ }
}

/**
 * Controllable [FeedSyncStatusPort] (P4-T2): the test drives the per-crew last-synced stamp via
 * [emit] and records [refresh] calls so the Refresh intent can be asserted.
 */
class FakeFeedSyncStatusPort(initial: Instant? = null) : FeedSyncStatusPort {
    private val flow = MutableStateFlow(initial)
    val refreshedCrews = mutableListOf<String>()
    fun emit(stamp: Instant?) { flow.value = stamp }
    override fun lastSyncedAt(crewId: CrewId): Flow<Instant?> = flow
    override suspend fun refresh(crewId: CrewId) { refreshedCrews += crewId.value }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 16)
    private val nowInstant = Instant.parse("2026-05-16T12:00:00Z")
    private val clock = FixedClockTest(nowInstant)
    private val crew = (CrewId.of("c-1") as Result.Ok).value
    private val viewerId = (AccountId.of("u-viewer") as Result.Ok).value
    private val session = FakeSessionProvider(Session(viewerId, crew))

    private val sampleMeal = Meal(
        id = (MealId.of("m-1") as Result.Ok).value,
        author = MealAuthor((AccountId.of("u-1") as Result.Ok).value, "Sam", null),
        crewId = crew,
        day = MealDay(today, zone),
        slot = MealSlot.Lunch,
        photoUrl = "https://x/p.jpg",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = nowInstant,
    )
    private val sampleMealWithRatings = MealWithRatings(sampleMeal, emptyList())

    private val idleUploadProgress = object : MealUploadProgressPort {
        override val status: MutableStateFlow<MealUploadStatus> =
            MutableStateFlow(MealUploadStatus.Idle)
        override val queue = MealUploadProgressPort.DEFAULT_QUEUE
    }

    private fun buildVm(
        ratingPort: FakeMealRatingPort = FakeMealRatingPort(),
        active: FakeActiveCrewProvider = FakeActiveCrewProvider(initial = crew),
        port: FakeMealReadPort = FakeMealReadPort(
            perDay = mapOf((crew to "2026-05-16") to listOf(sampleMealWithRatings))
        ),
        blindVoting: FakeCrewBlindVotingPort = FakeCrewBlindVotingPort(),
        reactionPort: FakeMealReactionPort = FakeMealReactionPort(),
        analytics: RecordingAnalyticsTracker = RecordingAnalyticsTracker(),
        sessionProvider: SessionProvider = session,
        uploadProgress: MealUploadProgressPort = idleUploadProgress,
        queuedActions: QueuedUploadActionsPort = FakeQueuedUploadActionsPort(),
        connectivity: FakeConnectivityPort = FakeConnectivityPort(online = true),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
        syncStatus: FakeFeedSyncStatusPort = FakeFeedSyncStatusPort(),
        blockedAccounts: es.schsebastian.foodrats.feature.feed.domain.usecase.FakeBlockedAccountsPort =
            es.schsebastian.foodrats.feature.feed.domain.usecase.FakeBlockedAccountsPort(),
    ) = FeedViewModel(
        observeFeed = ObserveFeedUseCase(active, port, sessionProvider, blockedAccounts),
        rateMeal = RateMealUseCase(ratingPort, connectivity, outbox, RecordingOptimisticMealWritePort()),
        activeCrew = active,
        session = sessionProvider,
        clock = clock,
        zone = zone,
        uploadProgress = uploadProgress,
        blindVoting = blindVoting,
        reactions = reactionPort,
        queuedUploadActions = queuedActions,
        connectivity = connectivity,
        outbox = outbox,
        syncStatus = syncStatus,
        analytics = analytics,
    )

    @Test fun initial_state_today_with_meals() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = mapOf((crew to "2026-05-16") to listOf(sampleMealWithRatings)))
        val vm = buildVm(active = active, port = port)
        vm.state.test {
            var s = awaitItem()
            if (s.isLoading) s = awaitItem()
            assertEquals(false, s.isLoading)
            assertEquals(1, s.meals.size)
            assertEquals("m-1", s.meals[0].mealId)
            assertEquals(false, s.canGoNext)
            assertEquals(true, s.canGoPrev)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun prev_day_loads_yesterday() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val yesterday = "2026-05-15"
        val port = FakeMealReadPort(perDay = mapOf(
            (crew to "2026-05-16") to emptyList(),
            (crew to yesterday) to listOf(sampleMealWithRatings.copy(
                meal = sampleMeal.copy(day = MealDay(LocalDate(2026, 5, 15), zone))
            )),
        ))
        val vm = buildVm(active = active, port = port)
        vm.onIntent(FeedIntent.PrevDay)
        val s = vm.state.value
        assertEquals(LocalDate(2026, 5, 15), s.day?.day?.date)
        assertEquals(true, s.canGoNext)
    }

    @Test fun next_day_blocked_at_today() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = emptyMap())
        val vm = buildVm(active = active, port = port)
        vm.onIntent(FeedIntent.NextDay)
        assertEquals(today, vm.state.value.day?.day?.date)
    }

    @Test fun prev_day_blocked_at_window_boundary() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = emptyMap())
        val vm = buildVm(active = active, port = port)
        repeat(30) { vm.onIntent(FeedIntent.PrevDay) }
        val boundary = vm.state.value.day?.day?.date
        vm.onIntent(FeedIntent.PrevDay)
        assertEquals(boundary, vm.state.value.day?.day?.date)
        assertTrue(vm.state.value.canGoPrev.not())
    }

    @Test fun read_error_propagates_to_state() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(readError = MealReadError.Unauthorized)
        val vm = buildVm(active = active, port = port)
        assertTrue(vm.state.value.error != null || vm.state.value.isLoading)
    }

    @Test fun rate_meal_records_call_with_correct_score() = runTest {
        val ratingPort = FakeMealRatingPort()
        val vm = buildVm(ratingPort = ratingPort)
        vm.onIntent(FeedIntent.RateMeal("meal-1", 4))
        runCurrent()
        assertEquals(1, ratingPort.calls.size)
        assertEquals("meal-1", ratingPort.calls.first().mealId)
        assertEquals(4, ratingPort.calls.first().score)
        assertEquals("u-viewer", ratingPort.calls.first().raterId)
    }

    @Test fun blind_voting_off_author_shown_in_state() = runTest {
        val vm = buildVm(blindVoting = FakeCrewBlindVotingPort(blindVoting = false))
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(false, s.blindVoting)
            assertEquals(false, s.meals.single().authorMasked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun blind_voting_on_not_voted_not_author_masks() = runTest {
        val vm = buildVm(blindVoting = FakeCrewBlindVotingPort(blindVoting = true))
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(true, s.blindVoting)
            assertTrue(s.meals.single().authorMasked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun blind_voting_on_own_meal_not_masked() = runTest {
        // Viewer is the author of the meal -> never masked even when blind voting is on.
        val authorId = sampleMeal.author.accountId
        val authorSession = FakeSessionProvider(Session(authorId, crew))
        val vm = buildVm(
            blindVoting = FakeCrewBlindVotingPort(blindVoting = true),
            sessionProvider = authorSession,
        )
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(true, s.blindVoting)
            assertEquals(false, s.meals.single().authorMasked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun rate_meal_failure_populates_rateError() = runTest {
        val ratingPort = FakeMealRatingPort().apply {
            nextResult = Result.failure(RateError.CannotRateOwnMeal)
        }
        val vm = buildVm(ratingPort = ratingPort)
        vm.state.test {
            skipItems(1)
            vm.onIntent(FeedIntent.RateMeal("meal-1", 5))
            val s = expectMostRecentItem()
            assertEquals(RateError.CannotRateOwnMeal, s.rateError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observed_reactions_reflect_into_meal_state() = runTest {
        val mealId = (MealId.of("m-1") as Result.Ok).value
        val other = (AccountId.of("u-other") as Result.Ok).value
        val reactionPort = FakeMealReactionPort(
            initial = mapOf(
                "m-1" to MealReactions(
                    mealId = mealId,
                    reactions = listOf(
                        MealReaction(mealId, crew, other, ReactionKind.DailyGlyph, FakeMealReactionPort.nowReactedAt),
                    ),
                ),
            ),
        )
        val vm = buildVm(reactionPort = reactionPort)
        vm.state.test {
            val s = expectMostRecentItem()
            val meal = s.meals.single { it.mealId == "m-1" }
            assertEquals(1, meal.reactionCount)
            assertEquals(false, meal.viewerReacted) // a different member reacted, not the viewer
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun react_added_updates_state_and_tracks_analytics() = runTest {
        val reactionPort = FakeMealReactionPort()
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(reactionPort = reactionPort, analytics = analytics)
        vm.state.test {
            skipItems(1)
            vm.onIntent(FeedIntent.ReactMeal("m-1"))
            runCurrent()
            val s = expectMostRecentItem()
            val meal = s.meals.single { it.mealId == "m-1" }
            assertEquals(1, meal.reactionCount)
            assertTrue(meal.viewerReacted)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, reactionPort.toggleCalls.size)
        assertEquals("m-1", reactionPort.toggleCalls.first().mealId)
        assertEquals("daily_glyph", reactionPort.toggleCalls.first().kind)
        assertEquals(
            listOf(AnalyticsEvent.MealReacted((MealId.of("m-1") as Result.Ok).value, "daily_glyph")),
            analytics.events.filterIsInstance<AnalyticsEvent.MealReacted>(),
        )
    }

    @Test fun react_removed_does_not_track_analytics() = runTest {
        val viewer = (AccountId.of("u-viewer") as Result.Ok).value
        val mealId = (MealId.of("m-1") as Result.Ok).value
        val reactionPort = FakeMealReactionPort(
            initial = mapOf(
                "m-1" to MealReactions(
                    mealId = mealId,
                    reactions = listOf(
                        MealReaction(mealId, crew, viewer, ReactionKind.DailyGlyph, FakeMealReactionPort.nowReactedAt),
                    ),
                ),
            ),
        ).apply { nextToggle = Result.success(ReactionToggle.Removed) }
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(reactionPort = reactionPort, analytics = analytics)
        vm.state.test {
            // Viewer already reacted -> starts highlighted with count 1.
            var s = expectMostRecentItem()
            assertTrue(s.meals.single { it.mealId == "m-1" }.viewerReacted)
            vm.onIntent(FeedIntent.ReactMeal("m-1"))
            runCurrent()
            s = expectMostRecentItem()
            val meal = s.meals.single { it.mealId == "m-1" }
            assertEquals(0, meal.reactionCount)
            assertEquals(false, meal.viewerReacted)
            cancelAndIgnoreRemainingEvents()
        }
        // Removed never tracks a reaction (CHARTER rule 9); FeedDayViewed from the initial load is unrelated.
        assertTrue(analytics.events.none { it is AnalyticsEvent.MealReacted })
    }

    @Test fun react_failure_with_offline_falls_back_to_outbox() = runTest {
        // A connectivity-class failure of the direct toggle parks the command instead of erroring.
        val reactionPort = FakeMealReactionPort().apply {
            nextToggle = Result.failure(ReactionError.Toggle.Offline)
        }
        val outbox = RecordingOutboxPort()
        val vm = buildVm(reactionPort = reactionPort, outbox = outbox)
        runCurrent()
        vm.onIntent(FeedIntent.ReactMeal("m-1"))
        runCurrent()
        assertEquals(null, vm.state.value.reactError, "connectivity failure is parked, not surfaced")
        assertEquals(1, outbox.enqueued.size)
        val cmd = outbox.enqueued.single()
        assertTrue(cmd is PendingCommand.ToggleReaction)
        assertEquals("m-1", cmd.mealId.value)
        assertEquals(true, cmd.desiredPresent) // viewer had not reacted -> target present
    }

    // --- offline-first write fallback (P2 §0.5 T7) ------------------------------

    @Test fun react_offline_enqueues_toggle_with_target_and_skips_direct_port() = runTest {
        val reactionPort = FakeMealReactionPort()
        val outbox = RecordingOutboxPort()
        val vm = buildVm(
            reactionPort = reactionPort,
            outbox = outbox,
            connectivity = FakeConnectivityPort(online = false),
        )
        vm.state.test {
            skipItems(1)
            vm.onIntent(FeedIntent.ReactMeal("m-1"))
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(reactionPort.toggleCalls.isEmpty(), "offline must not hit the direct port")
        val cmd = outbox.enqueued.single()
        assertTrue(cmd is PendingCommand.ToggleReaction)
        assertEquals("m-1", cmd.mealId.value)
        assertEquals("daily_glyph", cmd.reactionKindKey)
        assertEquals(true, cmd.desiredPresent)
    }

    @Test fun rate_offline_enqueues_and_skips_direct_port() = runTest {
        val ratingPort = FakeMealRatingPort()
        val outbox = RecordingOutboxPort()
        val vm = buildVm(
            ratingPort = ratingPort,
            outbox = outbox,
            connectivity = FakeConnectivityPort(online = false),
        )
        vm.onIntent(FeedIntent.RateMeal("m-1", 4))
        runCurrent()
        assertTrue(ratingPort.calls.isEmpty(), "offline must not hit the direct rating port")
        val cmd = outbox.enqueued.single()
        assertTrue(cmd is PendingCommand.RateMeal)
        assertEquals("m-1", cmd.mealId.value)
        assertEquals(4, cmd.score.value)
    }

    // --- Offline-first publish queue indicator (roadmap §5.2) ------------------

    @Test fun queue_snapshot_pending_count_is_surfaced_in_state() = runTest {
        val upload = FakeUploadProgressPort(
            queue = MealUploadQueueSnapshot(pending = 2, terminalFailed = 0),
        )
        val vm = buildVm(uploadProgress = upload)
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(2, s.queuedPending)
            assertEquals(0, s.queuedFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun queue_empty_keeps_both_counts_zero() = runTest {
        // No queue snapshot -> the top-bar indicator stays hidden.
        val vm = buildVm(uploadProgress = FakeUploadProgressPort())
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(0, s.queuedPending)
            assertEquals(0, s.queuedFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun terminal_failed_count_is_surfaced_and_retry_invokes_port() = runTest {
        val upload = FakeUploadProgressPort(
            queue = MealUploadQueueSnapshot(pending = 0, terminalFailed = 1),
        )
        val actions = FakeQueuedUploadActionsPort()
        val vm = buildVm(uploadProgress = upload, queuedActions = actions)
        vm.state.test {
            assertEquals(1, expectMostRecentItem().queuedFailed)
            cancelAndIgnoreRemainingEvents()
        }
        vm.onIntent(FeedIntent.RetryQueuedDrafts)
        runCurrent()
        assertEquals(1, actions.retryCount)
        assertEquals(0, actions.dismissCount)
    }

    @Test fun dismiss_queued_drafts_invokes_port() = runTest {
        val actions = FakeQueuedUploadActionsPort()
        val vm = buildVm(
            uploadProgress = FakeUploadProgressPort(
                queue = MealUploadQueueSnapshot(pending = 0, terminalFailed = 1),
            ),
            queuedActions = actions,
        )
        vm.onIntent(FeedIntent.DismissQueuedDrafts)
        runCurrent()
        assertEquals(1, actions.dismissCount)
        assertEquals(0, actions.retryCount)
    }

    // --- feed_day_viewed analytics (once per distinct loaded day) ---------------

    @Test fun feed_day_viewed_fires_once_on_initial_load() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(analytics = analytics)
        vm.state.test {
            var s = awaitItem()
            if (s.isLoading) s = awaitItem()
            assertEquals(1, s.meals.size)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.FeedDayViewed(mealCount = 1, dayOffset = 0)),
            analytics.events.toList(),
        )
    }

    @Test fun feed_day_viewed_fires_per_prev_and_next_navigation() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val yesterday = "2026-05-15"
        val port = FakeMealReadPort(perDay = mapOf(
            (crew to "2026-05-16") to listOf(sampleMealWithRatings),
            (crew to yesterday) to emptyList(),
        ))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(active = active, port = port, analytics = analytics)
        runCurrent()
        vm.onIntent(FeedIntent.PrevDay) // -> yesterday (offset 1, 0 meals)
        runCurrent()
        vm.onIntent(FeedIntent.NextDay) // -> back to today (offset 0, 1 meal)
        runCurrent()
        assertEquals(
            listOf<AnalyticsEvent>(
                AnalyticsEvent.FeedDayViewed(mealCount = 1, dayOffset = 0),
                AnalyticsEvent.FeedDayViewed(mealCount = 0, dayOffset = 1),
                AnalyticsEvent.FeedDayViewed(mealCount = 1, dayOffset = 0),
            ),
            analytics.events.toList(),
        )
    }

    @Test fun feed_day_viewed_does_not_refire_on_rate_re_emission() = runTest {
        val ratingPort = FakeMealRatingPort()
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(ratingPort = ratingPort, analytics = analytics)
        runCurrent()
        // A rate re-runs the feed Ok branch for the SAME day — must not re-fire feed_day_viewed.
        vm.onIntent(FeedIntent.RateMeal("m-1", 4))
        runCurrent()
        assertEquals(
            listOf<AnalyticsEvent>(
                AnalyticsEvent.FeedDayViewed(mealCount = 1, dayOffset = 0),
                AnalyticsEvent.MealRated((MealId.of("m-1") as Result.Ok).value, 4),
            ),
            analytics.events.toList(),
        )
    }

    // --- feed freshness + pull-to-refresh (offline-first P4-T2) -----------------

    @Test fun last_synced_stamp_maps_to_relative_in_state() = runTest {
        // The clock is fixed at 12:00:00Z; a stamp 5 minutes earlier resolves to "5 min ago".
        val syncStatus = FakeFeedSyncStatusPort(
            initial = Instant.parse("2026-05-16T11:55:00Z"),
        )
        val vm = buildVm(syncStatus = syncStatus)
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(FeedStringKey.CommentsRelativeMinutes, s.syncedRelative?.key)
            assertEquals(5, s.syncedRelative?.amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun no_sync_yet_keeps_synced_relative_null() = runTest {
        val vm = buildVm(syncStatus = FakeFeedSyncStatusPort(initial = null))
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(null, s.syncedRelative)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun fresh_stamp_clears_the_refreshing_spinner() = runTest {
        val syncStatus = FakeFeedSyncStatusPort(initial = null)
        val vm = buildVm(syncStatus = syncStatus)
        vm.onIntent(FeedIntent.Refresh)
        runCurrent()
        assertTrue(vm.state.value.isRefreshing, "refresh kicks the spinner on")
        // A fresh stamp arriving (the re-pull's snapshot) drops the spinner.
        syncStatus.emit(Instant.parse("2026-05-16T12:00:00Z"))
        runCurrent()
        assertEquals(false, vm.state.value.isRefreshing)
    }

    @Test fun refresh_intent_calls_the_port_with_active_crew() = runTest {
        val syncStatus = FakeFeedSyncStatusPort()
        val vm = buildVm(syncStatus = syncStatus)
        vm.onIntent(FeedIntent.Refresh)
        runCurrent()
        assertEquals(listOf("c-1"), syncStatus.refreshedCrews)
    }

    @Test fun published_queued_draft_is_not_double_rendered() = runTest {
        // Idempotency reconcile: a queued draft that actually published shares the
        // deterministic MealId with its eventual feed row, so the read port could
        // transiently emit the same MealId twice. The feed must render it ONCE.
        val active = FakeActiveCrewProvider(initial = crew)
        val duplicated = FakeMealReadPort(
            perDay = mapOf(
                (crew to "2026-05-16") to listOf(sampleMealWithRatings, sampleMealWithRatings),
            ),
        )
        val vm = buildVm(active = active, port = duplicated)
        vm.state.test {
            var s = awaitItem()
            if (s.isLoading) s = awaitItem()
            assertEquals(1, s.meals.size)
            assertEquals("m-1", s.meals.single().mealId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
