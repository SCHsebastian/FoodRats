package es.schsebastian.foodrats.feature.crew.data.sync

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDto
import es.schsebastian.foodrats.feature.crew.data.firebase.FakeCrewDataSource
import es.schsebastian.foodrats.feature.crew.data.firebase.MemberDto
import es.schsebastian.foodrats.feature.crew.domain.test.FakeActiveCrewProvider
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeSessionForReconciler(session: Session?) : SessionProvider {
    val state = MutableStateFlow(session)
    override val current: Flow<Session?> get() = state
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        state.value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
}

/**
 * Regression locks for the remote-removal active-crew invalidation (P2 crew-lifecycle review,
 * 2026-07-20): being kicked from the ACTIVE crew clears the selection; a `null` crew emission
 * (transient error / not-found) or a signed-out session never does. Uses the same explicit
 * `CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())` app-scope pattern as
 * [CrewSyncEngineTest] (runTest's backgroundScope does not drive these eager collectors).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveCrewMembershipReconcilerTest {

    private val me = aid("uid-me")
    private val crewId = cid("c-1")

    private fun crewDto(vararg memberIds: String) = CrewDto(
        id = crewId.value,
        name = "C1",
        code = "ABC234",
        ownerId = memberIds.first(),
        createdAtEpochMs = 0L,
        memberIds = memberIds.toList(),
        members = memberIds.associateWith { MemberDto(joinedAtEpochMs = 0L) },
    )

    private fun scope() = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    private fun reconciler(
        activeCrew: FakeActiveCrewProvider,
        dataSource: FakeCrewDataSource,
        session: SessionProvider = FakeSessionForReconciler(Session(accountId = me, activeCrewId = crewId)),
        appScope: CoroutineScope = scope(),
    ): CoroutineScope {
        ActiveCrewMembershipReconciler(
            session = session,
            activeCrew = activeCrew,
            dataSource = dataSource,
            appScope = appScope,
        ).start()
        return appScope
    }

    @Test fun remote_removal_from_the_active_crew_clears_the_selection() = runTest {
        val dataSource = FakeCrewDataSource()
        val crewFlow = MutableStateFlow<CrewDto?>(crewDto("uid-owner", me.value))
        dataSource.observeCrewFlow = crewFlow
        val activeCrew = FakeActiveCrewProvider(initial = crewId)
        val scope = reconciler(activeCrew, dataSource)
        advanceUntilIdle()
        assertEquals(crewId, activeCrew.state.value) // still a member — untouched

        crewFlow.value = crewDto("uid-owner") // owner kicked me
        advanceUntilIdle()
        assertNull(activeCrew.state.value)
        scope.cancel()
    }

    @Test fun a_null_crew_emission_never_clears_the_selection() = runTest {
        // null conflates not-found with transient listener errors — must NOT clear.
        val dataSource = FakeCrewDataSource()
        dataSource.observeCrewFlow = MutableStateFlow<CrewDto?>(null)
        val activeCrew = FakeActiveCrewProvider(initial = crewId)
        val scope = reconciler(activeCrew, dataSource)
        advanceUntilIdle()
        assertEquals(crewId, activeCrew.state.value)
        scope.cancel()
    }

    @Test fun signed_out_session_never_clears_the_selection() = runTest {
        val dataSource = FakeCrewDataSource()
        dataSource.observeCrewFlow = MutableStateFlow<CrewDto?>(crewDto("uid-owner"))
        val activeCrew = FakeActiveCrewProvider(initial = crewId)
        val scope = reconciler(activeCrew, dataSource, session = FakeSessionForReconciler(null))
        advanceUntilIdle()
        assertEquals(crewId, activeCrew.state.value)
        scope.cancel()
    }

    @Test fun membership_in_the_active_crew_is_reevaluated_after_a_switch() = runTest {
        val other = cid("c-2")
        val dataSource = FakeCrewDataSource()
        // The fake returns the same flow for any crew id; model the SWITCHED-TO crew's doc.
        val crewFlow = MutableStateFlow<CrewDto?>(crewDto("uid-owner", me.value))
        dataSource.observeCrewFlow = crewFlow
        val activeCrew = FakeActiveCrewProvider(initial = crewId)
        val scope = reconciler(activeCrew, dataSource)
        advanceUntilIdle()

        activeCrew.set(other)
        advanceUntilIdle()
        assertEquals(other, activeCrew.state.value)

        crewFlow.value = crewDto("uid-owner") // removed from the now-active crew
        advanceUntilIdle()
        assertNull(activeCrew.state.value)
        scope.cancel()
    }
}
