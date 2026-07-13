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
import es.schsebastian.foodrats.feature.crew.domain.model.WelcomeMessage
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

/**
 * Sets (or clears) the crew's pinned welcome message. Owner-only; authorization is enforced by the
 * repository.
 *
 * Validates [message] via [WelcomeMessage.of] first. A blank message is interpreted as "clear the
 * welcome message" (sends `null` to Firestore). Returns
 * [CrewError.Validation.WelcomeMessageTooLong] when the input exceeds [WelcomeMessage.MAX_LEN] = 200 chars.
 *
 * OFFLINE-FIRST (P2 §0.5). Validation + session resolution run first. When offline — or the direct
 * write fails with a connectivity-class error ([CrewError.Backend.Network] /
 * [CrewError.Backend.Unavailable]) — the change is durably parked in the [OutboxPort] and the use
 * case returns [Result.Ok]; the `OutboxRunner` replays it (idempotently) when connectivity returns.
 */
class SetCrewWelcomeMessageUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(crewId: CrewId, message: String): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok  -> s.value.accountId
            is Result.Err -> return Result.failure(s.error.toCrewError())
        }
        val validated = when (val r = WelcomeMessage.of(message)) {
            is Result.Ok  -> r.value      // null = blank input → clear message
            is Result.Err -> return Result.failure(r.error)
        }
        val value = validated?.value
        if (!connectivity.isOnline().first()) {
            repository.offlineOwnerGuard(crewId, accountId)?.let { return Result.failure(it) }
            return enqueue(crewId, accountId, value)
        }
        return when (val r = repository.setWelcomeMessage(crewId, accountId, value)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                CrewError.Backend.Network, CrewError.Backend.Unavailable ->
                    enqueue(crewId, accountId, value)
                else -> r
            }
        }
    }

    private suspend fun enqueue(
        crewId: CrewId,
        requestedBy: AccountId,
        message: String?,
    ): Result<Unit, CrewError> {
        return when (outbox.enqueue(PendingCommand.SetCrewWelcomeMessage(crewId, requestedBy, message))) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(CrewError.Backend.Unavailable)
        }
    }
}
