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

private class FakeSessionProvider(
    private val session: Session?,
) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session)
        else Result.failure(SessionError.NotSignedIn)
}

class RenameCrewUseCaseTest {

    private val ownerId = aid("uid-owner")
    private val crewId = cid("c-1")
    private val sampleCrew = Crew.of(
        id = crewId,
        name = "Old Name",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = ownerId,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(Member(ownerId, Instant.fromEpochMilliseconds(0L))),
    )
    private val session = Session(accountId = ownerId, activeCrewId = crewId)

    private fun useCase(
        repo: FakeCrewRepository,
        session: Session?,
        connectivity: FakeConnectivityPort = FakeConnectivityPort(online = true),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
    ) = RenameCrewUseCase(repo, FakeSessionProvider(session), connectivity, outbox)

    @Test fun rejects_blank_name() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "   ")
        assertEquals(Result.failure(CrewError.Validation.NameBlank), r)
    }

    @Test fun rejects_name_over_40_chars() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "x".repeat(41))
        assertEquals(Result.failure(CrewError.Validation.NameTooLong), r)
    }

    @Test fun rejects_non_owner() = runTest {
        val nonOwner = aid("uid-other")
        val nonOwnerSession = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, nonOwnerSession)(crewId, "New Name")
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
    }

    @Test fun renames_when_owner_and_valid_online() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, outbox = outbox)(crewId, "  New Name  ")
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(Pair(crewId, "New Name"), repo.lastRename)
        assertTrue(outbox.enqueued.isEmpty())
    }

    @Test fun offline_validates_then_enqueues_and_returns_ok() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, connectivity = FakeConnectivityPort(online = false), outbox = outbox)(
            crewId, "  New Name  ",
        )
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(null, repo.lastRename, "offline must not perform the direct write")
        assertEquals(
            listOf<PendingCommand>(PendingCommand.RenameCrew(crewId, ownerId, "New Name")),
            outbox.enqueued,
        )
    }

    @Test fun offline_still_rejects_invalid_name_without_enqueue() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, connectivity = FakeConnectivityPort(online = false), outbox = outbox)(
            crewId, "   ",
        )
        assertEquals(Result.failure(CrewError.Validation.NameBlank), r)
        assertTrue(outbox.enqueued.isEmpty())
    }
}
