package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

class RenameCrewUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, newName: String): Result<Unit, CrewError> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(CrewError.Validation.NameBlank)
        if (trimmed.length > MAX_NAME) return Result.failure(CrewError.Validation.NameTooLong)
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        return repository.renameCrew(crewId, accountId, trimmed)
    }

    companion object {
        const val MAX_NAME = 40
    }
}
