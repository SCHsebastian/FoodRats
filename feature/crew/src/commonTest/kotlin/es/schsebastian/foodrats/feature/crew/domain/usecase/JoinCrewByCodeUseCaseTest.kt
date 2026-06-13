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
import kotlin.test.assertIs

class JoinCrewByCodeUseCaseTest {
    private val joinerId = aid("uid-joiner")
    private val crew = Crew.of(
        id = cid("c-2"),
        name = "Friends",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = aid("uid-other"),
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(Member(aid("uid-other"), Instant.fromEpochMilliseconds(0L))),
    )

    @Test fun malformed_code_returns_CodeMalformed_without_calling_repo() = runTest {
        val repo = FakeCrewRepository()
        val r = JoinCrewByCodeUseCase(repo).invoke("xx", joinerId, "Pat")
        assertEquals(Result.failure(CrewError.Validation.CodeMalformed), r)
    }

    @Test fun valid_code_delegates_to_repo() = runTest {
        val repo = FakeCrewRepository().apply { nextJoin = Result.success(crew) }
        val r = JoinCrewByCodeUseCase(repo).invoke("ABCD23", joinerId, "Pat")
        assertIs<Result.Ok<Crew>>(r)
        assertEquals(crew, r.value)
    }
}
