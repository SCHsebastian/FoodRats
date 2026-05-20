package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Wraps [CrewRepository.updateMyAvatar] with the caller's account identity. The crew the
 * avatar is denormalized into is the one the settings screen is editing, not necessarily
 * the session's active crew, so we accept it as an explicit parameter.
 */
class UpdateMyAvatarUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, bytes: ByteArray): Result<String, CrewError> {
        if (bytes.isEmpty()) return Result.failure(CrewError.Backend.Unavailable)
        val currentSession = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        return repository.updateMyAvatar(crewId, currentSession.accountId, bytes)
    }
}
