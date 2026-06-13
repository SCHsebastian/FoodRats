package es.schsebastian.foodrats.feature.crew.presentation.settings

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.model.AccountId
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
import es.schsebastian.foodrats.feature.crew.domain.usecase.DeleteCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.LeaveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveMemberUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameCrewUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CrewSettingsViewModelTest {

    private val ownerId = aid("uid-owner")
    private val memberId = aid("uid-other")
    private val crewId = cid("c-1")
    private val sampleCrew = Crew.of(
        id = crewId,
        name = "My Crew",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = ownerId,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(
            Member(ownerId, Instant.fromEpochMilliseconds(0L)),
            Member(memberId, Instant.fromEpochMilliseconds(0L)),
        ),
    )

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun owner_confirms_remove_member_surfaces_not_implemented_error() = runTest {
        val vm = buildVm(ownerId)
        assertEquals(true, vm.state.value.isOwner)
        assertEquals(ownerId, vm.state.value.myAccountId)

        vm.onIntent(CrewSettingsIntent.RemoveMemberConfirmed(memberId))

        assertEquals(CrewError.NotImplemented.RemoveMember, vm.state.value.error)
    }

    @Test
    fun non_owner_state_excludes_my_id_from_being_owner() = runTest {
        val vm = buildVm(memberId)
        assertEquals(false, vm.state.value.isOwner)
        assertEquals(memberId, vm.state.value.myAccountId)
    }

    @Test
    fun save_crew_name_success_keeps_state_clean() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val vm = buildVm(ownerId, repo)

        vm.onIntent(CrewSettingsIntent.CrewNameChanged("Renamed Crew"))
        vm.onIntent(CrewSettingsIntent.SaveCrewName)

        assertEquals(false, vm.state.value.isSavingCrewName)
        assertNull(vm.state.value.error)
        assertEquals(Pair(crewId, "Renamed Crew"), repo.lastRename)
    }

    @Test
    fun save_crew_name_fails_with_authorization_not_owner_when_non_owner() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val vm = buildVm(memberId, repo)

        vm.onIntent(CrewSettingsIntent.CrewNameChanged("Hostile Rename"))
        vm.onIntent(CrewSettingsIntent.SaveCrewName)

        assertEquals(CrewError.Authorization.NotOwner, vm.state.value.error)
        assertEquals(false, vm.state.value.isSavingCrewName)
    }

    @Test
    fun save_crew_name_blank_is_rejected_via_validation() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val vm = buildVm(ownerId, repo)

        vm.onIntent(CrewSettingsIntent.CrewNameChanged("   "))
        vm.onIntent(CrewSettingsIntent.SaveCrewName)

        assertEquals(CrewError.Validation.NameBlank, vm.state.value.error)
        assertEquals(false, vm.state.value.isSavingCrewName)
    }

    @Test
    fun leave_crew_emits_left_effect_on_success() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew)).apply {
            nextLeave = Result.success(Unit)
        }
        val vm = buildVm(memberId, repo)

        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.Leave)
            assertEquals(CrewSettingsEffect.Left, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(false, vm.state.value.isLeaving)
        // Note: after a successful leave the crew is removed from the fake repo, so
        // observeCrew re-emits NotFound — that error surfacing back onto state is the
        // expected behavior (the screen has navigated away by then).
    }

    @Test
    fun leave_crew_error_surfaces_on_state() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew)).apply {
            nextLeave = Result.failure(CrewError.Backend.Network)
        }
        val vm = buildVm(memberId, repo)

        vm.onIntent(CrewSettingsIntent.Leave)

        assertEquals(CrewError.Backend.Network, vm.state.value.error)
        assertEquals(false, vm.state.value.isLeaving)
    }

    @Test
    fun confirm_delete_emits_deleted_effect_on_success_for_owner() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val vm = buildVm(ownerId, repo)

        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.ConfirmDelete)
            assertEquals(CrewSettingsEffect.Deleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(crewId, repo.lastDelete)
        assertEquals(false, vm.state.value.isDeleting)
        assertEquals(false, vm.state.value.showDeleteConfirm)
    }

    @Test
    fun request_delete_sets_dialog_flag() = runTest {
        val vm = buildVm(ownerId)

        vm.onIntent(CrewSettingsIntent.RequestDelete)

        assertTrue(vm.state.value.showDeleteConfirm)
    }

    @Test
    fun cancel_delete_clears_dialog_flag() = runTest {
        val vm = buildVm(ownerId)
        vm.onIntent(CrewSettingsIntent.RequestDelete)
        assertTrue(vm.state.value.showDeleteConfirm)

        vm.onIntent(CrewSettingsIntent.CancelDelete)

        assertEquals(false, vm.state.value.showDeleteConfirm)
    }

    @Test
    fun switch_crew_emits_navigate_effect() = runTest {
        val vm = buildVm(ownerId)

        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.SwitchCrew)
            assertEquals(CrewSettingsEffect.NavigateToCrewPicker, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun buildVm(
        actingAs: AccountId,
        repo: FakeCrewRepository = FakeCrewRepository(listOf(sampleCrew)),
    ): CrewSettingsViewModel {
        val session = FixedSessionProvider(Session(accountId = actingAs, activeCrewId = crewId))
        return CrewSettingsViewModel(
            crewId = crewId,
            observeCrew = ObserveCrewUseCase(repo),
            renameCrew = RenameCrewUseCase(repo, session),
            deleteCrew = DeleteCrewUseCase(repo, session),
            leaveCrew = LeaveCrewUseCase(repo),
            removeMember = RemoveMemberUseCase(),
            session = session,
            accountRead = EmptyAccountReadPort,
        )
    }

    private class FixedSessionProvider(private val session: Session?) : SessionProvider {
        override val current: Flow<Session?> = flowOf(session)
        override suspend fun requireCurrent(): Result<Session, SessionError> =
            session?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
    }

    private object EmptyAccountReadPort : AccountReadPort {
        override fun observe(id: AccountId): Flow<Account?> = MutableStateFlow(null)
    }
}
