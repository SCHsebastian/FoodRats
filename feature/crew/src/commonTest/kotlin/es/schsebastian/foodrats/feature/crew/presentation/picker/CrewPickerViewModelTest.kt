package es.schsebastian.foodrats.feature.crew.presentation.picker

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import es.schsebastian.foodrats.feature.crew.domain.usecase.CreateCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveMyCrewsUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RequestToJoinCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SwitchActiveCrewUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeSessionProvider(val session: Session) : SessionProvider {
    override val current: Flow<Session?> = MutableStateFlow(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> = Result.success(session)
}

class FakeActiveCrew : ActiveCrewProvider {
    val active = MutableStateFlow<CrewId?>(null)
    override val current = active
    override suspend fun set(crewId: CrewId) { active.value = crewId }
    override suspend fun clear() { active.value = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CrewPickerViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val me = aid("uid-me")
    private val crewA = Crew.of(
        id = cid("c-A"),
        name = "Crew A",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = me,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(Member(me, Instant.fromEpochMilliseconds(0L))),
    )

    /**
     * Wraps [FakeCrewRepository] and parks `create` / `requestToJoinByCode` on a gate so tests can
     * hold a write "in flight" while firing a second Submit — proves the double-tap re-entry guard.
     */
    private class GatedCrewRepository(
        private val delegate: FakeCrewRepository,
    ) : CrewRepository by delegate {
        val createGate = CompletableDeferred<Unit>()
        var createCalls = 0
        val joinGate = CompletableDeferred<Unit>()
        var joinCalls = 0

        override suspend fun create(name: String, founder: AccountId): Result<Crew, CrewError> {
            createCalls++
            createGate.await()
            return delegate.create(name, founder)
        }

        override suspend fun requestToJoinByCode(code: CrewCode, requester: AccountId): Result<Unit, CrewError> {
            joinCalls++
            joinGate.await()
            return delegate.requestToJoinByCode(code, requester)
        }
    }

    private fun viewModel(
        repo: CrewRepository,
        active: FakeActiveCrew = FakeActiveCrew(),
        analytics: RecordingAnalyticsTracker = RecordingAnalyticsTracker(),
    ) =
        CrewPickerViewModel(
            session = FakeSessionProvider(Session(me, null)),
            observeMyCrews = ObserveMyCrewsUseCase(repo),
            createCrew = CreateCrewUseCase(repo),
            requestToJoin = RequestToJoinCrewUseCase(repo),
            switchActive = SwitchActiveCrewUseCase(active),
            analytics = analytics,
        )

    @Test fun load_emits_crews() = runTest {
        val repo = FakeCrewRepository(initial = listOf(crewA))
        val vm = viewModel(repo)
        vm.state.test {
            // With UnconfinedTestDispatcher the init coroutine may run before test{} collects,
            // so we skip any empty-state emission and assert the final populated state.
            var state = awaitItem()
            if (state.crews.isEmpty()) state = awaitItem()
            assertEquals(listOf(crewA), state.crews)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun pick_sets_active_emits_effect_and_tracks_crew_switched() = runTest {
        val repo = FakeCrewRepository(initial = listOf(crewA))
        val active = FakeActiveCrew()
        val analytics = RecordingAnalyticsTracker()
        val vm = viewModel(repo, active, analytics)
        vm.effects.test {
            vm.onIntent(CrewPickerIntent.PickCrew(crewA.id))
            assertEquals(CrewPickerEffect.CrewSelected(crewA.id), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(crewA.id, active.active.value)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.CrewSwitched(crewA.id)), analytics.events.toList())
    }

    @Test fun submitCreate_with_blank_keeps_error_on_state() = runTest {
        val repo = FakeCrewRepository().apply { nextCreate = Result.failure(CrewError.Validation.NameBlank) }
        val vm = viewModel(repo)
        vm.onIntent(CrewPickerIntent.ToggleCreateForm)
        vm.onIntent(CrewPickerIntent.CreateInputChanged("  "))
        vm.onIntent(CrewPickerIntent.SubmitCreate)
        assertEquals(CrewError.Validation.NameBlank, vm.state.value.error)
    }

    @Test fun submitJoin_with_bad_code_keeps_error_on_state() = runTest {
        val repo = FakeCrewRepository()
        val vm = viewModel(repo)
        vm.onIntent(CrewPickerIntent.ToggleJoinForm)
        vm.onIntent(CrewPickerIntent.JoinInputChanged("xx"))
        vm.onIntent(CrewPickerIntent.SubmitJoin)
        // CrewCode.of("xx") returns CodeMalformed; use case returns that without touching repo.
        assertEquals(CrewError.Validation.CodeMalformed, vm.state.value.error)
    }

    @Test fun submitCreate_reentry_while_create_in_flight_is_ignored() = runTest {
        val repo = GatedCrewRepository(FakeCrewRepository().apply { nextCreate = Result.success(crewA) })
        val vm = viewModel(repo)
        vm.onIntent(CrewPickerIntent.ToggleCreateForm)
        vm.onIntent(CrewPickerIntent.CreateInputChanged("Crew A"))
        vm.onIntent(CrewPickerIntent.SubmitCreate)
        assertTrue(vm.state.value.isCreating)
        // Rapid double-tap while the first write is parked inside the repository — must be a no-op.
        vm.onIntent(CrewPickerIntent.SubmitCreate)
        assertEquals(1, repo.createCalls)
        repo.createGate.complete(Unit)
        vm.state.test {
            val state = expectMostRecentItem()
            assertEquals(false, state.isCreating)
            assertEquals(null, state.error)
        }
        assertEquals(1, repo.createCalls)
    }

    @Test fun submitJoin_reentry_while_join_in_flight_is_ignored() = runTest {
        val repo = GatedCrewRepository(FakeCrewRepository())
        val vm = viewModel(repo)
        vm.onIntent(CrewPickerIntent.ToggleJoinForm)
        vm.onIntent(CrewPickerIntent.JoinInputChanged("ABCD23"))
        vm.onIntent(CrewPickerIntent.SubmitJoin)
        assertTrue(vm.state.value.isJoining)
        // Double-tap while the first request is parked — must not file a duplicate join request.
        vm.onIntent(CrewPickerIntent.SubmitJoin)
        assertEquals(1, repo.joinCalls)
        repo.joinGate.complete(Unit)
        vm.state.test {
            val state = expectMostRecentItem()
            assertEquals(false, state.isJoining)
            assertEquals(null, state.error)
        }
        assertEquals(1, repo.joinCalls)
    }

    @Test fun dismissError_clears_error() = runTest {
        val repo = FakeCrewRepository()
        val vm = viewModel(repo)
        vm.onIntent(CrewPickerIntent.ToggleJoinForm)
        vm.onIntent(CrewPickerIntent.JoinInputChanged("xx"))
        vm.onIntent(CrewPickerIntent.SubmitJoin)
        assertTrue(vm.state.value.error != null)
        vm.onIntent(CrewPickerIntent.DismissError)
        assertEquals(null, vm.state.value.error)
    }
}
