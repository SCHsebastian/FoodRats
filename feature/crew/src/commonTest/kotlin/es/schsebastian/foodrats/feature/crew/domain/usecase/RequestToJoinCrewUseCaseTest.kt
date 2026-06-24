package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RequestToJoinCrewUseCaseTest {
    private val requesterId = aid("uid-joiner")

    @Test fun malformed_code_returns_CodeMalformed_without_calling_repo() = runTest {
        val repo = FakeCrewRepository()
        val r = RequestToJoinCrewUseCase(repo).invoke("xx", requesterId)
        assertEquals(Result.failure(CrewError.Validation.CodeMalformed), r)
        assertEquals(null, repo.lastRequestToJoin)
    }

    @Test fun valid_code_delegates_to_repo() = runTest {
        val repo = FakeCrewRepository().apply { nextRequestToJoin = Result.success(Unit) }
        val r = RequestToJoinCrewUseCase(repo).invoke("ABCD23", requesterId)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(requesterId, repo.lastRequestToJoin?.second)
    }

    @Test fun repo_failure_is_propagated() = runTest {
        val repo = FakeCrewRepository().apply { nextRequestToJoin = Result.failure(CrewError.Membership.AlreadyMember) }
        val r = RequestToJoinCrewUseCase(repo).invoke("ABCD23", requesterId)
        assertEquals(Result.failure(CrewError.Membership.AlreadyMember), r)
    }
}
