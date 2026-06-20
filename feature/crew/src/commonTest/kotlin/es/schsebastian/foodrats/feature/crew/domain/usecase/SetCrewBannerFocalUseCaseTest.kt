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

private class FakeSessionForFocal(private val session: Session?) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session)
        else Result.failure(SessionError.NotSignedIn)
}

class SetCrewBannerFocalUseCaseTest {

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

    @Test fun owner_sets_focal_point() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = SetCrewBannerFocalUseCase(repo, FakeSessionForFocal(session))
        val r = useCase(crewId, 0.25f)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(0.25f, repo.crews.value.first { it.id == crewId }.bannerFocalY)
    }

    @Test fun out_of_range_focal_is_clamped() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = SetCrewBannerFocalUseCase(repo, FakeSessionForFocal(session))
        useCase(crewId, 1.8f)
        assertEquals(1f, repo.crews.value.first { it.id == crewId }.bannerFocalY)
        useCase(crewId, -0.5f)
        assertEquals(0f, repo.crews.value.first { it.id == crewId }.bannerFocalY)
    }

    @Test fun non_owner_is_rejected() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = SetCrewBannerFocalUseCase(repo, FakeSessionForFocal(session))
        val r = useCase(crewId, 0.3f)
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
    }

    @Test fun no_session_returns_unavailable() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = SetCrewBannerFocalUseCase(repo, FakeSessionForFocal(null))
        val r = useCase(crewId, 0.3f)
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
    }
}
