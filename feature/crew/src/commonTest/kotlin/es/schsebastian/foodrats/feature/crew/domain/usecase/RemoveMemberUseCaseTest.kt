package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoveMemberUseCaseTest {
    @Test
    fun always_returns_not_implemented() = runTest {
        val uc = RemoveMemberUseCase()
        val r = uc.invoke((AccountId.of("u-other") as Result.Ok).value)
        assertTrue(r is Result.Err)
        assertEquals(CrewError.NotImplemented.RemoveMember, r.error)
    }
}
