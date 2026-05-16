package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

class CreateCrewUseCase(private val repo: CrewRepository) {
    suspend operator fun invoke(
        name: String,
        founder: AccountId,
        founderDisplayName: String,
    ): Result<Crew, CrewError> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(CrewError.Validation.NameBlank)
        if (trimmed.length > MAX_NAME_LEN) return Result.failure(CrewError.Validation.NameTooLong)
        return repo.create(trimmed, founder, founderDisplayName)
    }
    companion object { const val MAX_NAME_LEN = 40 }
}
