package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

class JoinCrewByCodeUseCase(private val repo: CrewRepository) {
    suspend operator fun invoke(
        rawCode: String,
        joiner: AccountId,
    ): Result<Crew, CrewError> = when (val parsed = CrewCode.of(rawCode)) {
        is Result.Err -> Result.failure(parsed.error)
        is Result.Ok  -> repo.joinByCode(parsed.value, joiner)
    }
}
