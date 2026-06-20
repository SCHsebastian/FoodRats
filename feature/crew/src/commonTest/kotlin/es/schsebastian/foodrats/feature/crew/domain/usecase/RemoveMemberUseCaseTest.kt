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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSessionForRemove(
    private val session: Session?,
) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session)
        else Result.failure(SessionError.NotSignedIn)
}

class RemoveMemberUseCaseTest {

    private val ownerId = aid("uid-owner")
    private val memberId = aid("uid-member")
    private val strangerId = aid("uid-stranger")
    private val crewId = cid("c-1")
    private val t0 = Instant.fromEpochMilliseconds(0L)
    private val sampleCrew = Crew.of(
        id = crewId,
        name = "My Crew",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = ownerId,
        createdAt = t0,
        members = listOf(Member(ownerId, t0), Member(memberId, t0)),
    )

    private fun useCase(
        repo: FakeCrewRepository,
        session: Session?,
        connectivity: FakeConnectivityPort = FakeConnectivityPort(online = true),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
    ) = RemoveMemberUseCase(repo, FakeSessionForRemove(session), connectivity, outbox)

    @Test fun owner_removes_member_calls_port() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val useCase = useCase(repo, Session(ownerId, crewId), outbox = outbox)

        val r = useCase(crewId, memberId)

        assertIs<Result.Ok<Unit>>(r)
        assertEquals(Triple(crewId, ownerId, memberId), repo.lastRemoveMember)
        assertTrue(repo.crews.value.first { it.id == crewId }.members.none { it.accountId == memberId })
        assertTrue(outbox.enqueued.isEmpty())
    }

    @Test fun non_owner_returns_not_owner() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = useCase(repo, Session(memberId, crewId))

        val r = useCase(crewId, strangerId)

        assertEquals(Result.failure(CrewError.RemoveMember.NotOwner), r)
        assertNull(repo.lastRemoveMember)
    }

    @Test fun owner_removing_self_returns_cannot_remove_self() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = useCase(repo, Session(ownerId, crewId))

        val r = useCase(crewId, ownerId)

        assertEquals(Result.failure(CrewError.RemoveMember.CannotRemoveSelf), r)
        assertNull(repo.lastRemoveMember)
    }

    @Test fun unknown_member_returns_member_not_found() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = useCase(repo, Session(ownerId, crewId))

        val r = useCase(crewId, strangerId)

        assertEquals(Result.failure(CrewError.RemoveMember.MemberNotFound), r)
        assertNull(repo.lastRemoveMember)
    }

    @Test fun offline_validates_then_enqueues_and_returns_ok() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val useCase = useCase(
            repo,
            Session(ownerId, crewId),
            connectivity = FakeConnectivityPort(online = false),
            outbox = outbox,
        )

        val r = useCase(crewId, memberId)

        assertIs<Result.Ok<Unit>>(r)
        assertNull(repo.lastRemoveMember, "offline must not perform the direct write")
        assertEquals(
            listOf<PendingCommand>(PendingCommand.RemoveMember(crewId, ownerId, memberId)),
            outbox.enqueued,
        )
    }

    @Test fun offline_still_rejects_non_owner_without_enqueue() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val useCase = useCase(
            repo,
            Session(memberId, crewId),
            connectivity = FakeConnectivityPort(online = false),
            outbox = outbox,
        )

        val r = useCase(crewId, strangerId)

        assertEquals(Result.failure(CrewError.RemoveMember.NotOwner), r)
        assertTrue(outbox.enqueued.isEmpty())
    }

    @Test fun connectivity_class_error_falls_back_to_outbox() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew)).apply {
            nextRemoveMember = Result.failure(CrewError.Backend.Unavailable)
        }
        val outbox = RecordingOutboxPort()
        val useCase = useCase(repo, Session(ownerId, crewId), outbox = outbox)

        val r = useCase(crewId, memberId)

        assertIs<Result.Ok<Unit>>(r)
        assertEquals(
            listOf<PendingCommand>(PendingCommand.RemoveMember(crewId, ownerId, memberId)),
            outbox.enqueued,
        )
    }
}
