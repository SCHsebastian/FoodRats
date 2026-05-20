package es.schsebastian.foodrats.feature.crew.presentation.settings

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.session.SignOutPort
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
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameMemberUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSettingsSessionProvider(
    session: Session,
) : SessionProvider {
    private val _session = MutableStateFlow<Session?>(session)
    override val current: Flow<Session?> = _session
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        _session.value?.let { Result.success(it) }
            ?: Result.failure(SessionError.NotSignedIn)
}

private class FakeSignOutPort(
    private val result: Result<Unit, SessionError> = Result.success(Unit),
) : SignOutPort {
    var calls = 0
    override suspend fun signOut(): Result<Unit, SessionError> {
        calls++
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CrewSettingsViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val ownerId = aid("uid-owner")
    private val memberId = aid("uid-member")
    private val crewId = cid("c-test")
    private val ownerMember = Member(ownerId, "Owner Name", null, Instant.fromEpochMilliseconds(0L))
    private val member = Member(memberId, "Member Name", null, Instant.fromEpochMilliseconds(0L))
    private val testCrew = Crew(
        id = crewId,
        name = "Test Crew",
        code = (CrewCode.of("TESTC2") as Result.Ok).value,
        ownerId = ownerId,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(ownerMember, member),
    )

    private fun viewModel(
        repo: FakeCrewRepository = FakeCrewRepository(initial = listOf(testCrew)),
        session: Session = Session(ownerId, crewId),
    ): CrewSettingsViewModel {
        val provider = FakeSettingsSessionProvider(session)
        return CrewSettingsViewModel(
            crewId = crewId,
            observeCrew = ObserveCrewUseCase(repo),
            renameCrew = RenameCrewUseCase(repo, provider),
            renameMember = RenameMemberUseCase(repo, provider),
            deleteCrew = DeleteCrewUseCase(repo, provider),
            leaveCrew = LeaveCrewUseCase(repo),
            session = provider,
            signOutPort = FakeSignOutPort(),
        )
    }

    @Test
    fun seeds_editing_fields_and_isOwner_from_observed_crew() = runTest {
        val vm = viewModel()
        vm.state.test {
            var state = awaitItem()
            if (state.crew == null) state = awaitItem()
            assertEquals(testCrew, state.crew)
            assertTrue(state.isOwner)
            assertEquals("Test Crew", state.editingCrewName)
            assertEquals("Owner Name", state.editingMyDisplayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun seeds_editing_fields_and_isOwner_from_observed_crew_non_owner() = runTest {
        val vm = viewModel(session = Session(memberId, crewId))
        vm.state.test {
            var state = awaitItem()
            if (state.crew == null) state = awaitItem()
            assertFalse(state.isOwner)
            assertEquals("Member Name", state.editingMyDisplayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun crew_name_changed_updates_state() = runTest {
        val vm = viewModel()
        vm.onIntent(CrewSettingsIntent.CrewNameChanged("New Name"))
        assertEquals("New Name", vm.state.value.editingCrewName)
    }

    @Test
    fun save_crew_name_success_clears_saving() = runTest {
        val vm = viewModel()
        // Seed editing name
        vm.onIntent(CrewSettingsIntent.CrewNameChanged("Renamed Crew"))
        vm.onIntent(CrewSettingsIntent.SaveCrewName)
        assertFalse(vm.state.value.isSavingCrewName)
        assertNull(vm.state.value.error)
    }

    @Test
    fun save_crew_name_error_sets_error_and_clears_saving() = runTest {
        val repo = FakeCrewRepository(initial = listOf(testCrew.copy(ownerId = memberId))) // owner is different so renameCrew will fail
        val provider = FakeSettingsSessionProvider(Session(ownerId, crewId))
        val vm = CrewSettingsViewModel(
            crewId = crewId,
            observeCrew = ObserveCrewUseCase(repo),
            renameCrew = RenameCrewUseCase(repo, provider),
            renameMember = RenameMemberUseCase(repo, provider),
            deleteCrew = DeleteCrewUseCase(repo, provider),
            leaveCrew = LeaveCrewUseCase(repo),
            session = provider,
            signOutPort = FakeSignOutPort(),
        )
        vm.onIntent(CrewSettingsIntent.CrewNameChanged("Some Name"))
        vm.onIntent(CrewSettingsIntent.SaveCrewName)
        assertFalse(vm.state.value.isSavingCrewName)
        assertEquals(CrewError.Authorization.NotOwner, vm.state.value.error)
    }

    @Test
    fun my_display_name_changed_updates_state() = runTest {
        val vm = viewModel()
        vm.onIntent(CrewSettingsIntent.MyDisplayNameChanged("New Display"))
        assertEquals("New Display", vm.state.value.editingMyDisplayName)
    }

    @Test
    fun save_my_display_name_success_clears_saving() = runTest {
        val vm = viewModel()
        vm.onIntent(CrewSettingsIntent.MyDisplayNameChanged("New Display Name"))
        vm.onIntent(CrewSettingsIntent.SaveMyDisplayName)
        assertFalse(vm.state.value.isSavingMyDisplayName)
        assertNull(vm.state.value.error)
    }

    @Test
    fun save_my_display_name_error_sets_error() = runTest {
        val vm = viewModel()
        // Blank display name triggers validation error
        vm.onIntent(CrewSettingsIntent.MyDisplayNameChanged("   "))
        vm.onIntent(CrewSettingsIntent.SaveMyDisplayName)
        assertFalse(vm.state.value.isSavingMyDisplayName)
        assertEquals(CrewError.Validation.DisplayNameBlank, vm.state.value.error)
    }

    @Test
    fun switch_crew_emits_navigate_to_crew_picker_effect() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.SwitchCrew)
            assertEquals(CrewSettingsEffect.NavigateToCrewPicker, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun request_delete_shows_confirm_dialog_without_effect() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.RequestDelete)
            assertTrue(vm.state.value.showDeleteConfirm)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancel_delete_hides_dialog_without_effect() = runTest {
        val vm = viewModel()
        vm.onIntent(CrewSettingsIntent.RequestDelete)
        assertTrue(vm.state.value.showDeleteConfirm)
        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.CancelDelete)
            assertFalse(vm.state.value.showDeleteConfirm)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirm_delete_success_emits_deleted_effect() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.ConfirmDelete)
            assertEquals(CrewSettingsEffect.Deleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.state.value.isDeleting)
    }

    @Test
    fun confirm_delete_error_keeps_dialog_closed_sets_error() = runTest {
        val repo = FakeCrewRepository(initial = listOf(testCrew.copy(ownerId = memberId))) // owner mismatch
        val provider = FakeSettingsSessionProvider(Session(ownerId, crewId))
        val vm = CrewSettingsViewModel(
            crewId = crewId,
            observeCrew = ObserveCrewUseCase(repo),
            renameCrew = RenameCrewUseCase(repo, provider),
            renameMember = RenameMemberUseCase(repo, provider),
            deleteCrew = DeleteCrewUseCase(repo, provider),
            leaveCrew = LeaveCrewUseCase(repo),
            session = provider,
            signOutPort = FakeSignOutPort(),
        )
        vm.onIntent(CrewSettingsIntent.ConfirmDelete)
        assertFalse(vm.state.value.showDeleteConfirm)
        assertFalse(vm.state.value.isDeleting)
        assertEquals(CrewError.Authorization.NotOwner, vm.state.value.error)
    }

    @Test
    fun dismiss_error_clears_error() = runTest {
        val vm = viewModel()
        // Trigger an error first
        vm.onIntent(CrewSettingsIntent.MyDisplayNameChanged("  "))
        vm.onIntent(CrewSettingsIntent.SaveMyDisplayName)
        assertTrue(vm.state.value.error != null)
        vm.onIntent(CrewSettingsIntent.DismissError)
        assertNull(vm.state.value.error)
    }

    @Test
    fun sign_out_success_emits_signedOut_effect_and_calls_port() = runTest {
        val repo = FakeCrewRepository(initial = listOf(testCrew))
        val provider = FakeSettingsSessionProvider(Session(ownerId, crewId))
        val signOutPort = FakeSignOutPort(result = Result.success(Unit))
        val vm = CrewSettingsViewModel(
            crewId = crewId,
            observeCrew = ObserveCrewUseCase(repo),
            renameCrew = RenameCrewUseCase(repo, provider),
            renameMember = RenameMemberUseCase(repo, provider),
            deleteCrew = DeleteCrewUseCase(repo, provider),
            leaveCrew = LeaveCrewUseCase(repo),
            session = provider,
            signOutPort = signOutPort,
        )
        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.SignOut)
            assertEquals(CrewSettingsEffect.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, signOutPort.calls)
        assertFalse(vm.state.value.isSigningOut)
        assertNull(vm.state.value.signOutError)
    }

    @Test
    fun sign_out_failure_sets_signOutError_and_no_effect() = runTest {
        val repo = FakeCrewRepository(initial = listOf(testCrew))
        val provider = FakeSettingsSessionProvider(Session(ownerId, crewId))
        val signOutPort = FakeSignOutPort(result = Result.failure(SessionError.FirebaseUnavailable))
        val vm = CrewSettingsViewModel(
            crewId = crewId,
            observeCrew = ObserveCrewUseCase(repo),
            renameCrew = RenameCrewUseCase(repo, provider),
            renameMember = RenameMemberUseCase(repo, provider),
            deleteCrew = DeleteCrewUseCase(repo, provider),
            leaveCrew = LeaveCrewUseCase(repo),
            session = provider,
            signOutPort = signOutPort,
        )
        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.SignOut)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SessionError.FirebaseUnavailable, vm.state.value.signOutError)
        assertFalse(vm.state.value.isSigningOut)
    }
}
