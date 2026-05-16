package es.schsebastian.foodrats.feature.crew.presentation.picker

import app.cash.turbine.test
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
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import es.schsebastian.foodrats.feature.crew.domain.usecase.CreateCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.JoinCrewByCodeUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveMyCrewsUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SwitchActiveCrewUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
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
    private val crewA = Crew(
        id = cid("c-A"),
        name = "Crew A",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = me,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(Member(me, "Me", null, Instant.fromEpochMilliseconds(0L))),
    )

    private fun viewModel(repo: FakeCrewRepository, active: FakeActiveCrew = FakeActiveCrew()) =
        CrewPickerViewModel(
            session = FakeSessionProvider(Session(me, null)),
            observeMyCrews = ObserveMyCrewsUseCase(repo),
            createCrew = CreateCrewUseCase(repo),
            joinCrew = JoinCrewByCodeUseCase(repo),
            switchActive = SwitchActiveCrewUseCase(active),
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

    @Test fun pick_sets_active_and_emits_effect() = runTest {
        val repo = FakeCrewRepository(initial = listOf(crewA))
        val active = FakeActiveCrew()
        val vm = viewModel(repo, active)
        vm.effects.test {
            vm.onIntent(CrewPickerIntent.PickCrew(crewA.id))
            assertEquals(CrewPickerEffect.CrewSelected(crewA.id), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(crewA.id, active.active.value)
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
