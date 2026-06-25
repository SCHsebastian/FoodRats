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
import es.schsebastian.foodrats.feature.crew.domain.model.CrewTagline
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

/**
 * Sets (or clears) the crew tagline. Owner-only; authorization is enforced by the repository.
 *
 * Validates [tagline] via [CrewTagline.of] first. A blank tagline is interpreted as "clear the
 * tagline" (sends `null` to Firestore). Returns [CrewError.Validation.TaglineTooLong] on a tagline
 * that exceeds [CrewTagline.MAX_LEN] = 120 chars.
 *
 * OFFLINE-FIRST (P2 §0.5). Validation + session resolution run first (so an invalid tagline fails
 * fast even offline). When the device is offline — or the direct write fails with a
 * connectivity-class error ([CrewError.Backend.Network] / [CrewError.Backend.Unavailable]) — the
 * change is durably parked in the [OutboxPort] and the use case returns [Result.Ok]; the
 * `OutboxRunner` replays it (idempotently — it sets the value) when connectivity returns.
 */
class SetCrewTaglineUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(crewId: CrewId, tagline: String): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok  -> s.value.accountId
            is Result.Err -> return Result.failure(s.error.toCrewError())
        }
        val validated = when (val r = CrewTagline.of(tagline)) {
            is Result.Ok  -> r.value      // null = blank input → clear tagline
            is Result.Err -> return Result.failure(r.error)
        }
        val value = validated?.value
        if (!connectivity.isOnline().first()) {
            repository.offlineOwnerGuard(crewId, accountId)?.let { return Result.failure(it) }
            return enqueue(crewId, accountId, value)
        }
        return when (val r = repository.setTagline(crewId, accountId, value)) {
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
        tagline: String?,
    ): Result<Unit, CrewError> {
        outbox.enqueue(PendingCommand.SetCrewTagline(crewId, requestedBy, tagline))
        return Result.success(Unit)
    }
}
