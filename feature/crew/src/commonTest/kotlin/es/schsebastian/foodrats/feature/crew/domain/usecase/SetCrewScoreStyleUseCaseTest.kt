package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeSessionForScoreStyle(
    private val session: Session?,
) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session)
        else Result.failure(SessionError.NotSignedIn)
}

class SetCrewScoreStyleUseCaseTest {

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
        connectivity: FakeConnectivityPort = FakeConnectivityPort(online = true),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
    ) = SetCrewScoreStyleUseCase(repo, FakeSessionForScoreStyle(session), connectivity, outbox)

    @Test fun sets_score_style_when_owner() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, CrewScoreStyle.Emoji)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(CrewScoreStyle.Emoji, repo.crews.value.first { it.id == crewId }.scoreStyle)
    }

    @Test fun rejects_non_owner() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, CrewScoreStyle.Numeric)
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
        assertEquals(CrewScoreStyle.Stars, repo.crews.value.first { it.id == crewId }.scoreStyle)
    }

    @Test fun maps_missing_session_to_backend_unavailable() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session = null)(crewId, CrewScoreStyle.Emoji)
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
    }

    @Test fun offline_enqueues_style_key_and_returns_ok() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, FakeConnectivityPort(online = false), outbox)(crewId, CrewScoreStyle.Emoji)
        assertIs<Result.Ok<Unit>>(r)
        // offline must not perform the direct write — style stays at the default.
        assertEquals(CrewScoreStyle.Stars, repo.crews.value.first { it.id == crewId }.scoreStyle)
        assertEquals(
            listOf<PendingCommand>(PendingCommand.SetCrewScoreStyle(crewId, ownerId, "emoji")),
            outbox.enqueued,
        )
    }

    @Test fun offline_no_session_returns_unavailable_without_enqueue() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session = null, connectivity = FakeConnectivityPort(online = false), outbox = outbox)(
            crewId, CrewScoreStyle.Emoji,
        )
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
        assertTrue(outbox.enqueued.isEmpty())
    }
}
