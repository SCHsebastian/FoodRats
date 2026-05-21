package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError

class RemoveMemberUseCase {
    suspend operator fun invoke(memberId: AccountId): Result<Unit, CrewError> =
        Result.failure(CrewError.NotImplemented.RemoveMember)
}
