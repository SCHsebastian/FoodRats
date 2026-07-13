package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
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
import kotlin.test.assertEquals
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

    /**
     * A multi-photo draft for the strip tests below. Plain distinguishable byte arrays (not a real
     * JPEG) are fine here: the strip's tile Box/click-target/semantics render unconditionally, and
     * only the thumbnail `Image` itself is gated on a successful `decodeImageBitmap` — none of the
     * strip assertions (position, selection, counter, gallery marker) depend on that decode.
     */
    private fun draftWithPhotos(vararg sources: PlateSource) = MealDraft(
        audienceCrewIds = setOf(crew),
        authorId = account,
        day = MealDay(LocalDate(2026, 5, 24), zone),
        plates = sources.mapIndexed { i, source -> Plate(byteArrayOf(i.toByte()), source = source) },
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
        // A single-photo gallery draft now carries the marker TWICE (Wave 3): once on the hero (the
        // pre-existing chip asserted above via its visible "Gallery" label) and once as the strip
        // tile's own mini marker (decision: "strip tiles carry their own mini marker") — both reuse
        // this same a11y string since they describe the identical fact about the identical photo.
        rule.onAllNodesWithContentDescription("Photo picked from the gallery").assertCountEquals(2)
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

    // ── multi-photo strip (Wave 3) ─────────────────────────────────────────

    @Test fun photo_strip_renders_every_draft_photo_in_order_with_a_counter() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera, PlateSource.Camera, PlateSource.Camera))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        rule.onNodeWithText("3 / ${MealPublishPolicy.MAX_PHOTOS_PER_MEAL}").assertExists()
        rule.onNodeWithContentDescription("Photo 1 of 3").assertExists()
        rule.onNodeWithContentDescription("Photo 2 of 3").assertExists()
        rule.onNodeWithContentDescription("Photo 3 of 3").assertExists()
        // No phantom 4th tile.
        rule.onNodeWithContentDescription("Photo 4 of 3").assertDoesNotExist()
    }

    @Test fun add_tile_is_shown_while_under_the_photo_cap() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera, PlateSource.Camera))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Add another photo").assertExists()
    }

    @Test fun add_tile_is_hidden_once_the_photo_cap_is_reached() {
        val atCap = Array(MealPublishPolicy.MAX_PHOTOS_PER_MEAL) { PlateSource.Camera }
        val repo = FakeMealRepository().apply { runBlockingSaveDraft(draftWithPhotos(*atCap)) }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        rule.onNodeWithText("${MealPublishPolicy.MAX_PHOTOS_PER_MEAL} / ${MealPublishPolicy.MAX_PHOTOS_PER_MEAL}").assertExists()
        rule.onNodeWithContentDescription("Add another photo").assertDoesNotExist()
    }

    @Test fun add_tile_opens_a_camera_gallery_chooser_that_dismisses_on_either_action_without_crashing() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Add another photo").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Take a photo").assertExists()
        rule.onNodeWithText("Choose from gallery").assertExists()

        // Tapping either action calls the real Android rememberPhotoPicker actual (via the host
        // Activity's ActivityResultRegistry, same as CaptureMealScreenTest's gallery-action test) and
        // immediately dismisses the chooser — proving the tap is wired through rather than a dead button.
        rule.onNodeWithText("Choose from gallery").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Take a photo").assertDoesNotExist()
    }

    @Test fun add_tile_chooser_camera_action_dismisses_without_crashing() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Add another photo").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Take a photo").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Choose from gallery").assertDoesNotExist()
    }

    @Test fun chooser_cancel_exists_only_while_the_add_photo_chooser_is_open() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        // Chooser CLOSED: its "Cancel" action (a TEXT pill inside the Dialog) must not exist
        // anywhere. The screen's only Cancel-labeled node is the PRE-EXISTING pinned bottom-bar
        // close button, which carries "Cancel" as a contentDescription — locked to exactly ONE so
        // an unconditionally-rendered chooser (or any stray overlay) fails this count. Regression
        // lock for the 2026-07-13 emulator smoke-walk report, which mistook that pinned close
        // button (screen-anchored by design; predates Wave 3 — ce42be6) for a stray chooser Cancel.
        rule.onNodeWithText("Cancel").assertDoesNotExist()
        rule.onAllNodesWithContentDescription("Cancel").assertCountEquals(1)

        rule.onNodeWithContentDescription("Add another photo").performClick()
        rule.waitForIdle()

        // Chooser OPEN: its Cancel pill exists; the pinned close button stays the only cd-"Cancel".
        rule.onNodeWithText("Cancel").assertExists()
        rule.onAllNodesWithContentDescription("Cancel").assertCountEquals(1)

        // Dismiss via the chooser's own Cancel — the pill must leave composition with the dialog.
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test fun tapping_a_tile_selects_it() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera, PlateSource.Camera, PlateSource.Camera))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        // Default selection is the first photo.
        rule.onNodeWithContentDescription("Photo 1 of 3").assertIsSelected()

        rule.onNodeWithContentDescription("Photo 2 of 3").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Photo 2 of 3").assertIsSelected()
        rule.onNodeWithContentDescription("Photo 1 of 3").assertIsNotSelected()
        // The action row's visible "Photo N of M" label (a real FrText, distinct from any tile's
        // contentDescription) confirms it re-targets the newly-selected photo too.
        rule.onNodeWithText("Photo 2 of 3").assertExists()
    }

    @Test fun remove_deletes_the_selected_photo_and_the_strip_shrinks() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera, PlateSource.Camera, PlateSource.Camera))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()
        rule.onNodeWithText("3 / ${MealPublishPolicy.MAX_PHOTOS_PER_MEAL}").assertExists()

        rule.onNodeWithContentDescription("Remove this photo").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("2 / ${MealPublishPolicy.MAX_PHOTOS_PER_MEAL}").assertExists()
        rule.onNodeWithContentDescription("Photo 3 of 3").assertDoesNotExist()
        rule.onNodeWithContentDescription("Photo 1 of 2").assertExists()
        rule.onNodeWithContentDescription("Photo 2 of 2").assertExists()
    }

    @Test fun move_right_reorders_the_draft_and_keeps_the_moved_photo_selected() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera, PlateSource.Gallery))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Move photo right").performClick()
        rule.waitForIdle()

        // Verified via state (explicitly allowed alongside semantics order): the Camera photo that
        // started at index 0 is now at index 1, and the Gallery photo slid into index 0.
        assertEquals(listOf(PlateSource.Gallery, PlateSource.Camera), vm.state.value.photos.map { it.source })
        // Selection follows the moved photo to its new slot.
        assertEquals(1, vm.state.value.selectedIndex)
    }

    @Test fun strip_tile_gallery_marker_shows_only_on_the_gallery_sourced_photo() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera, PlateSource.Gallery))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        // Exactly one marker exists — onNodeWithContentDescription fails if there were zero or more
        // than one match, so this alone proves the Camera-sourced tile carries none.
        rule.onNodeWithContentDescription("Photo picked from the gallery").assertExists()
    }

    @Test fun strip_tile_gallery_marker_absent_when_every_photo_is_camera_sourced() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera, PlateSource.Camera))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Photo picked from the gallery").assertDoesNotExist()
    }

    // ── edge-case hardening (2026-07-13 track-edge-presentation) ─────────────────────────

    @Test fun add_tile_exists_at_nine_photos_with_counter_nine_of_ten() {
        val nine = Array(9) { PlateSource.Camera }
        val repo = FakeMealRepository().apply { runBlockingSaveDraft(draftWithPhotos(*nine)) }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        // One under the cap: the counter reads "9 / 10" and the add tile is still offered — the
        // precise boundary immediately below the already-covered "10 / 10, tile gone" case.
        rule.onNodeWithText("9 / ${MealPublishPolicy.MAX_PHOTOS_PER_MEAL}").assertExists()
        // At 9 tiles the strip's content (9 * 76dp tiles + the add tile, all inside the 480dp
        // form-max width) overflows the viewport, so the LazyRow only composes what's currently
        // visible — the add tile (index 9, the very last item) needs a scroll before it exists in
        // the semantics tree. Target the LazyRow via its own horizontal-scroll semantics rather
        // than a testTag (none is set in production, and this track may not add one).
        val horizontalScrollableRow = SemanticsMatcher("hasHorizontalScrollAxisRange") { node ->
            node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
        }
        rule.onNode(horizontalScrollableRow).performScrollToIndex(9)
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Add another photo").assertExists()
    }

    @Test fun move_left_button_is_disabled_when_the_first_photo_is_selected() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera, PlateSource.Camera, PlateSource.Camera))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()

        // Default selection is the first photo (index 0) — move-left has nowhere to go, so it must
        // be disabled (not hidden — the row's width stays stable per PhotoActionRow's kdoc).
        rule.onNodeWithContentDescription("Move photo left").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Move photo right").assertIsEnabled()
    }

    @Test fun move_right_button_is_disabled_when_the_last_photo_is_selected() {
        val repo = FakeMealRepository().apply {
            runBlockingSaveDraft(draftWithPhotos(PlateSource.Camera, PlateSource.Camera, PlateSource.Camera))
        }
        val vm = viewModel(repo)

        rule.setContent { FoodRatsTheme { ComposePlateScreen(onPublishStarted = {}, onEditIngredients = {}, onClose = {}, vm = vm) } }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Photo 3 of 3").performClick() // select the last photo
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Move photo right").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Move photo left").assertIsEnabled()
    }

    /** [FakeMealRepository.saveDraft] is a suspend fun; the `apply {}` fixture builders above need
     *  a blocking call site (test setup, not production code) to seed the draft before `setContent`. */
    private fun FakeMealRepository.runBlockingSaveDraft(draft: MealDraft) {
        kotlinx.coroutines.runBlocking { saveDraft(draft) }
    }
}
