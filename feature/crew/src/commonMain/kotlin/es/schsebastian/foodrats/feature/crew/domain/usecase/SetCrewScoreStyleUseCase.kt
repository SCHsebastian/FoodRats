package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
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
 * Sets the crew's Score display vocabulary (C8). Owner-only; authorization is enforced by the
 * repository and the `['scoreStyle']` Firestore rule arm.
 *
 * No validation errors: [CrewScoreStyle] is a sealed interface with exactly 3 data-object leaves;
 * the caller passes a typed value and the repository converts it to the Firestore string via
 * [es.schsebastian.foodrats.feature.crew.data.firebase.toDto]. Re-uses the existing backend /
 * persist [CrewError] leaves — no new validation leaf is needed.
 *
 * OFFLINE-FIRST (P2 §0.5). When offline — or the direct write fails with a connectivity-class error
 * ([CrewError.Backend.Network] / [CrewError.Backend.Unavailable]) — the change is durably parked in
 * the [OutboxPort] (carrying the style as its [CrewScoreStyle.key]) and the use case returns
 * [Result.Ok]; the `OutboxRunner` replays it (idempotently) when connectivity returns.
 */
class SetCrewScoreStyleUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(crewId: CrewId, style: CrewScoreStyle): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok  -> s.value.accountId
            is Result.Err -> return Result.failure(s.error.toCrewError())
        }
        if (!connectivity.isOnline().first()) {
            repository.offlineOwnerGuard(crewId, accountId)?.let { return Result.failure(it) }
            return enqueue(crewId, accountId, style)
        }
        return when (val r = repository.setScoreStyle(crewId, accountId, style)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                CrewError.Backend.Network, CrewError.Backend.Unavailable ->
                    enqueue(crewId, accountId, style)
                else -> r
            }
        }
    }

    private suspend fun enqueue(
        crewId: CrewId,
        requestedBy: AccountId,
        style: CrewScoreStyle,
    ): Result<Unit, CrewError> {
        outbox.enqueue(PendingCommand.SetCrewScoreStyle(crewId, requestedBy, style.key))
        return Result.success(Unit)
    }
}
