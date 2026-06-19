package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.CrewName
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

/**
 * OFFLINE-FIRST (P2 §0.5). Validation + ownership (resolved via [SessionProvider]) run first,
 * exactly as before. When the device is offline — or the direct write fails with a
 * connectivity-class error ([CrewError.Backend.Network] / [CrewError.Backend.Unavailable]) —
 * the rename is durably parked in the [OutboxPort] and the use case returns [Result.Ok]; the
 * `OutboxRunner` replays it (idempotently — rename sets the name) when connectivity returns.
 */
class RenameCrewUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(crewId: CrewId, newName: String): Result<Unit, CrewError> {
        val crewName = CrewName.of(newName).getOrElse { return Result.failure(it) }
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        if (!connectivity.isOnline().first()) {
            return enqueue(crewId, accountId, crewName.value)
        }
        return when (val r = repository.renameCrew(crewId, accountId, crewName.value)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                CrewError.Backend.Network, CrewError.Backend.Unavailable ->
                    enqueue(crewId, accountId, crewName.value)
                else -> r
            }
        }
    }

    private suspend fun enqueue(
        crewId: CrewId,
        requestedBy: AccountId,
        newName: String,
    ): Result<Unit, CrewError> {
        outbox.enqueue(PendingCommand.RenameCrew(crewId, requestedBy, newName))
        return Result.success(Unit)
    }
}
