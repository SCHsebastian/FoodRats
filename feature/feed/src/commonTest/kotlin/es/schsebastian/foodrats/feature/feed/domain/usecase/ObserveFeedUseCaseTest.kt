package es.schsebastian.foodrats.feature.feed.domain.usecase

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FakeActiveCrewProvider(initial: CrewId? = null) : ActiveCrewProvider {
    val state = MutableStateFlow(initial)
    override val current: Flow<CrewId?> = state
    override suspend fun set(crewId: CrewId) { state.value = crewId }
    override suspend fun clear() { state.value = null }
}

class FakeMealReadPort(
    private val perDay: Map<Pair<CrewId, String>, List<MealWithRatings>> = emptyMap(),
    private val readError: MealReadError? = null,
) : MealReadPort {
    override fun observeFeed(crewId: CrewId, day: MealDay): Flow<Result<List<MealWithRatings>, MealReadError>> {
        val err = readError
        return MutableStateFlow(Unit).map {
            if (err != null) Result.failure(err)
            else Result.success(perDay[crewId to day.toKey()].orEmpty())
        }
    }
    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) =
        observeFeed(crewId, from)
}

/**
 * Shared test double for [BlockedAccountsPort]. [blocked] is the live blocked set the viewer sees;
 * mutate it to verify blocked-author content disappears reactively. Used across the feed test suite.
 */
class FakeBlockedAccountsPort(initial: Set<AccountId> = emptySet()) : BlockedAccountsPort {
    val blocked = MutableStateFlow(initial)
    val blockCalls = mutableListOf<Pair<AccountId, AccountId>>()
    val unblockCalls = mutableListOf<Pair<AccountId, AccountId>>()
    override fun observeBlocked(owner: AccountId): Flow<Set<AccountId>> = blocked
    override suspend fun block(owner: AccountId, target: AccountId): Result<Unit, BlockError> {
        blockCalls += owner to target
        blocked.value = blocked.value + target
        return Result.success(Unit)
    }
    override suspend fun unblock(owner: AccountId, target: AccountId): Result<Unit, BlockError> {
        unblockCalls += owner to target
        blocked.value = blocked.value - target
        return Result.success(Unit)
    }
}

/** Emits a fixed (possibly null) [Session]; sufficient for the blocked-set derivation in feed reads. */
class FixedSessionProvider(private val session: Session?) : SessionProvider {
    override val current: Flow<Session?> = MutableStateFlow(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        session?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFeedUseCaseTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 16)
    private val day = FeedDay(MealDay(today, zone))
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val viewer = (AccountId.of("u-viewer") as Result.Ok).value
    private val session = FixedSessionProvider(Session(viewer, crew))
    private fun blocked(ids: Set<AccountId> = emptySet()) = FakeBlockedAccountsPort(ids)
    private val sampleMeal = Meal(
        id = (MealId.of("m-1") as Result.Ok).value,
        author = MealAuthor((AccountId.of("u-1") as Result.Ok).value, "Sam", null),
        crewId = crew,
        day = MealDay(today, zone),
        slot = MealSlot.Lunch,
        photoUrl = "https://example/p.jpg",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )
    private val sampleMealWithRatings = MealWithRatings(sampleMeal, emptyList())

    @Test fun no_active_crew_emits_NoActiveCrew() = runTest {
        val active = FakeActiveCrewProvider(initial = null)
        val port = FakeMealReadPort()
        val daysFlow = MutableStateFlow(day)
        ObserveFeedUseCase(active, port, session, blocked()).invoke(daysFlow).test {
            assertEquals(Result.failure(FeedError.Session.NoActiveCrew), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun active_crew_emits_meals_for_day() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = mapOf((crew to day.day.toKey()) to listOf(sampleMealWithRatings)))
        val daysFlow = MutableStateFlow(day)
        ObserveFeedUseCase(active, port, session, blocked()).invoke(daysFlow).test {
            val r = awaitItem()
            assertIs<Result.Ok<List<MealWithRatings>>>(r)
            assertEquals(listOf(sampleMealWithRatings), r.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun read_error_maps_to_Read_error() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(readError = MealReadError.Unauthorized)
        val daysFlow = MutableStateFlow(day)
        ObserveFeedUseCase(active, port, session, blocked()).invoke(daysFlow).test {
            assertEquals(Result.failure(FeedError.Read.Unauthorized), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun blocked_authors_meals_are_filtered_out() = runTest {
        val blockedAuthor = (AccountId.of("u-1") as Result.Ok).value // author of sampleMeal
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = mapOf((crew to day.day.toKey()) to listOf(sampleMealWithRatings)))
        val daysFlow = MutableStateFlow(day)
        ObserveFeedUseCase(active, port, session, blocked(setOf(blockedAuthor))).invoke(daysFlow).test {
            val r = awaitItem()
            assertIs<Result.Ok<List<MealWithRatings>>>(r)
            assertEquals(emptyList(), r.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun blocking_an_author_hides_their_meals_reactively() = runTest {
        val blockedAuthor = (AccountId.of("u-1") as Result.Ok).value
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = mapOf((crew to day.day.toKey()) to listOf(sampleMealWithRatings)))
        val daysFlow = MutableStateFlow(day)
        val blocks = FakeBlockedAccountsPort()
        ObserveFeedUseCase(active, port, session, blocks).invoke(daysFlow).test {
            assertEquals(listOf(sampleMealWithRatings), assertIs<Result.Ok<List<MealWithRatings>>>(awaitItem()).value)
            blocks.blocked.value = setOf(blockedAuthor)
            assertEquals(emptyList(), assertIs<Result.Ok<List<MealWithRatings>>>(awaitItem()).value)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
