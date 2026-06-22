package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.model.WeeklyChallenge
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

private class FakeSessionForChallenge(
    private val session: Session?,
) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session)
        else Result.failure(SessionError.NotSignedIn)
}

class SetCrewWeeklyChallengeUseCaseTest {

    private val ownerId = aid("uid-owner")
    private val crewId = cid("c-1")
    private val fixedNow = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val clock = FixedClock(fixedNow)

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
    ) = SetCrewWeeklyChallengeUseCase(repo, FakeSessionForChallenge(session), clock, connectivity, outbox)

    @Test fun sets_weekly_challenge_when_owner() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "Taco Tuesday")
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(Triple(crewId, "Taco Tuesday", fixedNow.toEpochMilliseconds()), repo.lastSetWeeklyChallenge)
    }

    @Test fun clears_weekly_challenge_on_blank_input() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "   ") // blank → null
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(Triple(crewId, null, null), repo.lastSetWeeklyChallenge)
    }

    @Test fun rejects_challenge_too_long() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val tooLong = "x".repeat(WeeklyChallenge.MAX_LEN + 1)
        val r = useCase(repo, session)(crewId, tooLong)
        assertEquals(Result.failure(CrewError.Validation.WeeklyChallengeTooLong), r)
        assertNull(repo.lastSetWeeklyChallenge)
    }

    @Test fun maps_missing_session_to_backend_unavailable() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session = null)(crewId, "Soup week")
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
        assertNull(repo.lastSetWeeklyChallenge)
    }

    @Test fun rejects_non_owner() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "Soup week")
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
        assertNull(repo.lastSetWeeklyChallenge)
    }

    @Test fun trims_challenge_before_saving() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "  Soup week  ")
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(Triple(crewId, "Soup week", fixedNow.toEpochMilliseconds()), repo.lastSetWeeklyChallenge)
    }

    @Test fun stamps_clock_now_as_set_at() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        useCase(repo, session)(crewId, "Pasta night")
        val recorded = repo.lastSetWeeklyChallenge
        assertEquals(fixedNow.toEpochMilliseconds(), recorded?.third)
    }

    @Test fun offline_enqueues_with_stamped_set_at_and_returns_ok() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, FakeConnectivityPort(online = false), outbox)(crewId, "  Soup week  ")
        assertIs<Result.Ok<Unit>>(r)
        assertNull(repo.lastSetWeeklyChallenge, "offline must not perform the direct write")
        assertEquals(
            listOf<PendingCommand>(
                PendingCommand.SetCrewWeeklyChallenge(crewId, ownerId, "Soup week", fixedNow.toEpochMilliseconds()),
            ),
            outbox.enqueued,
        )
    }

    @Test fun offline_blank_enqueues_clear_command_with_null_set_at() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, session, FakeConnectivityPort(online = false), outbox)(crewId, "   ")
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(
            listOf<PendingCommand>(PendingCommand.SetCrewWeeklyChallenge(crewId, ownerId, null, null)),
            outbox.enqueued,
        )
    }

    @Test fun offline_still_rejects_too_long_without_enqueue() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val outbox = RecordingOutboxPort()
        val tooLong = "x".repeat(WeeklyChallenge.MAX_LEN + 1)
        val r = useCase(repo, session, FakeConnectivityPort(online = false), outbox)(crewId, tooLong)
        assertEquals(Result.failure(CrewError.Validation.WeeklyChallengeTooLong), r)
        assertTrue(outbox.enqueued.isEmpty())
    }
}
