package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.domain.config.FeatureFlagPort
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
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.preferences.AiPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.AiPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.ClassifyDraftPlateUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

/**
 * [ComposePlateScreen]'s non-removable "Gallery" provenance marker chip — the composer-side half
 * of the gallery-meal-photos feature (feed/detail already covered in `FeedMealUiTest`/
 * `FeedScreen.kt`). Only reachable once the plate's bytes decode into a real [ImageBitmap]
 * (`produceState` gates the whole hero + chip on a non-null decode), so this fixture writes an
 * actual encodable JPEG via `javax.imageio` rather than an arbitrary byte array.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ComposePlateScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val clock = object : Clock { override fun now() = Instant.parse("2026-05-24T12:00:00Z") }
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val account = (AccountId.of("acc-1") as Result.Ok).value

    /** A tiny, genuinely-decodable JPEG — `decodeImageBitmap` must succeed for the hero (and the
     *  gallery chip nested inside it) to render at all. */
    private fun realJpegBytes(): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    private fun draftWithPhoto(source: PlateSource) = MealDraft(
        audienceCrewIds = setOf(crew),
        authorId = account,
        day = MealDay(LocalDate(2026, 5, 24), zone),
        plates = listOf(Plate(realJpegBytes(), source = source)),
        dish = null,
        description = Description.EMPTY,
    )

    private class FakeCrewMembership(private val crews: List<CrewId>) : CrewMembershipPort {
        override fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>> =
            MutableStateFlow(crews.map { CrewSummary(it, "Crew ${it.value}") })
    }

    private class FakeClassifier : MealClassifierPort {
        override suspend fun classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError> =
            Result.success(emptyList())
    }

    private class FakeIngredients : IngredientReadPort {
        override fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>> = MutableStateFlow(emptyMap())
        override suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient> = emptyList()
        override suspend fun suggestForDish(dishSlug: String): List<IngredientSlug> = emptyList()
    }

    private class FakeFeatureFlags : FeatureFlagPort {
        override fun isMealAiEnabled(): Boolean = false
    }

    private fun viewModel(repo: FakeMealRepository) = ComposePlateViewModel(
        updateDraft = UpdateMealDraftUseCase(repo),
        repository = repo,
        crewMembership = FakeCrewMembership(listOf(crew)),
        uploadCoordinator = object : MealUploadCoordinator { override fun enqueueDraftUpload() {} },
        locationProvider = object : LocationProvider {
            override suspend fun current(): Result<Coordinates, LocationError> =
                Result.failure(LocationError.Unavailable)
        },
        classifyPlate = ClassifyDraftPlateUseCase(
            FakeClassifier(),
            FakeIngredients(),
            FakeFeatureFlags(),
            object : AiPreferencePort {
                override val enabled: Flow<Boolean> = flowOf(true)
                override suspend fun set(enabled: Boolean): Result<Unit, AiPreferenceError> = Result.success(Unit)
            },
        ),
        clock = clock,
        zone = zone,
    )

    @Test fun gallery_draft_shows_the_non_removable_gallery_chip_with_a11y_description() {
        val repo = FakeMealRepository().apply { runBlockingSaveDraft(draftWithPhoto(PlateSource.Gallery)) }
        val vm = viewModel(repo)

        rule.setContent {
            FoodRatsTheme {
                ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Gallery").assertExists()
        rule.onNodeWithContentDescription("Photo picked from the gallery").assertExists()
    }

    @Test fun camera_draft_does_not_show_the_gallery_chip() {
        val repo = FakeMealRepository().apply { runBlockingSaveDraft(draftWithPhoto(PlateSource.Camera)) }
        val vm = viewModel(repo)

        rule.setContent {
            FoodRatsTheme {
                ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Gallery").assertDoesNotExist()
        rule.onNodeWithContentDescription("Photo picked from the gallery").assertDoesNotExist()
    }

    /** [FakeMealRepository.saveDraft] is a suspend fun; the `apply {}` fixture builders above need
     *  a blocking call site (test setup, not production code) to seed the draft before `setContent`. */
    private fun FakeMealRepository.runBlockingSaveDraft(draft: MealDraft) {
        kotlinx.coroutines.runBlocking { saveDraft(draft) }
    }
}
