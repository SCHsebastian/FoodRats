package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Transfers crew ownership to [newOwner] (a current member). The requester is the signed-in owner
 * from [SessionProvider]. Online-only by design — a deliberate, rare hand-off; a connectivity-class
 * failure surfaces as [CrewError.Backend.Network] for the UI to retry, rather than queueing.
 */
class TransferCrewOwnershipUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, newOwner: AccountId): Result<Unit, CrewError> {
        val requestedBy = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        return repository.transferOwnership(crewId, requestedBy, newOwner)
    }
}
