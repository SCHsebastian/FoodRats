package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.feature.crew.domain.test.FakeConnectivityPort
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.RecordingOutboxPort
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
import kotlin.test.assertTrue

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

    private fun useCase(
        repo: FakeCrewRepository,
        session: Session?,
        connectivity: FakeConnectivityPort = FakeConnectivityPort(online = true),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
    ) = SetCrewBannerFocalUseCase(repo, FakeSessionForFocal(session), connectivity, outbox)

    @Test fun owner_sets_focal_point() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, 0.25f)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(0.25f, repo.crews.value.first { it.id == crewId }.bannerFocalY)
    }

    @Test fun out_of_range_focal_is_clamped() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = useCase(repo, session)
        useCase(crewId, 1.8f)
        assertEquals(1f, repo.crews.value.first { it.id == crewId }.bannerFocalY)
        useCase(crewId, -0.5f)
        assertEquals(0f, repo.crews.value.first { it.id == crewId }.bannerFocalY)
    }

    @Test fun non_owner_is_rejected() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, 0.3f)
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
    }

    @Test fun offline_non_owner_rejected_without_enqueue() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, connectivity = FakeConnectivityPort(online = false), outbox = outbox)(crewId, 0.3f)
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
        assertTrue(outbox.enqueued.isEmpty(), "a non-owner must not park a doomed offline command")
    }

    @Test fun no_session_returns_not_signed_in() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session = null)(crewId, 0.3f)
        assertEquals(Result.failure(CrewError.Session.NotSignedIn), r)
    }

    @Test fun offline_enqueues_clamped_focal_and_returns_ok() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, FakeConnectivityPort(online = false), outbox)(crewId, 1.8f)
        assertIs<Result.Ok<Unit>>(r)
        assertNull(repo.lastSetBannerFocal, "offline must not perform the direct write")
        assertEquals(
            listOf<PendingCommand>(PendingCommand.SetCrewBannerFocalY(crewId, ownerId, 1f)), // clamped
            outbox.enqueued,
        )
    }

    @Test fun no_session_offline_still_returns_not_signed_in_without_enqueue() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session = null, connectivity = FakeConnectivityPort(online = false), outbox = outbox)(crewId, 0.3f)
        assertEquals(Result.failure(CrewError.Session.NotSignedIn), r)
        assertTrue(outbox.enqueued.isEmpty())
    }
}
