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
    ): ComposePlateViewModel = ComposePlateViewModel(
        updateDraft = UpdateMealDraftUseCase(repo),
        repository = repo,
        crewMembership = FakeCrewMembership(crew),
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

    private class FakeCrewMembership(private val crew: CrewId) : CrewMembershipPort {
        override fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>> =
            MutableStateFlow(listOf(CrewSummary(crew, "Crew ${crew.value}")))
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
