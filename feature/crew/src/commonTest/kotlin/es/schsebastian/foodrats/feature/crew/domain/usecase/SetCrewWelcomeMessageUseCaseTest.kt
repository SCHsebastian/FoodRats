package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.model.WelcomeMessage
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

private class FakeSessionForWelcome(
    private val session: Session?,
) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session)
        else Result.failure(SessionError.NotSignedIn)
}

class SetCrewWelcomeMessageUseCaseTest {

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
    ) = SetCrewWelcomeMessageUseCase(repo, FakeSessionForWelcome(session))

    @Test fun sets_welcome_message_when_owner() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "Cook before 10 PM!")
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId to "Cook before 10 PM!", repo.lastSetWelcomeMessage)
    }

    @Test fun clears_welcome_message_on_blank_input() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "   ")   // blank → null
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId to null, repo.lastSetWelcomeMessage)
    }

    @Test fun rejects_message_too_long() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val tooLong = "x".repeat(WelcomeMessage.MAX_LEN + 1)
        val r = useCase(repo, session)(crewId, tooLong)
        assertEquals(Result.failure(CrewError.Validation.WelcomeMessageTooLong), r)
        assertNull(repo.lastSetWelcomeMessage)
    }

    @Test fun maps_missing_session_to_backend_unavailable() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session = null)(crewId, "Cook before 10 PM!")
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
        assertNull(repo.lastSetWelcomeMessage)
    }

    @Test fun rejects_non_owner() = runTest {
        val nonOwner = aid("uid-other")
        val session = Session(accountId = nonOwner, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "Cook before 10 PM!")
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
        assertNull(repo.lastSetWelcomeMessage)
    }

    @Test fun trims_message_before_saving() = runTest {
        val session = Session(accountId = ownerId, activeCrewId = crewId)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val r = useCase(repo, session)(crewId, "  house rules  ")
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(crewId to "house rules", repo.lastSetWelcomeMessage)
    }
}
