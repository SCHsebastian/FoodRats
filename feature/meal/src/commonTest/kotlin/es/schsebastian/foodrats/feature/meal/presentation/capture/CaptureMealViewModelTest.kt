package es.schsebastian.foodrats.feature.meal.presentation.capture

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.CaptureSource
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
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.StartMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureMealViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val clock = object : Clock { override fun now() = Instant.parse("2026-05-16T12:00:00Z") }
    private val account = (AccountId.of("acc-1") as Result.Ok).value
    private val crew = (CrewId.of("crew-1") as Result.Ok).value

    private class FakeSessionProvider(
        private val session: Session?,
        private val error: SessionError = SessionError.NotSignedIn,
    ) : SessionProvider {
        override val current: Flow<Session?> = MutableStateFlow(session)
        override suspend fun requireCurrent(): Result<Session, SessionError> =
            session?.let { Result.success(it) } ?: Result.failure(error)
    }

    private class FakeCrewMembership(private val crews: List<CrewId>) : CrewMembershipPort {
        override fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>> =
            MutableStateFlow(crews.map { CrewSummary(it, "Crew ${it.value}") })
    }

    private class FakeDefaultAudiencePort(
        private val saved: Set<CrewId>? = null,
    ) : DefaultAudiencePort {
        override val defaultAudience: Flow<Set<CrewId>?> = MutableStateFlow(saved)
        override suspend fun set(crewIds: Set<CrewId>): Result<Unit, DefaultAudienceError> =
            Result.success(Unit)
    }

    private val analytics = RecordingAnalyticsTracker()
    private val crew2 = (CrewId.of("crew-2") as Result.Ok).value

    private fun viewModel(
        repo: FakeMealRepository = FakeMealRepository(),
        session: Session? = Session(account, crew),
        crews: List<CrewId> = listOf(crew),
        savedDefaultAudience: Set<CrewId>? = null,
    ) = CaptureMealViewModel(
        startDraft = StartMealDraftUseCase(repo, clock, zone),
        updateDraft = UpdateMealDraftUseCase(repo),
        sessionProvider = FakeSessionProvider(session),
        crewMembership = FakeCrewMembership(crews),
        defaultAudience = FakeDefaultAudiencePort(savedDefaultAudience),
        analytics = analytics,
    )

    @Test fun session_error_on_start_surfaces_error_banner() = runTest {
        val vm = viewModel(session = null)
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            assertEquals(MealStringKey.CaptureSessionError, expectMostRecentItem().error)
        }
        assertEquals(emptyList(), analytics.events)
    }

    @Test fun no_crews_on_start_surfaces_error_banner() = runTest {
        val vm = viewModel(crews = emptyList())
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            assertEquals(MealStringKey.CaptureNoCrews, expectMostRecentItem().error)
        }
        assertEquals(emptyList(), analytics.events)
    }

    @Test fun successful_start_leaves_no_error() = runTest {
        val vm = viewModel()
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            assertEquals(null, expectMostRecentItem().error)
        }
    }

    @Test fun successful_start_tracks_meal_capture_started_once() = runTest {
        val vm = viewModel()
        vm.onIntent(CaptureMealIntent.Start)
        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.MealCaptureStarted(CaptureSource.UNKNOWN)),
            analytics.events.toList(),
        )
    }

    @Test fun draft_failure_on_start_does_not_track() = runTest {
        val repo = FakeMealRepository().apply {
            saveDraftResultOverride = Result.failure(MealError.Publish.PublishUnavailable)
        }
        val vm = viewModel(repo = repo)
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            assertEquals(MealStringKey.CaptureDraftFailed, expectMostRecentItem().error)
        }
        assertEquals(emptyList(), analytics.events)
    }

    @Test fun no_saved_default_seeds_draft_with_all_crews() = runTest {
        val repo = FakeMealRepository()
        val crews = listOf(crew, crew2)
        val vm = viewModel(repo = repo, crews = crews, savedDefaultAudience = null)
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            expectMostRecentItem()
        }
        repo.observeDraft().test {
            val draft = awaitItem()
            assertEquals(setOf(crew, crew2), draft?.audienceCrewIds)
        }
    }

    @Test fun saved_default_seeds_draft_with_saved_subset() = runTest {
        val repo = FakeMealRepository()
        // Only crew is in the saved default; crew2 is available but not saved.
        val crews = listOf(crew, crew2)
        val vm = viewModel(repo = repo, crews = crews, savedDefaultAudience = setOf(crew))
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            expectMostRecentItem()
        }
        repo.observeDraft().test {
            val draft = awaitItem()
            assertEquals(setOf(crew), draft?.audienceCrewIds)
        }
    }

    @Test fun saved_default_outside_current_crews_falls_back_to_all_crews() = runTest {
        // The saved default references a crew the user has since left.
        val leftCrew = (CrewId.of("left-crew") as Result.Ok).value
        val repo = FakeMealRepository()
        val crews = listOf(crew)
        val vm = viewModel(repo = repo, crews = crews, savedDefaultAudience = setOf(leftCrew))
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            expectMostRecentItem()
        }
        repo.observeDraft().test {
            val draft = awaitItem()
            // Intersection is empty → fall back to all current crews.
            assertEquals(setOf(crew), draft?.audienceCrewIds)
        }
    }
}
