package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Approves a pending join request. The approver is the signed-in account from [SessionProvider]
 * (the caller only supplies the [requester] to admit); the repository + Firestore rule re-check that
 * the approver owns the crew and that the membership cap is respected, atomically.
 */
class ApproveJoinRequestUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, requester: AccountId): Result<Unit, CrewError> {
        val requestedBy = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        return repository.approveJoinRequest(crewId, requestedBy, requester)
    }
}
