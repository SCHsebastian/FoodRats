package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeSessionForCancel(private val session: Session?) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session) else Result.failure(SessionError.NotSignedIn)
}

class CancelJoinRequestUseCaseTest {

    private val me = aid("uid-me")
    private val crewId = cid("c-1")

    @Test fun cancels_using_requester_resolved_from_session() = runTest {
        val repo = FakeCrewRepository()
        val r = CancelJoinRequestUseCase(repo, FakeSessionForCancel(Session(me, crewId)))(crewId)
        assertEquals(Result.success(Unit), r)
        assertEquals(crewId to me, repo.lastCancel)
    }

    @Test fun no_session_returns_not_signed_in_without_cancelling() = runTest {
        val repo = FakeCrewRepository()
        val r = CancelJoinRequestUseCase(repo, FakeSessionForCancel(null))(crewId)
        assertEquals(Result.failure(CrewError.Session.NotSignedIn), r)
        assertNull(repo.lastCancel)
    }
}
