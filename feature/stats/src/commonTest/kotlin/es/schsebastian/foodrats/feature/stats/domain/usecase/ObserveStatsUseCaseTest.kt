package es.schsebastian.foodrats.feature.stats.domain.usecase

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.cuisine.Cuisine
import es.schsebastian.foodrats.core.domain.cuisine.CuisineReadPort
import es.schsebastian.foodrats.core.domain.cuisine.CuisineSlug
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
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
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class FakeSession(val session: Session?) : SessionProvider {
    val s = MutableStateFlow(session)
    override val current: Flow<Session?> = s
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session) else Result.failure(SessionError.NotSignedIn)
}

private class FakeActive(initial: CrewId?) : ActiveCrewProvider {
    val s = MutableStateFlow(initial)
    override val current = s
    override suspend fun set(crewId: CrewId) { s.value = crewId }
    override suspend fun clear() { s.value = null }
}

private class FakeRead(initial: List<MealWithRatings>, val err: MealReadError? = null) : MealReadPort {
    val flow = MutableStateFlow<Result<List<MealWithRatings>, MealReadError>>(
        if (err != null) Result.failure(err) else Result.success(initial),
    )
    fun update(value: List<MealWithRatings>) { flow.value = Result.success(value) }
    override fun observeFeed(crewId: CrewId, day: MealDay) = error("unused")
    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) = flow
}

private class FakeIngredientRead(
    private val catalog: Map<IngredientSlug, Ingredient> = emptyMap(),
) : IngredientReadPort {
    override fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>> = flowOf(catalog)
    override suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient> = emptyList()
    override suspend fun suggestForDish(dishSlug: String): List<IngredientSlug> = emptyList()
}

private class FakeCuisineRead(private val catalog: Map<CuisineSlug, Cuisine> = emptyMap()) : CuisineReadPort {
    override fun observeCatalog(): Flow<Map<CuisineSlug, Cuisine>> = flowOf(catalog)
    override suspend fun loadDishCuisine(dishSlug: String): CuisineSlug? = null
}

private class FixedClock(val instant: Instant) : Clock { override fun now() = instant }

/**
 * Serves the current window (any range spanning < ~60 days) and the historic 365-day window from
 * SEPARATE flows, so the historic read can fail while the current one stays OK. The historic range
 * is the one whose span exceeds 300 days.
 */
private class SplitRangeRead(
    current: List<MealWithRatings>,
) : MealReadPort {
    val currentFlow = MutableStateFlow<Result<List<MealWithRatings>, MealReadError>>(Result.success(current))
    val historicFlow = MutableStateFlow<Result<List<MealWithRatings>, MealReadError>>(Result.success(emptyList()))
    override fun observeFeed(crewId: CrewId, day: MealDay) = error("unused")
    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<Result<List<MealWithRatings>, MealReadError>> {
        val span = to.date.toEpochDays() - from.date.toEpochDays()
        return if (span > 300) historicFlow else currentFlow
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveStatsUseCaseTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 21)
    private val now = Instant.parse("2026-05-21T12:00:00Z")
    private val clock = FixedClock(now)
    private val me = (AccountId.of("me") as Result.Ok).value
    private val crewId = (CrewId.of("c-1") as Result.Ok).value

    private fun makeMeal(
        authorId: AccountId,
        date: LocalDate,
        ingredients: List<IngredientSlug> = emptyList(),
        detectedIngredients: List<IngredientSlug> = emptyList(),
    ): MealWithRatings {
        val meal = Meal(
            id = (MealId.of("m-${authorId.value}-${date}") as Result.Ok).value,
            author = MealAuthor(authorId, authorId.value, null),
            crewId = crewId,
            day = MealDay(date, zone),
            slot = MealSlot.Lunch,
            photoUrl = "u",
            dish = (DishName.of("Pasta") as Result.Ok).value,
            description = Description.EMPTY,
            publishedAt = now,
            ingredients = ingredients,
            detectedIngredients = detectedIngredients,
        )
        return MealWithRatings(meal, emptyList())
    }

    private fun slug(raw: String) = (IngredientSlug.of(raw) as Result.Ok).value

    @Test fun emits_NotSignedIn_when_no_session() = runTest {
        val uc = ObserveStatsUseCase(FakeActive(crewId), FakeSession(null), FakeRead(emptyList()), FakeIngredientRead(), FakeCuisineRead(), clock, zone)
        uc(flowOf(false), flowOf(0)).test {
            assertEquals(Result.failure(StatsError.Session.NotSignedIn), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun emits_NoActiveCrew_when_no_crew() = runTest {
        val uc = ObserveStatsUseCase(FakeActive(null), FakeSession(Session(me, null)), FakeRead(emptyList()), FakeIngredientRead(), FakeCuisineRead(), clock, zone)
        uc(flowOf(false), flowOf(0)).test {
            assertEquals(Result.failure(StatsError.Session.NoActiveCrew), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun ok_path_populates_week_and_month_hero_no_historic() = runTest {
        val mine = makeMeal(me, today)
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            FakeRead(listOf(mine)),
            FakeIngredientRead(),
            FakeCuisineRead(),
            clock,
            zone,
        )
        uc(flowOf(false), flowOf(0)).test {
            val r = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r)
            assertEquals(1, r.value.hero.personalStreak.days)
            assertEquals(1, r.value.hero.platesToday)
            assertEquals(1, r.value.week.totalMeals)
            assertEquals(1, r.value.month.totalMeals)
            assertNull(r.value.historic)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun historic_populates_when_enabled_flips_true() = runTest {
        val mine = makeMeal(me, today)
        val historicFlag = MutableStateFlow(false)
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            FakeRead(listOf(mine)),
            FakeIngredientRead(),
            FakeCuisineRead(),
            clock,
            zone,
        )
        uc(historicFlag, flowOf(0)).test {
            // first emission: historic null
            val r1 = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r1)
            assertNull(r1.value.historic)
            // flip flag → historic populated
            historicFlag.value = true
            val r2 = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r2)
            assertNotNull(r2.value.historic)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun epoch_increment_re_emits() = runTest {
        val mine = makeMeal(me, today)
        val epochFlow = MutableStateFlow(0)
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            FakeRead(listOf(mine)),
            FakeIngredientRead(),
            FakeCuisineRead(),
            clock,
            zone,
        )
        uc(flowOf(false), epochFlow).test {
            val r1 = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r1)
            epochFlow.value = 1
            val r2 = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun bingo_credits_confirmed_ingredients_only_not_AI_detected() = runTest {
        val tomato = slug("tomato")
        val basil = slug("basil")
        val mine = makeMeal(
            me,
            today,
            ingredients = listOf(tomato),       // confirmed → collected
            detectedIngredients = listOf(basil), // AI-only → must stay locked
        )
        val catalog = linkedMapOf(
            tomato to Ingredient(tomato, "Tomato", IngredientCategory.Vegetable),
            basil to Ingredient(basil, "Basil", IngredientCategory.Spice),
        )
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            FakeRead(listOf(mine)),
            FakeIngredientRead(catalog),
            FakeCuisineRead(),
            clock,
            zone,
        )
        uc(flowOf(false), flowOf(0)).test {
            val r = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r)
            val bingo = r.value.ingredientBingo
            assertNotNull(bingo)
            assertEquals(2, bingo.totalCount)
            assertEquals(1, bingo.collectedCount)
            assertEquals(true, bingo.cells.single { it.ingredient.slug == tomato }.collected)
            assertEquals(false, bingo.cells.single { it.ingredient.slug == basil }.collected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun bingo_is_null_when_ingredient_catalog_empty() = runTest {
        val mine = makeMeal(me, today, ingredients = listOf(slug("tomato")))
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            FakeRead(listOf(mine)),
            FakeIngredientRead(emptyMap()),
            FakeCuisineRead(),
            clock,
            zone,
        )
        uc(flowOf(false), flowOf(0)).test {
            val r = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r)
            assertNull(r.value.ingredientBingo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun current_error_propagates() = runTest {
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            FakeRead(emptyList(), MealReadError.Unauthorized),
            FakeIngredientRead(),
            FakeCuisineRead(),
            clock,
            zone,
        )
        uc(flowOf(false), flowOf(0)).test {
            assertEquals(Result.failure(StatsError.Read.Unauthorized), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // stats-02: a Historic-window read failure surfaces as StatsSnapshot.historicError instead of
    // being swallowed into a null historic window (which the UI can't tell from "not loaded yet").
    @Test fun historic_error_surfaces_as_historicError_not_swallowed() = runTest {
        val mine = makeMeal(me, today)
        // Current window OK throughout; only the historic 365-day read fails.
        val read = SplitRangeRead(listOf(mine))
        read.historicFlow.value = Result.failure(MealReadError.Unavailable)
        val historicFlag = MutableStateFlow(false)
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            read,
            FakeIngredientRead(),
            FakeCuisineRead(),
            clock,
            zone,
        )
        uc(historicFlag, flowOf(0)).test {
            val r1 = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r1)
            assertNull(r1.value.historicError)
            // Open Historic → the failed 365-day read must surface, not vanish into a null window.
            historicFlag.value = true
            val r2 = expectMostRecentItem()
            assertIs<Result.Ok<StatsSnapshot>>(r2)
            assertEquals(StatsError.Read.Unavailable, r2.value.historicError)
            assertNull(r2.value.historic)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
