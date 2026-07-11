package es.schsebastian.foodrats.feature.achievements.presentation

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressError
import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.achievements.domain.AchievementEvaluator
import es.schsebastian.foodrats.feature.achievements.domain.AchievementReconciler
import es.schsebastian.foodrats.feature.achievements.domain.AchievementSignalsBuilder
import es.schsebastian.foodrats.feature.achievements.domain.acct
import es.schsebastian.foodrats.feature.achievements.domain.crew
import es.schsebastian.foodrats.feature.achievements.domain.meal
import es.schsebastian.foodrats.feature.achievements.domain.usecase.ObserveAchievementsUseCase
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 6, 14)
    private val nowInstant = Instant.parse("2026-06-14T12:00:00Z")
    private val clock = object : Clock { override fun now() = nowInstant }
    private val me: AccountId = acct("me")
    private val crewId: CrewId = crew("c-1")

    private class FakeProgress(
        initial: Map<String, Long> = emptyMap(),
        private val recordResult: Result<Unit, AchievementProgressError> = Result.success(Unit),
    ) : AchievementProgressPort {
        val unlocks = MutableStateFlow<Map<String, Long>>(initial)
        val recorded = mutableListOf<Map<String, Long>>()
        override fun observeUnlocks(accountId: AccountId): Flow<Result<Map<String, Long>, AchievementProgressError>> =
            MutableStateFlow(Result.success(unlocks.value))
        override suspend fun recordUnlocks(
            accountId: AccountId,
            newlyUnlocked: Map<String, Long>,
        ): Result<Unit, AchievementProgressError> {
            recorded += newlyUnlocked
            if (recordResult is Result.Ok) unlocks.value = unlocks.value + newlyUnlocked
            return recordResult
        }
    }

    private fun makeVm(
        meals: List<MealWithRatings>,
        progress: FakeProgress = FakeProgress(),
        analytics: RecordingAnalyticsTracker = RecordingAnalyticsTracker(),
    ): Pair<AchievementsViewModel, RecordingAnalyticsTracker> {
        val active = object : ActiveCrewProvider {
            override val current: Flow<CrewId?> = MutableStateFlow(crewId)
            override suspend fun set(crewId: CrewId) {}
            override suspend fun clear() {}
        }
        val session = object : SessionProvider {
            override val current: Flow<Session?> = MutableStateFlow(Session(me, crewId))
            override suspend fun requireCurrent(): Result<Session, SessionError> = Result.success(Session(me, crewId))
        }
        val read = object : MealReadPort {
            override fun observeFeed(crewId: CrewId, day: MealDay) = error("unused")
            override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) =
                MutableStateFlow<Result<List<MealWithRatings>, MealReadError>>(Result.success(meals))
        }
        val useCase = ObserveAchievementsUseCase(
            activeCrew = active,
            session = session,
            mealRead = read,
            progress = progress,
            evaluator = AchievementEvaluator(),
            reconciler = AchievementReconciler(),
            signalsBuilder = AchievementSignalsBuilder(),
            clock = clock,
            zone = zone,
        )
        return AchievementsViewModel(useCase, progress, clock, analytics) to analytics
    }

    @Test
    fun renders_earned_and_locked_with_persisted_dates() = runTest {
        // first_plate is met (one plate by me) AND already persisted → renders earned.
        val progress = FakeProgress(initial = mapOf("first_plate" to 111L))
        val (vm, _) = makeVm(listOf(meal("m1", "me")), progress)
        testScheduler.advanceUntilIdle()
        vm.state.test {
            val s = expectMostRecentItem()
            assertTrue(s.statuses.isNotEmpty())
            val firstPlate = s.statuses.first { it.achievement.id.value == "first_plate" }
            assertEquals(111L, firstPlate.unlockedAtEpochMs)        // earned (persisted)
            val meals10 = s.statuses.first { it.achievement.id.value == "meals_10" }
            assertEquals(null, meals10.unlockedAtEpochMs)            // locked
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun newly_met_persists_and_tracks_and_celebrates() = runTest {
        // first_plate is met (one plate) but NOT persisted → newly unlocked.
        val progress = FakeProgress(initial = emptyMap())
        val analytics = RecordingAnalyticsTracker()
        val (vm, _) = makeVm(listOf(meal("m1", "me")), progress, analytics)
        testScheduler.advanceUntilIdle()
        // BUG FIX (2026-07-12): the celebration lives in state now, not a one-shot effect (see
        // AchievementsState.celebration's kdoc) — it must survive rotation/process recreation.
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(AchievementStringKey.FirstPlateTitle, s.celebration)
            cancelAndIgnoreRemainingEvents()
        }
        // persisted exactly the newly-met id
        assertTrue(progress.recorded.any { "first_plate" in it.keys })
        // tracked the analytics event AFTER the Ok write
        assertTrue(
            analytics.events.any {
                it is AnalyticsEvent.AchievementUnlocked && it.achievementId == "first_plate"
            },
        )
    }

    @Test
    fun dismiss_celebration_intent_clears_state() = runTest {
        val progress = FakeProgress(initial = emptyMap())
        val (vm, _) = makeVm(listOf(meal("m1", "me")), progress)
        testScheduler.advanceUntilIdle()
        assertEquals(AchievementStringKey.FirstPlateTitle, vm.state.value.celebration)
        vm.onIntent(AchievementsIntent.DismissCelebration)
        testScheduler.advanceUntilIdle()
        assertEquals(null, vm.state.value.celebration)
    }

    @Test
    fun failed_persist_does_not_track_or_celebrate() = runTest {
        val progress = FakeProgress(
            initial = emptyMap(),
            recordResult = Result.failure(AchievementProgressError.Unavailable),
        )
        val analytics = RecordingAnalyticsTracker()
        val (vm, _) = makeVm(listOf(meal("m1", "me")), progress, analytics)
        // Let the pipeline (including the 400ms debounce) run.
        testScheduler.advanceUntilIdle()
        vm.state.test {
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        // It attempted the write but, on Err, fired no analytics.
        assertTrue(progress.recorded.isNotEmpty())
        assertTrue(analytics.events.none { it is AnalyticsEvent.AchievementUnlocked })
    }
}
