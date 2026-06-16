package es.schsebastian.foodrats.feature.achievements.domain.usecase

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressError
import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressPort
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
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.achievements.domain.AchievementEvaluator
import es.schsebastian.foodrats.feature.achievements.domain.AchievementReconciler
import es.schsebastian.foodrats.feature.achievements.domain.AchievementSignalsBuilder
import es.schsebastian.foodrats.feature.achievements.domain.acct
import es.schsebastian.foodrats.feature.achievements.domain.crew
import es.schsebastian.foodrats.feature.achievements.domain.error.AchievementError
import es.schsebastian.foodrats.feature.achievements.domain.meal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAchievementsUseCaseTest {

    private val zone = TimeZone.UTC
    private val clock = FixedClock(Instant.parse("2026-06-14T12:00:00Z"))
    private val me: AccountId = acct("me")
    private val crewId: CrewId = crew("c-1")

    private fun fakeActiveCrew(value: CrewId?) = object : ActiveCrewProvider {
        override val current: Flow<CrewId?> = MutableStateFlow(value)
        override suspend fun set(crewId: CrewId) {}
        override suspend fun clear() {}
    }

    private fun fakeSession(value: Session?) = object : SessionProvider {
        override val current: Flow<Session?> = MutableStateFlow(value)
        override suspend fun requireCurrent(): Result<Session, SessionError> =
            value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
    }

    private fun fakeMealRead(
        rangeResult: Result<List<MealWithRatings>, MealReadError>,
    ) = object : MealReadPort {
        override fun observeFeed(crewId: CrewId, day: MealDay) = error("unused")
        override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) =
            MutableStateFlow(rangeResult)
    }

    private class FakeProgress(
        unlocksResult: Result<Map<String, Long>, AchievementProgressError>,
    ) : AchievementProgressPort {
        val unlocks = MutableStateFlow(unlocksResult)
        override fun observeUnlocks(accountId: AccountId): Flow<Result<Map<String, Long>, AchievementProgressError>> =
            unlocks
        override suspend fun recordUnlocks(
            accountId: AccountId,
            newlyUnlocked: Map<String, Long>,
        ): Result<Unit, AchievementProgressError> = Result.success(Unit)
    }

    private fun useCase(
        activeCrew: ActiveCrewProvider,
        session: SessionProvider,
        mealRead: MealReadPort,
        progress: AchievementProgressPort,
    ) = ObserveAchievementsUseCase(
        activeCrew = activeCrew,
        session = session,
        mealRead = mealRead,
        progress = progress,
        evaluator = AchievementEvaluator(),
        reconciler = AchievementReconciler(),
        signalsBuilder = AchievementSignalsBuilder(),
        clock = clock,
        zone = zone,
    )

    @Test
    fun no_session_emits_not_signed_in() = runTest {
        val uc = useCase(
            activeCrew = fakeActiveCrew(crewId),
            session = fakeSession(null),
            mealRead = fakeMealRead(Result.success(emptyList())),
            progress = FakeProgress(Result.success(emptyMap())),
        )
        uc().test {
            testScheduler.advanceUntilIdle()
            assertEquals(Result.failure(AchievementError.Session.NotSignedIn), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun no_active_crew_emits_no_active_crew() = runTest {
        val uc = useCase(
            activeCrew = fakeActiveCrew(null),
            session = fakeSession(Session(me, null)),
            mealRead = fakeMealRead(Result.success(emptyList())),
            progress = FakeProgress(Result.success(emptyMap())),
        )
        uc().test {
            testScheduler.advanceUntilIdle()
            assertEquals(Result.failure(AchievementError.Session.NoActiveCrew), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun meal_read_unauthorized_maps_to_read_unauthorized() = runTest {
        val uc = useCase(
            activeCrew = fakeActiveCrew(crewId),
            session = fakeSession(Session(me, crewId)),
            mealRead = fakeMealRead(Result.failure(MealReadError.Unauthorized)),
            progress = FakeProgress(Result.success(emptyMap())),
        )
        uc().test {
            testScheduler.advanceUntilIdle()
            assertEquals(Result.failure(AchievementError.Read.Unauthorized), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun meal_read_crew_not_found_maps_to_read_unavailable() = runTest {
        val uc = useCase(
            activeCrew = fakeActiveCrew(crewId),
            session = fakeSession(Session(me, crewId)),
            mealRead = fakeMealRead(Result.failure(MealReadError.CrewNotFound)),
            progress = FakeProgress(Result.success(emptyMap())),
        )
        uc().test {
            testScheduler.advanceUntilIdle()
            assertEquals(Result.failure(AchievementError.Read.Unavailable), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun meal_read_unavailable_maps_to_read_unavailable() = runTest {
        val uc = useCase(
            activeCrew = fakeActiveCrew(crewId),
            session = fakeSession(Session(me, crewId)),
            mealRead = fakeMealRead(Result.failure(MealReadError.Unavailable)),
            progress = FakeProgress(Result.success(emptyMap())),
        )
        uc().test {
            testScheduler.advanceUntilIdle()
            assertEquals(Result.failure(AchievementError.Read.Unavailable), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun progress_unauthorized_maps_to_read_unauthorized() = runTest {
        val uc = useCase(
            activeCrew = fakeActiveCrew(crewId),
            session = fakeSession(Session(me, crewId)),
            mealRead = fakeMealRead(Result.success(emptyList())),
            progress = FakeProgress(Result.failure(AchievementProgressError.Unauthorized)),
        )
        uc().test {
            testScheduler.advanceUntilIdle()
            assertEquals(Result.failure(AchievementError.Read.Unauthorized), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun progress_unavailable_maps_to_read_unavailable() = runTest {
        val uc = useCase(
            activeCrew = fakeActiveCrew(crewId),
            session = fakeSession(Session(me, crewId)),
            mealRead = fakeMealRead(Result.success(emptyList())),
            progress = FakeProgress(Result.failure(AchievementProgressError.Unavailable)),
        )
        uc().test {
            testScheduler.advanceUntilIdle()
            assertEquals(Result.failure(AchievementError.Read.Unavailable), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun success_emits_snapshot_with_account_id() = runTest {
        val uc = useCase(
            activeCrew = fakeActiveCrew(crewId),
            session = fakeSession(Session(me, crewId)),
            mealRead = fakeMealRead(Result.success(listOf(meal("m1", "me")))),
            progress = FakeProgress(Result.success(emptyMap())),
        )
        uc().test {
            testScheduler.advanceUntilIdle()
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok)
            assertEquals(me, result.value.accountId)
            assertTrue(result.value.statuses.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun debounce_collapses_rapid_emissions_into_one() = runTest {
        val progress = FakeProgress(Result.success(emptyMap()))
        val uc = useCase(
            activeCrew = fakeActiveCrew(crewId),
            session = fakeSession(Session(me, crewId)),
            mealRead = fakeMealRead(Result.success(listOf(meal("m1", "me")))),
            progress = progress,
        )
        uc().test {
            // Three rapid upstream changes within the 400ms debounce window: only the last survives.
            progress.unlocks.value = Result.success(mapOf("first_plate" to 1L))
            progress.unlocks.value = Result.success(mapOf("first_plate" to 2L))
            progress.unlocks.value = Result.success(mapOf("first_plate" to 3L))
            testScheduler.advanceUntilIdle()
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok)
            assertEquals(me, result.value.accountId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
