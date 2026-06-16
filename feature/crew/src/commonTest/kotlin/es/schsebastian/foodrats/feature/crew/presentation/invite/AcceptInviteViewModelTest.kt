package es.schsebastian.foodrats.feature.crew.presentation.invite

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.analytics.JoinMethod
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import es.schsebastian.foodrats.feature.crew.domain.usecase.JoinCrewByCodeUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ResolveCrewByCodeUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SwitchActiveCrewUseCase
import es.schsebastian.foodrats.feature.crew.presentation.picker.FakeAccountReadPort
import es.schsebastian.foodrats.feature.crew.presentation.picker.FakeActiveCrew
import es.schsebastian.foodrats.feature.crew.presentation.picker.FakeSessionProvider
import es.schsebastian.foodrats.core.domain.account.Account
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
    private val myAccount = Account(
        id = me, handle = "me", displayName = "Me", email = null, avatarUrl = null,
    )

    private fun viewModel(
        repo: FakeCrewRepository,
        active: FakeActiveCrew = FakeActiveCrew(),
        analytics: RecordingAnalyticsTracker = RecordingAnalyticsTracker(),
        rawCode: String = code.value,
    ) = AcceptInviteViewModel(
        code = rawCode,
        session = FakeSessionProvider(Session(me, null)),
        resolveCrew = ResolveCrewByCodeUseCase(repo),
        joinCrew = JoinCrewByCodeUseCase(repo),
        switchActive = SwitchActiveCrewUseCase(active),
        accountRead = FakeAccountReadPort(myAccount),
        analytics = analytics,
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

    @Test fun join_succeeds_switches_active_emits_effect_and_tracks_invite_link() = runTest {
        val repo = FakeCrewRepository(initial = listOf(crew)).apply {
            nextJoin = Result.success(crew)
        }
        val active = FakeActiveCrew()
        val analytics = RecordingAnalyticsTracker()
        val vm = viewModel(repo, active, analytics)
        vm.effects.test {
            vm.onIntent(AcceptInviteIntent.Join)
            assertEquals(AcceptInviteEffect.Joined(crew.id), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(crew.id, active.active.value)
        assertTrue(
            analytics.events.contains(
                es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent.CrewJoined(
                    crew.id,
                    JoinMethod.INVITE_LINK,
                ),
            ),
            "expected a join_group event tagged invite_link",
        )
    }

    @Test fun join_full_crew_keeps_error_on_state_no_effect() = runTest {
        val repo = FakeCrewRepository(initial = listOf(crew)).apply {
            nextJoin = Result.failure(CrewError.Membership.Full)
        }
        val vm = viewModel(repo)
        vm.onIntent(AcceptInviteIntent.Join)
        assertEquals(CrewError.Membership.Full, vm.state.value.error)
    }

    @Test fun join_already_member_keeps_error_on_state() = runTest {
        val repo = FakeCrewRepository(initial = listOf(crew)).apply {
            nextJoin = Result.failure(CrewError.Membership.AlreadyMember)
        }
        val vm = viewModel(repo)
        vm.onIntent(AcceptInviteIntent.Join)
        assertEquals(CrewError.Membership.AlreadyMember, vm.state.value.error)
    }
}
