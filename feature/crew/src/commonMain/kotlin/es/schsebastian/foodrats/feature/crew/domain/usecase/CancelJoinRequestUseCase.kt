package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.error.toCrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Withdraws the signed-in user's own pending request to join [crewId] (the requester-side cancel).
 * The requester is resolved from [SessionProvider] — the caller only supplies the crew. Idempotent:
 * cancelling a request that's already gone (declined/approved) succeeds. Complements
 * [RequestToJoinCrewUseCase] so a requester is no longer stranded after filing a request.
 */
class CancelJoinRequestUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId): Result<Unit, CrewError> {
        val requester = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(s.error.toCrewError())
        }
        return repository.cancelJoinRequest(crewId, requester)
    }
}
