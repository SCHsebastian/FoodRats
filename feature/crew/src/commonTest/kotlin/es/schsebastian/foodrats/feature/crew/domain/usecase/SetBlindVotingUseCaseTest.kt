package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.test.FakeConnectivityPort
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.RecordingOutboxPort
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    private fun useCase(
        repo: FakeCrewRepository,
        session: Session?,
        connectivity: FakeConnectivityPort = FakeConnectivityPort(online = true),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
    ) = SetBlindVotingUseCase(repo, FakeSessionForBlindVoting(session), connectivity, outbox)

    @Test fun enables_blind_voting_when_owner() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, outbox = outbox)(crewId, enabled = true)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId to true, repo.lastSetBlindVoting)
        assertTrue(outbox.enqueued.isEmpty())
    }

    @Test fun passes_disabled_flag_through_to_repository() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, enabled = false)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId to false, repo.lastSetBlindVoting)
    }

    @Test fun maps_missing_session_to_not_signed_in() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session = null)(crewId, enabled = true)
        assertEquals(Result.failure(CrewError.Session.NotSignedIn), r)
        assertEquals(null, repo.lastSetBlindVoting)
    }

    @Test fun rejects_non_owner() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, enabled = true)
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
    }

    @Test fun offline_non_owner_rejected_without_enqueue() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, connectivity = FakeConnectivityPort(online = false), outbox = outbox)(crewId, enabled = true)
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
        assertTrue(outbox.enqueued.isEmpty(), "a non-owner must not park a doomed offline command")
    }

    @Test fun offline_enqueues_and_returns_ok() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, connectivity = FakeConnectivityPort(online = false), outbox = outbox)(
            crewId, enabled = true,
        )
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(null, repo.lastSetBlindVoting, "offline must not perform the direct write")
        assertEquals(
            listOf<PendingCommand>(PendingCommand.SetBlindVoting(crewId, ownerId, true)),
            outbox.enqueued,
        )
    }

    @Test fun connectivity_class_error_falls_back_to_outbox() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew)).apply {
            nextSetBlindVoting = Result.failure(CrewError.Backend.Network)
        }
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, outbox = outbox)(crewId, enabled = true)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(
            listOf<PendingCommand>(PendingCommand.SetBlindVoting(crewId, ownerId, true)),
            outbox.enqueued,
        )
    }
}
