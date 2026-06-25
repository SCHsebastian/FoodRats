package es.schsebastian.foodrats.feature.meal.presentation.compose

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.config.FeatureFlagPort
import es.schsebastian.foodrats.core.domain.preferences.AiPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.AiPreferencePort
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.location.LocationError
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishLabel
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.ClassifyDraftPlateUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ComposePlateViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val clock = object : Clock { override fun now() = Instant.parse("2026-05-24T12:00:00Z") }
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val account = (AccountId.of("acc-1") as Result.Ok).value

    private fun bytes(s: String) = s.encodeToByteArray()

    private fun draftWithPhoto(label: String) = MealDraft(
        audienceCrewIds = setOf(crew),
        authorId = account,
        day = MealDay(LocalDate(2026, 5, 24), zone),
        plate = Plate(bytes(label)),
        dish = null,
        description = Description.EMPTY,
    )

    private suspend fun vmWith(
        repo: FakeMealRepository,
        classifyResult: (ByteArray) -> Result<List<DishLabel>, ClassifierError>,
        dishMap: Map<String, List<String>> = mapOf("pizza" to listOf("tomato", "cheese")),
        mealAiEnabled: Boolean = true,
        crews: List<CrewId> = listOf(crew),
    ): ComposePlateViewModel = ComposePlateViewModel(
        updateDraft = UpdateMealDraftUseCase(repo),
        repository = repo,
        crewMembership = FakeCrewMembership(crews),
        uploadCoordinator = object : MealUploadCoordinator { override fun enqueueDraftUpload() {} },
        locationProvider = object : LocationProvider {
            override suspend fun current(): Result<Coordinates, LocationError> =
                Result.failure(LocationError.Unavailable)
        },
        classifyPlate = ClassifyDraftPlateUseCase(
            FakeClassifier(classifyResult),
            FakeIngredients(dishMap),
            FakeFeatureFlags(mealAiEnabled),
            object : AiPreferencePort {
                override val enabled: Flow<Boolean> = flowOf(true)
                override suspend fun set(enabled: Boolean): Result<Unit, AiPreferenceError> = Result.success(Unit)
            },
        ),
        clock = clock,
        zone = zone,
    )

    @Test fun on_photo_classified_seeds_detected_only_not_confirmed() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        val vm = vmWith(repo, classifyResult = { Result.success(listOf(DishLabel("pizza", 0.9f))) })

        vm.onPhotoCaptured(bytes("plate"))

        vm.state.test {
            val st = expectMostRecentItem()
            assertFalse(st.classifying)
            assertEquals(listOf(IngredientSlug.of("tomato").getOrNull()!!, IngredientSlug.of("cheese").getOrNull()!!), st.detectedIngredients)
            // Detected ≠ confirmed: classification must NOT populate the confirmed list.
            assertEquals(emptyList(), st.draftIngredients)
        }
        // SetDetected stamps the detected set only; the user-confirmed `ingredients` stays empty.
        val draft = repo.observeDraft().first()!!
        assertEquals(listOf(IngredientSlug.of("tomato").getOrNull()!!, IngredientSlug.of("cheese").getOrNull()!!), draft.detectedIngredients)
        assertEquals(emptyList(), draft.ingredients)
    }

    @Test fun killswitch_off_skips_classification_no_detections_no_error() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        var classifierCalled = false
        val vm = vmWith(
            repo,
            classifyResult = { classifierCalled = true; Result.success(listOf(DishLabel("pizza", 0.9f))) },
            mealAiEnabled = false,
        )

        vm.onPhotoCaptured(bytes("plate"))

        vm.state.test {
            val st = expectMostRecentItem()
            assertFalse(st.classifying)
            // Kill-switch on: no detections surfaced and NO error/banner (advisory feature).
            assertEquals(emptyList(), st.detectedIngredients)
            assertEquals(null, st.classifierError)
        }
        assertFalse(classifierCalled, "kill-switch off must never invoke the on-device classifier")
        val draft = repo.observeDraft().first()!!
        assertEquals(emptyList(), draft.detectedIngredients)
        assertEquals(emptyList(), draft.ingredients)
    }

    @Test fun classifier_failure_surfaces_error_and_keeps_canContinue() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        val vm = vmWith(repo, classifyResult = { Result.failure(ClassifierError.Run.InferenceFailed) })
        // Make the form valid so canContinue is true before classification fails.
        vm.onIntent(ComposePlateIntent.DishChanged("Pizza"))

        vm.onPhotoCaptured(bytes("plate"))

        vm.state.test {
            val st = expectMostRecentItem()
            assertFalse(st.classifying)
            assertEquals(ClassifierError.Run.InferenceFailed, st.classifierError)
            assertTrue(st.canContinue, "classification is advisory — must not block publishing")
        }
    }

    @Test fun dish_too_long_blocks_continue_and_shows_too_long_message() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        val vm = vmWith(repo, classifyResult = { Result.success(listOf(DishLabel("pizza", 0.9f))) })

        vm.onIntent(ComposePlateIntent.DishChanged("x".repeat(DishName.MAX_LEN + 1)))

        vm.state.test {
            val st = expectMostRecentItem()
            assertTrue(st.dishTooLong)
            // The RIGHT message: "Keep the dish name short." (TooLong), not the blank "Tell us what you ate."
            assertEquals(MealError.Validation.TooLong, st.error)
            assertFalse(st.canContinue, "an over-length dish must block Continue")
        }
    }

    @Test fun valid_dish_clears_too_long_message() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        val vm = vmWith(repo, classifyResult = { Result.success(listOf(DishLabel("pizza", 0.9f))) })

        vm.onIntent(ComposePlateIntent.DishChanged("x".repeat(DishName.MAX_LEN + 1)))
        vm.onIntent(ComposePlateIntent.DishChanged("Pizza"))

        vm.state.test {
            val st = expectMostRecentItem()
            assertFalse(st.dishTooLong)
            assertEquals(null, st.error)
            assertTrue(st.canContinue, "a valid dish + photo + crew should allow Continue")
        }
    }

    @Test fun over_length_dish_on_confirm_maps_to_too_long_not_blank() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        val vm = vmWith(repo, classifyResult = { Result.success(listOf(DishLabel("pizza", 0.9f))) })

        vm.onIntent(ComposePlateIntent.DishChanged("x".repeat(DishName.MAX_LEN + 1)))
        vm.onIntent(ComposePlateIntent.RequestConfirm)

        vm.state.test {
            val st = expectMostRecentItem()
            // The submit path (persistDraft → DishName.of) must distinguish too-long from blank.
            assertEquals(MealError.Validation.TooLong, st.error)
            assertFalse(st.showConfirm, "a too-long dish must not open the publish confirm dialog")
        }
    }

    @Test fun re_capture_overwrites_manual_edits() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate-1")) }
        val vm = vmWith(
            repo,
            classifyResult = { jpeg ->
                when (jpeg.decodeToString()) {
                    "plate-1" -> Result.success(listOf(DishLabel("pizza", 0.9f)))
                    else -> Result.success(listOf(DishLabel("salad", 0.9f)))
                }
            },
            dishMap = mapOf("pizza" to listOf("tomato"), "salad" to listOf("lettuce", "olive")),
        )

        vm.onPhotoCaptured(bytes("plate-1"))
        // User trims the selection in the picker (writes through the draft port).
        repo.setIngredients(listOf(IngredientSlug.of("only-one").getOrNull()!!))
        // Re-capture a different plate.
        vm.onPhotoCaptured(bytes("plate-2"))

        vm.state.test {
            val st = expectMostRecentItem()
            // Re-capture overwrites the detected seed...
            assertEquals(listOf(IngredientSlug.of("lettuce").getOrNull()!!, IngredientSlug.of("olive").getOrNull()!!), st.detectedIngredients)
            // ...but the user's confirmed selection is left untouched (detected ≠ confirmed).
            assertEquals(listOf(IngredientSlug.of("only-one").getOrNull()!!), st.draftIngredients)
        }
    }

    @Test fun seeded_audience_subset_is_not_clobbered_to_all_crews() = runTest {
        // Regression: the composer is opened with the draft pre-seeded to ONE crew (the active
        // crew the user launched from), while they belong to three. loadCrewsAndCounts must keep
        // that subset, not reconcile it up to "all crews" — the bug was reading the transient empty
        // initial selection (observeDraft vs observeMyCrews race) and defaulting to all.
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val crew3 = (CrewId.of("crew-3") as Result.Ok).value
        val seeded = draftWithPhoto("plate").copy(audienceCrewIds = setOf(crew))
        val repo = FakeMealRepository().apply { saveDraft(seeded) }
        val vm = vmWith(repo, classifyResult = { Result.failure(ClassifierError.Run.InferenceFailed) }, crews = listOf(crew, crew2, crew3))

        vm.state.test {
            assertEquals(setOf(crew), expectMostRecentItem().selectedCrewIds)
        }
        // And the persisted draft audience stays the single seeded crew.
        assertEquals(setOf(crew), repo.observeDraft().first()!!.audienceCrewIds)
    }

    @Test fun seeded_audience_drops_a_left_crew_but_keeps_the_rest() = runTest {
        // The reconcile still does its job: a seeded crew the user is no longer a member of is
        // dropped from the audience (here crew3 is gone), without inflating to all crews.
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val crew3 = (CrewId.of("crew-3") as Result.Ok).value
        val seeded = draftWithPhoto("plate").copy(audienceCrewIds = setOf(crew, crew3))
        val repo = FakeMealRepository().apply { saveDraft(seeded) }
        val vm = vmWith(repo, classifyResult = { Result.failure(ClassifierError.Run.InferenceFailed) }, crews = listOf(crew, crew2))

        vm.state.test {
            assertEquals(setOf(crew), expectMostRecentItem().selectedCrewIds)
        }
        assertEquals(setOf(crew), repo.observeDraft().first()!!.audienceCrewIds)
    }

    private class FakeCrewMembership(private val crews: List<CrewId>) : CrewMembershipPort {
        constructor(crew: CrewId) : this(listOf(crew))
        override fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>> =
            MutableStateFlow(crews.map { CrewSummary(it, "Crew ${it.value}") })
    }

    private class FakeClassifier(
        private val result: (ByteArray) -> Result<List<DishLabel>, ClassifierError>,
    ) : MealClassifierPort {
        override suspend fun classify(jpeg: ByteArray) = result(jpeg)
    }

    private class FakeIngredients(private val dishMap: Map<String, List<String>>) : IngredientReadPort {
        override fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>> = MutableStateFlow(emptyMap())
        override suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient> = emptyList()
        override suspend fun suggestForDish(dishSlug: String): List<IngredientSlug> =
            dishMap[dishSlug].orEmpty().map { IngredientSlug.of(it).getOrNull()!! }
    }

    private class FakeFeatureFlags(private val mealAiEnabled: Boolean) : FeatureFlagPort {
        override fun isMealAiEnabled(): Boolean = mealAiEnabled
    }
}
