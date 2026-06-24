package es.schsebastian.foodrats.feature.crew.presentation.invite

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import es.schsebastian.foodrats.feature.crew.domain.usecase.RequestToJoinCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ResolveCrewByCodeUseCase
import es.schsebastian.foodrats.feature.crew.presentation.picker.FakeSessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptInviteViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val me = aid("uid-me")
    private val code = (CrewCode.of("ABCD23") as Result.Ok).value
    private val crew = Crew.of(
        id = cid("c-A"),
        name = "Crew A",
        code = code,
        ownerId = aid("uid-owner"),
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(Member(aid("uid-owner"), Instant.fromEpochMilliseconds(0L))),
    )

    private fun viewModel(
        repo: FakeCrewRepository,
        rawCode: String = code.value,
    ) = AcceptInviteViewModel(
        code = rawCode,
        session = FakeSessionProvider(Session(me, null)),
        resolveCrew = ResolveCrewByCodeUseCase(repo),
        requestToJoin = RequestToJoinCrewUseCase(repo),
    )

    @Test fun resolves_preview_crew_on_init() = runTest {
        val repo = FakeCrewRepository(initial = listOf(crew))
        val vm = viewModel(repo)
        vm.state.test {
            var s = awaitItem()
            while (s.crew == null && s.error == null) s = awaitItem()
            assertEquals(crew, s.crew)
            assertEquals(null, s.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun unknown_code_surfaces_code_unknown_error() = runTest {
        val repo = FakeCrewRepository(initial = emptyList()) // no crew with that code
        val vm = viewModel(repo)
        vm.state.test {
            var s = awaitItem()
            while (s.error == null && s.isResolving) s = awaitItem()
            assertEquals(CrewError.Invite.CodeUnknown, s.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun malformed_code_surfaces_validation_error_without_backend() = runTest {
        val repo = FakeCrewRepository().apply {
            nextFindByCode = Result.failure(CrewError.Backend.Unavailable) // must NOT be reached
        }
        val vm = viewModel(repo, rawCode = "xx")
        vm.state.test {
            var s = awaitItem()
            while (s.error == null && s.isResolving) s = awaitItem()
            assertEquals(CrewError.Validation.CodeMalformed, s.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun request_succeeds_marks_request_sent_no_join() = runTest {
        val repo = FakeCrewRepository(initial = listOf(crew)).apply {
            nextRequestToJoin = Result.success(Unit)
        }
        val vm = viewModel(repo)
        vm.onIntent(AcceptInviteIntent.Join)
        assertTrue(vm.state.value.requestSent)
        assertEquals(null, vm.state.value.error)
        // The requester is NOT added as a member — approval is required.
        assertTrue(crew.members.none { it.accountId == me })
    }

    @Test fun already_member_keeps_error_on_state() = runTest {
        val repo = FakeCrewRepository(initial = listOf(crew)).apply {
            nextRequestToJoin = Result.failure(CrewError.Membership.AlreadyMember)
        }
        val vm = viewModel(repo)
        vm.onIntent(AcceptInviteIntent.Join)
        assertEquals(CrewError.Membership.AlreadyMember, vm.state.value.error)
        assertTrue(!vm.state.value.requestSent)
    }
}
