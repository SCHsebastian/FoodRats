package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LeaveCrewUseCaseTest {
    @Test fun delegates_to_repo() = runTest {
        val repo = FakeCrewRepository().apply { nextLeave = Result.success(Unit) }
        val r = LeaveCrewUseCase(repo).invoke(cid("c-1"), aid("uid"))
        assertIs<Result.Ok<Unit>>(r)
    }

    @Test fun propagates_error() = runTest {
        val repo = FakeCrewRepository().apply {
            nextLeave = Result.failure(CrewError.Membership.NotMember)
        }
        val r = LeaveCrewUseCase(repo).invoke(cid("c-1"), aid("uid"))
        assertEquals(
            Result.failure(CrewError.Membership.NotMember),
            r,
        )
    }
}
