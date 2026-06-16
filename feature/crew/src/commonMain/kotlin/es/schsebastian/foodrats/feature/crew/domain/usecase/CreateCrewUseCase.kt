package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewName
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

class CreateCrewUseCase(private val repo: CrewRepository) {
    suspend operator fun invoke(
        name: String,
        founder: AccountId,
        founderDisplayName: String,
    ): Result<Crew, CrewError> {
        val crewName = CrewName.of(name).getOrElse { return Result.failure(it) }
        return repo.create(crewName.value, founder, founderDisplayName)
    }
}
