package es.schsebastian.foodrats.feature.crew.domain.usecase

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeSessionForBlindVoting(
    private val session: Session?,
) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session)
        else Result.failure(SessionError.NotSignedIn)
}

class SetBlindVotingUseCaseTest {

    private val ownerId = aid("uid-owner")
    private val crewId = cid("c-1")
    private val sampleCrew = Crew.of(
        id = crewId,
        name = "My Crew",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = ownerId,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(Member(ownerId, Instant.fromEpochMilliseconds(0L))),
    )

    @Test fun enables_blind_voting_when_owner() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = SetBlindVotingUseCase(repo, FakeSessionForBlindVoting(session))
        val r = useCase(crewId, enabled = true)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId to true, repo.lastSetBlindVoting)
    }

    @Test fun passes_disabled_flag_through_to_repository() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = SetBlindVotingUseCase(repo, FakeSessionForBlindVoting(session))
        val r = useCase(crewId, enabled = false)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId to false, repo.lastSetBlindVoting)
    }

    @Test fun maps_missing_session_to_backend_unavailable() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = SetBlindVotingUseCase(repo, FakeSessionForBlindVoting(session = null))
        val r = useCase(crewId, enabled = true)
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
        assertEquals(null, repo.lastSetBlindVoting)
    }

    @Test fun rejects_non_owner() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = SetBlindVotingUseCase(repo, FakeSessionForBlindVoting(session))
        val r = useCase(crewId, enabled = true)
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
    }
}
