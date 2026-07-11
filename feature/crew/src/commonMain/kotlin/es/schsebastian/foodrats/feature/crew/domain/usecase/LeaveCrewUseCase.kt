package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

/**
 * OFFLINE-FIRST (P2 §0.5). When the device is offline — or the direct write fails with a
 * connectivity-class error ([CrewError.Backend.Network] / [CrewError.Backend.Unavailable]) — the
 * leave is durably parked in the [OutboxPort] and the use case returns [Result.Ok]; the
 * `OutboxRunner` replays it (idempotently — leaving a crew you're no longer in succeeds) when
 * connectivity returns.
 */
class LeaveCrewUseCase(
    private val repo: CrewRepository,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    /**
     * [successor] (optional) is the member the owner picked to inherit the crew when an OWNER leaves
     * with others remaining. It is honored only on the online path — the offline replay can't carry
     * the choice through the existing [PendingCommand.LeaveCrew] shape, so a queued owner-leave falls
     * back to the longest-tenured remaining member (the documented auto policy).
     */
    suspend operator fun invoke(
        crewId: CrewId,
        leaver: AccountId,
        successor: AccountId? = null,
    ): Result<Unit, CrewError> {
        if (!connectivity.isOnline().first()) {
            return enqueue(crewId, leaver)
        }
        return when (val r = repo.leave(crewId, leaver, successor)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                CrewError.Backend.Network, CrewError.Backend.Unavailable ->
                    enqueue(crewId, leaver)
                else -> r
            }
        }
    }

    private suspend fun enqueue(crewId: CrewId, leaver: AccountId): Result<Unit, CrewError> {
        return when (outbox.enqueue(PendingCommand.LeaveCrew(crewId, leaver))) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(CrewError.Backend.Unavailable)
        }
    }
}
