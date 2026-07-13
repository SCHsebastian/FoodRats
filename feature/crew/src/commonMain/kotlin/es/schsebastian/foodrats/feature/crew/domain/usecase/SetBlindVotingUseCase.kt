package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.error.toCrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

/**
 * Toggles the active crew's blind-voting policy. Owner-only; authorization is enforced by the repository.
 *
 * OFFLINE-FIRST (P2 §0.5). When the device is offline — or the direct write fails with a
 * connectivity-class error ([CrewError.Backend.Network] / [CrewError.Backend.Unavailable]) — the
 * toggle is durably parked in the [OutboxPort] and the use case returns [Result.Ok]; the
 * `OutboxRunner` replays it (idempotently — it sets the flag) when connectivity returns.
 */
class SetBlindVotingUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(crewId: CrewId, enabled: Boolean): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(s.error.toCrewError())
        }
        if (!connectivity.isOnline().first()) {
            repository.offlineOwnerGuard(crewId, accountId)?.let { return Result.failure(it) }
            return enqueue(crewId, accountId, enabled)
        }
        return when (val r = repository.setBlindVoting(crewId, accountId, enabled)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                CrewError.Backend.Network, CrewError.Backend.Unavailable ->
                    enqueue(crewId, accountId, enabled)
                else -> r
            }
        }
    }

    private suspend fun enqueue(
        crewId: CrewId,
        requestedBy: AccountId,
        enabled: Boolean,
    ): Result<Unit, CrewError> {
        return when (outbox.enqueue(PendingCommand.SetBlindVoting(crewId, requestedBy, enabled))) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(CrewError.Backend.Unavailable)
        }
    }
}
