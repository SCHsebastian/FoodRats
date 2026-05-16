package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

class LeaveCrewUseCase(private val repo: CrewRepository) {
    suspend operator fun invoke(crewId: CrewId, leaver: AccountId): Result<Unit, CrewError> =
        repo.leave(crewId, leaver)
}
