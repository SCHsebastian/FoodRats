package es.schsebastian.foodrats.feature.stats.presentation.stats

import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.share.RecordingStoryShareController
import es.schsebastian.foodrats.core.data.share.StoryShareOutcome
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsValue
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
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
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    /** Shared with the use case's `computeDispatcher` so `flowOn` stays synchronous in tests. */
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 21)
    private val now = Instant.parse("2026-05-21T12:00:00Z")
    private val clock = object : Clock { override fun now() = now }
    private val me = (AccountId.of("me") as Result.Ok).value
    private val crew = (CrewId.of("c") as Result.Ok).value

    private fun makeMealMine(
        id: String = "m",
        cuisine: CuisineSlug? = null,
        ingredients: List<IngredientSlug> = emptyList(),
        detectedIngredients: List<IngredientSlug> = emptyList(),
    ): MealWithRatings {
        val meal = Meal(
            id = (MealId.of(id) as Result.Ok).value,
            author = MealAuthor(me, "Me", null),
            crewId = crew,
            day = MealDay(today, zone),
            slot = MealSlot.Lunch,
            photoUrl = "u",
            dish = (DishName.of("Pasta") as Result.Ok).value,
            description = Description.EMPTY,
            publishedAt = now,
            ingredients = ingredients,
            detectedIngredients = detectedIngredients,
            cuisine = cuisine,
        )
        return MealWithRatings(meal, emptyList())
    }

    private fun cuisine(slug: String, name: String = slug) =
        Cuisine((CuisineSlug.of(slug) as Result.Ok).value, name, slug)

    private fun slug(raw: String) = (IngredientSlug.of(raw) as Result.Ok).value

    private fun ingredient(slug: String, name: String = slug) =
        Ingredient((IngredientSlug.of(slug) as Result.Ok).value, name, IngredientCategory.Vegetable)

    /** A meal authored by [otherId] WITH one rating from [me], so the window has a non-null best plate. */
    private fun makeRatedMeal(
        id: String = "rated",
        otherId: AccountId = (AccountId.of("chef") as Result.Ok).value,
    ): MealWithRatings {
        val meal = Meal(
            id = (MealId.of(id) as Result.Ok).value,
            author = MealAuthor(otherId, "Chef", null),
            crewId = crew,
            day = MealDay(today, zone),
            slot = MealSlot.Lunch,
            photoUrl = "photo-url-$id",
            dish = (DishName.of("Lasagna") as Result.Ok).value,
            description = Description.EMPTY,
            publishedAt = now,
        )
        val rating = es.schsebastian.foodrats.core.domain.meal.MealRating(
            raterId = me,
            raterDisplayName = "Me",
            raterAvatarUrl = null,
            score = (es.schsebastian.foodrats.core.domain.meal.Score.of(5) as Result.Ok).value,
            ratedAt = now,
        )
        return MealWithRatings(meal, listOf(rating))
    }

    private fun makeVm(
        meals: List<MealWithRatings> = listOf(makeMealMine()),
        cuisineCatalog: Map<CuisineSlug, Cuisine> = emptyMap(),
        ingredientCatalog: Map<IngredientSlug, Ingredient> = emptyMap(),
        shareController: RecordingStoryShareController = RecordingStoryShareController(),
        analytics: RecordingAnalyticsTracker = RecordingAnalyticsTracker(),
    ): StatsViewModel {
        val mealsFlow = MutableStateFlow<Result<List<MealWithRatings>, MealReadError>>(
            Result.success(meals),
        )
        val active = object : ActiveCrewProvider {
            override val current: Flow<CrewId?> = MutableStateFlow(crew)
            override suspend fun set(crewId: CrewId) {}
            override suspend fun clear() {}
        }
        val session = object : SessionProvider {
            override val current: Flow<Session?> = MutableStateFlow(Session(me, null))
            override suspend fun requireCurrent(): Result<Session, SessionError> =
                Result.success(Session(me, null))
        }
        val read = object : MealReadPort {
            override fun observeFeed(crewId: CrewId, day: MealDay) = error("unused")
            override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) = mealsFlow
        }
        val ingredientRead = object : IngredientReadPort {
            override fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>> = MutableStateFlow(ingredientCatalog)
            override suspend fun findBySlugs(slugs: Set<IngredientSlug>) = emptyList<Ingredient>()
            override suspend fun suggestForDish(dishSlug: String) = emptyList<IngredientSlug>()
        }
        val cuisineRead = object : CuisineReadPort {
            override fun observeCatalog(): Flow<Map<CuisineSlug, Cuisine>> = MutableStateFlow(cuisineCatalog)
            override suspend fun loadDishCuisine(dishSlug: String): CuisineSlug? = null
        }
        val blockedAccounts = object : es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort {
            override fun observeBlocked(owner: es.schsebastian.foodrats.core.domain.model.AccountId): Flow<Set<es.schsebastian.foodrats.core.domain.model.AccountId>> =
                MutableStateFlow(emptySet())
            override suspend fun block(owner: es.schsebastian.foodrats.core.domain.model.AccountId, target: es.schsebastian.foodrats.core.domain.model.AccountId) =
                es.schsebastian.foodrats.core.domain.result.Result.success(Unit)
            override suspend fun unblock(owner: es.schsebastian.foodrats.core.domain.model.AccountId, target: es.schsebastian.foodrats.core.domain.model.AccountId) =
                es.schsebastian.foodrats.core.domain.result.Result.success(Unit)
        }
        val uploadProgress = object : MealUploadProgressPort {
            override val status: MutableStateFlow<MealUploadStatus> =
                MutableStateFlow(MealUploadStatus.Idle)
            override val queue = MealUploadProgressPort.DEFAULT_QUEUE
        }
        return StatsViewModel(
            observeStats = ObserveStatsUseCase(
                active, session, read, ingredientRead, cuisineRead, blockedAccounts, clock, zone,
                computeDispatcher = mainDispatcher,
            ),
            uploadProgress = uploadProgress,
            storyShareController = shareController,
            clock = clock,
            zone = zone,
            analytics = analytics,
        )
    }

    @Test fun initial_state_loads_snapshot_for_week_tab() = runTest {
        val vm = makeVm()
        vm.state.test {
            val s = expectMostRecentItem()
            assertNotNull(s.snapshot)
            assertEquals(Tab.Week, s.selectedTab)
            assertEquals(1, s.snapshot!!.hero.personalStreak.days)
            assertEquals(1, s.snapshot!!.week.totalMeals)
            assertNull(s.snapshot!!.historic)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun selecting_historic_tab_loads_historic_window() = runTest {
        val vm = makeVm()
        vm.onIntent(StatsIntent.SelectTab(Tab.Historic))
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(Tab.Historic, s.selectedTab)
            assertNotNull(s.snapshot)
            assertNotNull(s.snapshot!!.historic)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun refresh_increments_epoch() = runTest {
        val vm = makeVm()
        val initial = vm.state.value.epoch
        vm.onIntent(StatsIntent.Refresh)
        assertEquals(initial + 1, vm.state.value.epoch)
    }

    @Test fun passport_marks_collected_and_locked_cells_with_progress() = runTest {
        val italian = (CuisineSlug.of("italian") as Result.Ok).value
        val japanese = (CuisineSlug.of("japanese") as Result.Ok).value
        val vm = makeVm(
            // One own meal stamped italian; japanese in the catalog but never cooked.
            meals = listOf(makeMealMine(id = "m1", cuisine = italian)),
            cuisineCatalog = linkedMapOf(
                italian to cuisine("italian", "Italian"),
                japanese to cuisine("japanese", "Japanese"),
            ),
        )
        vm.state.test {
            val s = expectMostRecentItem()
            val passport = s.snapshot?.cuisinePassport
            assertNotNull(passport)
            assertEquals(2, passport.totalCount)
            assertEquals(1, passport.collectedCount)
            assertEquals(true, passport.cells.first { it.cuisine.slug == italian }.collected)
            assertEquals(false, passport.cells.first { it.cuisine.slug == japanese }.collected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun passport_is_null_when_cuisine_catalog_empty() = runTest {
        val vm = makeVm(cuisineCatalog = emptyMap())
        vm.state.test {
            val s = expectMostRecentItem()
            assertNotNull(s.snapshot)
            assertNull(s.snapshot!!.cuisinePassport)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun bingo_marks_collected_and_locked_cells_with_progress() = runTest {
        val tomato = slug("tomato")
        val basil = slug("basil")
        val garlic = slug("garlic")
        val vm = makeVm(
            // One own meal CONFIRMS tomato; basil is only an AI detection (must stay locked); garlic
            // is in the catalog but never used.
            meals = listOf(
                makeMealMine(
                    id = "m1",
                    ingredients = listOf(tomato),
                    detectedIngredients = listOf(basil),
                ),
            ),
            ingredientCatalog = linkedMapOf(
                tomato to ingredient("tomato", "Tomato"),
                basil to ingredient("basil", "Basil"),
                garlic to ingredient("garlic", "Garlic"),
            ),
        )
        vm.state.test {
            val s = expectMostRecentItem()
            val bingo = s.snapshot?.ingredientBingo
            assertNotNull(bingo)
            assertEquals(3, bingo.totalCount)
            assertEquals(1, bingo.collectedCount)
            assertEquals(true, bingo.cells.first { it.ingredient.slug == tomato }.collected)
            assertEquals(false, bingo.cells.first { it.ingredient.slug == basil }.collected)
            assertEquals(false, bingo.cells.first { it.ingredient.slug == garlic }.collected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun bingo_is_null_when_ingredient_catalog_empty() = runTest {
        val vm = makeVm(ingredientCatalog = emptyMap())
        vm.state.test {
            val s = expectMostRecentItem()
            assertNotNull(s.snapshot)
            assertNull(s.snapshot!!.ingredientBingo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ───────────────────────────── analytics: streak / leaderboard views ─────────────────────────────

    @Test fun streak_viewed_fires_once_on_init_default_week() = runTest {
        val analytics = RecordingAnalyticsTracker()
        makeVm(analytics = analytics)
        assertEquals(
            1,
            analytics.events.filterIsInstance<AnalyticsEvent.StreakViewed>().size,
        )
        // Landing on Week must NOT emit a leaderboard view.
        assertEquals(
            0,
            analytics.events.filterIsInstance<AnalyticsEvent.LeaderboardViewed>().size,
        )
    }

    @Test fun leaderboard_viewed_fires_on_first_select_month_and_historic() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = makeVm(analytics = analytics)
        vm.onIntent(StatsIntent.SelectTab(Tab.Month))
        vm.onIntent(StatsIntent.SelectTab(Tab.Historic))
        assertEquals(
            2,
            analytics.events.filterIsInstance<AnalyticsEvent.LeaderboardViewed>().size,
        )
        assertEquals("leaderboard_viewed", AnalyticsEvent.LeaderboardViewed.name)
    }

    @Test fun reselecting_an_already_viewed_leaderboard_tab_does_not_refire() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = makeVm(analytics = analytics)
        vm.onIntent(StatsIntent.SelectTab(Tab.Month))
        vm.onIntent(StatsIntent.SelectTab(Tab.Week))
        vm.onIntent(StatsIntent.SelectTab(Tab.Month))
        assertEquals(
            1,
            analytics.events.filterIsInstance<AnalyticsEvent.LeaderboardViewed>().size,
        )
    }

    // ───────────────────────────── share (spec §8.2 / §12) ─────────────────────────────

    @Test fun sharing_streak_invokes_launcher_and_fires_streak_event_on_success() = runTest {
        val share = RecordingStoryShareController(outcome = StoryShareOutcome.OpenedInstagram)
        val analytics = RecordingAnalyticsTracker()
        val vm = makeVm(shareController = share, analytics = analytics)
        vm.onIntent(StatsIntent.ShareStreakTapped)
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(false, s.isPreparingShare)
            assertEquals(ShareOutcomeUi.Succeeded, s.shareOutcome)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, share.callCount)
        assertNull(share.lastCall!!.plateUrl) // streak card has no photo
        val event = analytics.events.filterIsInstance<AnalyticsEvent.StreakShared>().single()
        assertEquals("share", event.name)
        assertEquals(AnalyticsValue.Text("1"), event.params["item_id"]) // makeVm seeds a 1-day streak; item_id is text like every *Shared event
    }

    @Test fun sharing_streak_does_not_fire_event_on_failed_outcome() = runTest {
        val share = RecordingStoryShareController(outcome = StoryShareOutcome.Failed)
        val analytics = RecordingAnalyticsTracker()
        val vm = makeVm(shareController = share, analytics = analytics)
        vm.onIntent(StatsIntent.ShareStreakTapped)
        vm.state.test {
            assertEquals(ShareOutcomeUi.Failed, expectMostRecentItem().shareOutcome)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, share.callCount)
        assertEquals(0, analytics.events.filterIsInstance<AnalyticsEvent.StreakShared>().size)
    }

    @Test fun sharing_award_decodes_plate_url_and_fires_award_event() = runTest {
        val share = RecordingStoryShareController(outcome = StoryShareOutcome.OpenedFallbackSheet)
        val analytics = RecordingAnalyticsTracker()
        val vm = makeVm(meals = listOf(makeRatedMeal(id = "rated")), shareController = share, analytics = analytics)
        // Read the best plate's id from the loaded snapshot, then share it.
        val mealId = vm.state.value.snapshot!!.week.bestMeal!!.mealId.value
        vm.onIntent(StatsIntent.ShareAwardTapped(mealId))
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(false, s.isPreparingShare)
            assertEquals(ShareOutcomeUi.OpenedSheet, s.shareOutcome)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, share.callCount)
        assertEquals("photo-url-rated", share.lastCall!!.plateUrl)
        val event = analytics.events.filterIsInstance<AnalyticsEvent.AwardShared>().single()
        assertEquals("share", event.name)
        assertEquals(AnalyticsValue.Text(mealId), event.params["item_id"])
    }
}
