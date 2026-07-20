package es.schsebastian.foodrats.feature.crew.domain.usecase

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
import es.schsebastian.foodrats.feature.crew.domain.test.FakeActiveCrewProvider
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
import kotlin.test.assertNull

private class FakeSessionForDelete(
    private val session: Session?,
) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session)
        else Result.failure(SessionError.NotSignedIn)
}

class DeleteCrewUseCaseTest {

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

    @Test fun rejects_non_owner() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = DeleteCrewUseCase(repo, FakeSessionForDelete(session), FakeActiveCrewProvider())
        val r = useCase(crewId)
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
    }

    @Test fun deletes_when_owner() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = DeleteCrewUseCase(repo, FakeSessionForDelete(session), FakeActiveCrewProvider())
        val r = useCase(crewId)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId, repo.lastDelete)
        assertNull(repo.crews.value.firstOrNull { it.id == crewId })
    }

    @Test fun deleting_the_active_crew_clears_the_active_selection() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val activeCrew = FakeActiveCrewProvider(initial = crewId)
        val r = DeleteCrewUseCase(repo, FakeSessionForDelete(session), activeCrew).invoke(crewId)
        assertIs<Result.Ok<Unit>>(r)
        assertNull(activeCrew.state.value)
    }

    @Test fun deleting_a_non_active_crew_keeps_the_active_selection() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val other = cid("c-other")
        val activeCrew = FakeActiveCrewProvider(initial = other)
        val r = DeleteCrewUseCase(repo, FakeSessionForDelete(session), activeCrew).invoke(crewId)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(other, activeCrew.state.value)
    }
}
