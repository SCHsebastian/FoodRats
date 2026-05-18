package es.schsebastian.foodrats.feature.feed.domain.usecase

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
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
    private val perDay: Map<Pair<CrewId, String>, List<Meal>> = emptyMap(),
    private val readError: MealReadError? = null,
) : MealReadPort {
    override fun observeFeed(crewId: CrewId, day: MealDay): Flow<Result<List<Meal>, MealReadError>> {
        val err = readError
        return MutableStateFlow(Unit).map {
            if (err != null) Result.failure(err)
            else Result.success(perDay[crewId to day.toKey()].orEmpty())
        }
    }
    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) =
        observeFeed(crewId, from)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFeedUseCaseTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 16)
    private val day = FeedDay(MealDay(today, zone))
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val sampleMeal = Meal(
        id = (MealId.of("m-1") as Result.Ok).value,
        author = MealAuthor((AccountId.of("u-1") as Result.Ok).value, "Sam", null),
        crewId = crew,
        day = MealDay(today, zone),
        slot = MealSlot.Lunch,
        photoUrl = "https://example/p.jpg",
        score = (Score.of(8) as Result.Ok).value,
        dish = (DishName.of("Pasta") as Result.Ok).value,
        tags = emptyList(),
        publishedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    @Test fun no_active_crew_emits_NoActiveCrew() = runTest {
        val active = FakeActiveCrewProvider(initial = null)
        val port = FakeMealReadPort()
        val daysFlow = MutableStateFlow(day)
        ObserveFeedUseCase(active, port).invoke(daysFlow).test {
            assertEquals(Result.failure(FeedError.Session.NoActiveCrew), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun active_crew_emits_meals_for_day() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = mapOf((crew to day.day.toKey()) to listOf(sampleMeal)))
        val daysFlow = MutableStateFlow(day)
        ObserveFeedUseCase(active, port).invoke(daysFlow).test {
            val r = awaitItem()
            assertIs<Result.Ok<List<Meal>>>(r)
            assertEquals(listOf(sampleMeal), r.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun read_error_maps_to_Read_error() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(readError = MealReadError.Unauthorized)
        val daysFlow = MutableStateFlow(day)
        ObserveFeedUseCase(active, port).invoke(daysFlow).test {
            assertEquals(Result.failure(FeedError.Read.Unauthorized), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
