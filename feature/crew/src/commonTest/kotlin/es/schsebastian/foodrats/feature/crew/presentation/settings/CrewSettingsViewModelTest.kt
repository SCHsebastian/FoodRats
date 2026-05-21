package es.schsebastian.foodrats.feature.crew.presentation.settings

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CrewSettingsViewModelTest {

    private val ownerId = aid("uid-owner")
    private val memberId = aid("uid-other")
    private val crewId = cid("c-1")
    private val sampleCrew = Crew(
        id = crewId,
        name = "My Crew",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = ownerId,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(
            Member(ownerId, "Owner", null, Instant.fromEpochMilliseconds(0L)),
            Member(memberId, "Other", null, Instant.fromEpochMilliseconds(0L)),
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

    private fun buildVm(actingAs: AccountId): CrewSettingsViewModel {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val session = FixedSessionProvider(Session(accountId = actingAs, activeCrewId = crewId))
        return CrewSettingsViewModel(
            crewId = crewId,
            observeCrew = ObserveCrewUseCase(repo),
            renameCrew = RenameCrewUseCase(repo, session),
            deleteCrew = DeleteCrewUseCase(repo, session),
            leaveCrew = LeaveCrewUseCase(repo),
            removeMember = RemoveMemberUseCase(),
            session = session,
        )
    }

    private class FixedSessionProvider(private val session: Session?) : SessionProvider {
        override val current: Flow<Session?> = flowOf(session)
        override suspend fun requireCurrent(): Result<Session, SessionError> =
            session?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
    }
}
