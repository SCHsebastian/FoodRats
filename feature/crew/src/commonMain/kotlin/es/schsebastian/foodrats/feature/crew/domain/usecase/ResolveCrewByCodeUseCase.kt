package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Resolves a crew from an invite [rawCode] for the accept-invite PREVIEW (read-only; does not join).
 * Validates the code shape first ([CrewError.Validation.CodeMalformed]) so a corrupt link fails fast
 * before a backend round-trip, then reads the crew via [CrewRepository.findByCode].
 */
class ResolveCrewByCodeUseCase(private val repo: CrewRepository) {
    suspend operator fun invoke(rawCode: String): Result<Crew, CrewError> =
        when (val parsed = CrewCode.of(rawCode)) {
            is Result.Err -> Result.failure(parsed.error)
            is Result.Ok  -> repo.findByCode(parsed.value)
        }
}
