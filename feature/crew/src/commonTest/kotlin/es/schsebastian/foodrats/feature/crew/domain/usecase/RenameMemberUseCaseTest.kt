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

private class FakeSessionForMemberRename(
    private val session: Session?,
) : SessionProvider {
    override val current: Flow<Session?> = flowOf(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session)
        else Result.failure(SessionError.NotSignedIn)
}

class RenameMemberUseCaseTest {

    private val memberId = aid("uid-member")
    private val crewId = cid("c-1")
    private val sampleCrew = Crew(
        id = crewId,
        name = "My Crew",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = memberId,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(Member(memberId, "Old Name", null, Instant.fromEpochMilliseconds(0L))),
    )
    private val session = Session(accountId = memberId, activeCrewId = crewId)

    @Test fun rejects_blank_display_name() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = RenameMemberUseCase(repo, FakeSessionForMemberRename(session))
        val r = useCase("   ")
        assertEquals(Result.failure(CrewError.Validation.DisplayNameBlank), r)
    }

    @Test fun rejects_display_name_over_40_chars() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = RenameMemberUseCase(repo, FakeSessionForMemberRename(session))
        val r = useCase("x".repeat(41))
        assertEquals(Result.failure(CrewError.Validation.DisplayNameTooLong), r)
    }

    @Test fun renames_self_in_active_crew() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = RenameMemberUseCase(repo, FakeSessionForMemberRename(session))
        val r = useCase("  Trimmed Name  ")
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(Triple(crewId, memberId, "Trimmed Name"), repo.lastMemberRename)
    }

    @Test fun errors_when_no_active_crew() = runTest {
        val noCrewSession = Session(accountId = memberId, activeCrewId = null)
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val useCase = RenameMemberUseCase(repo, FakeSessionForMemberRename(noCrewSession))
        val r = useCase("Valid Name")
        assertEquals(Result.failure(CrewError.Membership.NotMember), r)
    }
}
