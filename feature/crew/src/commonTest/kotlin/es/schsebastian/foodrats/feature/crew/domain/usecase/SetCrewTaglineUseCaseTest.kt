package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.CrewTagline
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
import kotlin.test.assertNull

private class FakeSessionForTagline(
    private val session: Session?,
) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session)
        else Result.failure(SessionError.NotSignedIn)
}

class SetCrewTaglineUseCaseTest {

    private val ownerId = aid("uid-owner")
    private val crewId = cid("c-1")
    private val sampleCrew = Crew.of(
        id = crewId,
        name = "Test Crew",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = ownerId,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(Member(ownerId, Instant.fromEpochMilliseconds(0L))),
    )

    private fun useCase(
        repo: FakeCrewRepository,
        session: Session?,
    ) = SetCrewTaglineUseCase(repo, FakeSessionForTagline(session))

    @Test fun sets_tagline_when_owner() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "only home-cooked")
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId to "only home-cooked", repo.lastSetTagline)
    }

    @Test fun clears_tagline_on_blank_input() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "   ")   // blank → null
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId to null, repo.lastSetTagline)
    }

    @Test fun rejects_tagline_too_long() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val tooLong = "x".repeat(CrewTagline.MAX_LEN + 1)
        val r = useCase(repo, session)(crewId, tooLong)
        assertEquals(Result.failure(CrewError.Validation.TaglineTooLong), r)
        assertNull(repo.lastSetTagline)
    }

    @Test fun maps_missing_session_to_backend_unavailable() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session = null)(crewId, "only home-cooked")
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
        assertNull(repo.lastSetTagline)
    }

    @Test fun rejects_non_owner() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "some tagline")
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
        assertNull(repo.lastSetTagline)
    }

    @Test fun trims_tagline_before_saving() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "  house rules  ")
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId to "house rules", repo.lastSetTagline)
    }
}
