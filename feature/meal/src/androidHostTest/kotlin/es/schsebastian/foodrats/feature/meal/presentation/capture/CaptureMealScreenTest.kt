package es.schsebastian.foodrats.feature.meal.presentation.capture

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudienceError
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudiencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.StartMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [CaptureMealScreen] is a transparent camera auto-launcher with almost no persistent UI: the
 * ONLY reachable render surface (besides the processing overlay and the error banner) is the
 * camera-dismiss fallback chooser. `rememberPhotoPicker`'s Android actual builds the capture
 * FileProvider URI against the authority `"${packageName}.photopicker"` — that `<provider>` is
 * declared only in `:androidApp`'s manifest (see `androidApp/src/main/AndroidManifest.xml`), NOT
 * in `:feature:meal`'s own manifest, so under this module's Robolectric harness
 * `FileProvider.getUriForFile` genuinely throws, `launchCamera()`'s catch block converts that into
 * `PhotoPickResult.Failed`, and the screen's own (pre-existing) `Failed -> awaitingChoice = true`
 * branch lands on the real chooser — no fake picker/registry needed to reach this state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CaptureMealScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val clock = object : Clock { override fun now() = Instant.parse("2026-05-16T12:00:00Z") }
    private val account = (AccountId.of("acc-1") as Result.Ok).value
    private val crew = (CrewId.of("crew-1") as Result.Ok).value

    private class FakeSessionProvider(private val session: Session?) : SessionProvider {
        override val current: Flow<Session?> = MutableStateFlow(session)
        override suspend fun requireCurrent(): Result<Session, SessionError> =
            session?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
    }

    private class FakeCrewMembership(private val crews: List<CrewId>) : CrewMembershipPort {
        override fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>> =
            MutableStateFlow(crews.map { CrewSummary(it, "Crew ${it.value}") })
    }

    private class FakeDefaultAudiencePort : DefaultAudiencePort {
        override val defaultAudience: Flow<Set<CrewId>?> = MutableStateFlow(null)
        override suspend fun set(crewIds: Set<CrewId>): Result<Unit, DefaultAudienceError> = Result.success(Unit)
    }

    private fun viewModel() = CaptureMealViewModel(
        startDraft = StartMealDraftUseCase(FakeMealRepository(), clock, zone),
        updateDraft = UpdateMealDraftUseCase(FakeMealRepository()),
        sessionProvider = FakeSessionProvider(Session(account, crew)),
        crewMembership = FakeCrewMembership(listOf(crew)),
        defaultAudience = FakeDefaultAudiencePort(),
        analytics = RecordingAnalyticsTracker(),
    )

    @Test fun camera_failure_fallback_chooser_renders_failed_title_with_retry_gallery_and_cancel_actions() {
        val vm = viewModel()

        rule.setContent {
            FoodRatsTheme {
                CaptureMealScreen(onCaptured = {}, onCancelled = {}, onOpenSettings = {}, vm = vm)
            }
        }
        rule.waitForIdle()

        // This chooser is reached via PhotoPickResult.Failed (the FileProvider authority is
        // missing in this harness — see the class KDoc), so the title must be the FAILED
        // variant, not the generic "Add your plate photo" a plain camera dismiss shows.
        rule.onNodeWithText("Couldn't get that photo. Try again or pick one from your gallery.").assertExists()
        rule.onNodeWithText("Add your plate photo").assertDoesNotExist()
        rule.onNodeWithText("Take a photo").assertExists()
        rule.onNodeWithText("Choose from gallery").assertExists()
        rule.onNodeWithText("Cancel").assertExists()
    }

    @Test fun cancel_action_on_the_fallback_chooser_invokes_onCancelled() {
        val vm = viewModel()
        var cancelled = false

        rule.setContent {
            FoodRatsTheme {
                CaptureMealScreen(onCaptured = {}, onCancelled = { cancelled = true }, onOpenSettings = {}, vm = vm)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test fun gallery_action_dismisses_the_chooser_and_invokes_the_picker_without_crashing() {
        val vm = viewModel()

        rule.setContent {
            FoodRatsTheme {
                CaptureMealScreen(onCaptured = {}, onCancelled = {}, onOpenSettings = {}, vm = vm)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Choose from gallery").assertExists()

        // Tapping the gallery affordance calls picker.launchGallery() (the real Android actual,
        // via the host Activity's ActivityResultRegistry) and immediately flips awaitingChoice
        // back to false — the fallback chooser must disappear, proving the tap was wired through
        // to the picker rather than a dead button.
        rule.onNodeWithText("Choose from gallery").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Couldn't get that photo. Try again or pick one from your gallery.")
            .assertDoesNotExist()
    }
}
