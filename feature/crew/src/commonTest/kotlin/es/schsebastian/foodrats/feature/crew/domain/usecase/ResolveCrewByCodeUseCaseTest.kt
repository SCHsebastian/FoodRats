package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveCrewByCodeUseCaseTest {

    private val code = (CrewCode.of("ABCD23") as Result.Ok).value
    private val crew = Crew.of(
        id = cid("c-A"),
        name = "Crew A",
        code = code,
        ownerId = aid("uid-owner"),
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(Member(aid("uid-owner"), Instant.fromEpochMilliseconds(0L))),
    )

    @Test fun resolves_crew_for_a_known_code() = runTest {
        val repo = FakeCrewRepository(initial = listOf(crew))
        val result = ResolveCrewByCodeUseCase(repo)(code.value)
        assertEquals(Result.success(crew), result)
    }

    @Test fun unknown_code_returns_code_unknown() = runTest {
        val repo = FakeCrewRepository(initial = emptyList())
        val result = ResolveCrewByCodeUseCase(repo)(code.value)
        assertEquals(Result.failure(CrewError.Invite.CodeUnknown), result)
    }

    @Test fun malformed_code_returns_validation_without_touching_repo() = runTest {
        val repo = FakeCrewRepository().apply {
            nextFindByCode = Result.failure(CrewError.Backend.Unavailable) // must not be reached
        }
        val result = ResolveCrewByCodeUseCase(repo)("nope")
        assertEquals(Result.failure(CrewError.Validation.CodeMalformed), result)
    }
}
