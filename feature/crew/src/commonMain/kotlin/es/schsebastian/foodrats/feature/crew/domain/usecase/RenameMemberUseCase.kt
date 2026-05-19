package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

class RenameMemberUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(newDisplayName: String): Result<Unit, CrewError> {
        val trimmed = newDisplayName.trim()
        if (trimmed.isEmpty()) return Result.failure(CrewError.Validation.DisplayNameBlank)
        if (trimmed.length > MAX) return Result.failure(CrewError.Validation.DisplayNameTooLong)
        val currentSession = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        val crewId = currentSession.activeCrewId ?: return Result.failure(CrewError.Membership.NotMember)
        return repository.renameMember(crewId, currentSession.accountId, trimmed)
    }

    companion object {
        const val MAX = 40
    }
}
